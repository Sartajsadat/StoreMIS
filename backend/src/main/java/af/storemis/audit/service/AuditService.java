package af.storemis.audit.service;

import af.storemis.audit.entity.AuditAction;
import af.storemis.audit.entity.AuditLog;
import af.storemis.audit.entity.AuditModule;
import af.storemis.audit.repository.AuditLogRepository;
import af.storemis.common.security.AppPrincipal;
import af.storemis.common.security.SecurityUtils;
import af.storemis.common.time.BusinessClock;
import af.storemis.common.web.ClientInfo;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final BusinessClock clock;

    public AuditService(AuditLogRepository repository, BusinessClock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Records an event for the current user inside the caller's transaction,
     * so a rolled-back operation never leaves an audit entry behind.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void log(AuditEvent event) {
        AppPrincipal p = SecurityUtils.currentPrincipal().orElse(null);
        write(event, p == null ? null : p.id(), p == null ? null : p.username());
    }

    /** Same as {@link #log} but for an explicitly named user (e.g. during login, before authentication is set). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void logAs(Long userId, String username, AuditEvent event) {
        write(event, userId, username);
    }

    /** Commits independently of the caller — used for failed logins, which roll back their own transaction. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logIndependent(Long userId, String username, AuditEvent event) {
        write(event, userId, username);
    }

    private void write(AuditEvent e, Long userId, String username) {
        repository.save(new AuditLog(clock.now(), userId, username, e.action(), e.module(), e.entityType(), e.entityId(), e.reference(),
                truncate(e.description(), 500), e.details(), ClientInfo.ip()));
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(Long userId, AuditModule module, AuditAction action, LocalDate from, LocalDate to, String q, Pageable pageable) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (userId != null) ps.add(cb.equal(root.get("userId"), userId));
            if (module != null) ps.add(cb.equal(root.get("module"), module));
            if (action != null) ps.add(cb.equal(root.get("action"), action));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from.atStartOfDay(clock.zone()).toOffsetDateTime()));
            if (to != null) ps.add(cb.lessThan(root.get("occurredAt"), to.plusDays(1).atStartOfDay(clock.zone()).toOffsetDateTime()));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("reference")), like), cb.like(cb.lower(root.get("description")), like),
                        cb.like(cb.lower(root.get("username")), like)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return repository.findAll(spec, pageable);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
