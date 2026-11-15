package af.storemis.user.service;

import af.storemis.audit.entity.AuditAction;
import af.storemis.audit.entity.AuditModule;
import af.storemis.audit.service.AuditEvent;
import af.storemis.audit.service.AuditService;
import af.storemis.auth.repository.RefreshTokenRepository;
import af.storemis.common.exception.BusinessException;
import af.storemis.common.exception.ErrorCode;
import af.storemis.common.exception.NotFoundException;
import af.storemis.common.security.Permission;
import af.storemis.common.security.Role;
import af.storemis.common.security.SecurityUtils;
import af.storemis.common.time.BusinessClock;
import af.storemis.user.dto.CreateUserRequest;
import af.storemis.user.dto.PermissionResponse;
import af.storemis.user.dto.UpdateUserRequest;
import af.storemis.user.entity.AppUser;
import af.storemis.user.entity.PermissionEntity;
import af.storemis.user.repository.AppUserRepository;
import af.storemis.user.repository.PermissionRepository;
import af.storemis.user.repository.RoleRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User management for Admins. Users are never deleted, only deactivated. */
@Service
public class UserService {

    private static final String ENTITY = "User";

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final BusinessClock clock;

    public UserService(AppUserRepository users, RoleRepository roles, PermissionRepository permissions, RefreshTokenRepository refreshTokens,
            PasswordEncoder passwordEncoder, AuditService audit, BusinessClock clock) {
        this.users = users;
        this.roles = roles;
        this.permissions = permissions;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<AppUser> search(String q, Role role, Boolean active, Pageable pageable) {
        Specification<AppUser> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("username")), like), cb.like(cb.lower(root.get("fullName")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("phone"), "")), like)));
            }
            if (role != null) ps.add(cb.equal(root.get("role").get("code"), role));
            if (active != null) ps.add(cb.equal(root.get("active"), active));
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return users.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public AppUser get(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException(ENTITY, id));
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        return permissions.findAll().stream().sorted(Comparator.comparing(p -> p.getCode().ordinal()))
                .map(p -> new PermissionResponse(p.getCode(), p.getDescription(), p.isDefaultForEntryUser())).toList();
    }

    @Transactional
    public AppUser create(CreateUserRequest req) {
        String username = req.username().trim();
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new BusinessException(ErrorCode.DUPLICATE, Map.of("field", "username"));
        }
        PasswordPolicy.check(req.password());
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setFullName(req.fullName().trim());
        u.setPhone(blankToNull(req.phone()));
        u.setRole(roles.findByCode(req.role()).orElseThrow());
        u.setPermissions(permissionsFor(req.role(), req.permissions() == null ? defaultEntryPermissions() : req.permissions()));
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setMustChangePassword(true);
        u.setActive(true);
        users.save(u);
        audit.log(AuditEvent.of(AuditAction.CREATE, AuditModule.USERS, ENTITY, u.getId(), u.getUsername(), "User created")
                .withDetails(Map.of("role", req.role().name(), "permissions", names(u.getPermissions()))));
        return u;
    }

    @Transactional
    public AppUser update(Long id, UpdateUserRequest req) {
        AppUser u = get(id);
        u.checkVersion(req.version());
        Map<String, Object> before = snapshot(u);
        if (u.getRole() == Role.ADMIN && req.role() != Role.ADMIN) {
            ensureNotSelf(u, "role");
            ensureAnotherActiveAdmin(u);
        }
        u.setFullName(req.fullName().trim());
        u.setPhone(blankToNull(req.phone()));
        u.setRole(roles.findByCode(req.role()).orElseThrow());
        u.setPermissions(permissionsFor(req.role(), req.permissions()));
        users.saveAndFlush(u);
        Map<String, Object> after = snapshot(u);
        audit.log(AuditEvent.of(AuditAction.UPDATE, AuditModule.USERS, ENTITY, u.getId(), u.getUsername(), "User updated")
                .withDetails(diff(before, after)));
        return u;
    }

    @Transactional
    public AppUser setActive(Long id, boolean active) {
        AppUser u = get(id);
        if (u.isActive() == active) return u;
        if (!active) {
            ensureNotSelf(u, "active");
            if (u.isAdmin()) ensureAnotherActiveAdmin(u);
            refreshTokens.revokeAllForUser(u.getId(), clock.now());
        }
        u.setActive(active);
        audit.log(AuditEvent.of(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE, AuditModule.USERS, ENTITY, u.getId(), u.getUsername(),
                active ? "User activated" : "User deactivated"));
        return u;
    }

    /** Admin sets a temporary password; the user must change it at next sign-in. Unlocks the account and ends its sessions. */
    @Transactional
    public AppUser resetPassword(Long id, String newPassword) {
        AppUser u = get(id);
        PasswordPolicy.check(newPassword);
        u.setPasswordHash(passwordEncoder.encode(newPassword));
        u.setMustChangePassword(true);
        u.setFailedLoginAttempts(0);
        u.setLockedUntil(null);
        u.setPasswordChangedAt(clock.now());
        refreshTokens.revokeAllForUser(u.getId(), clock.now());
        audit.log(AuditEvent.of(AuditAction.PASSWORD_RESET, AuditModule.USERS, ENTITY, u.getId(), u.getUsername(), "Password reset by admin"));
        return u;
    }

    private void ensureNotSelf(AppUser u, String field) {
        if (Objects.equals(SecurityUtils.currentUserId().orElse(null), u.getId())) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_SELF, Map.of("field", field));
        }
    }

    private void ensureAnotherActiveAdmin(AppUser u) {
        long activeAdmins = users.countActiveByRole(Role.ADMIN);
        if (u.isActive() && activeAdmins <= 1) {
            throw new BusinessException(ErrorCode.LAST_ADMIN);
        }
    }

    /** Admins hold every permission implicitly, so their stored toggle list is kept empty. */
    private static Set<Permission> permissionsFor(Role role, Set<Permission> requested) {
        if (role == Role.ADMIN || requested == null) return EnumSet.noneOf(Permission.class);
        return requested.isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(requested);
    }

    private Set<Permission> defaultEntryPermissions() {
        return permissions.findAll().stream().filter(PermissionEntity::isDefaultForEntryUser).map(PermissionEntity::getCode)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Permission.class)));
    }

    private static Map<String, Object> snapshot(AppUser u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fullName", u.getFullName());
        m.put("phone", u.getPhone());
        m.put("role", u.getRole().name());
        m.put("permissions", names(u.getPermissions()));
        return m;
    }

    private static Map<String, Object> diff(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> changes = new LinkedHashMap<>();
        after.forEach((k, v) -> {
            if (!Objects.equals(before.get(k), v)) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("from", before.get(k));
                change.put("to", v);
                changes.put(k, change);
            }
        });
        return changes;
    }

    private static List<String> names(Set<Permission> ps) {
        return ps.stream().map(Enum::name).collect(Collectors.toCollection(TreeSet::new)).stream().toList();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
