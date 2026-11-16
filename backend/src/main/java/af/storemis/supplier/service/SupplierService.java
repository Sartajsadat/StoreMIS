package af.storemis.supplier.service;

import af.storemis.audit.entity.AuditAction;
import af.storemis.audit.entity.AuditModule;
import af.storemis.audit.service.AuditEvent;
import af.storemis.audit.service.AuditService;
import af.storemis.common.exception.BusinessException;
import af.storemis.common.exception.ErrorCode;
import af.storemis.common.exception.NotFoundException;
import af.storemis.common.money.Money;
import af.storemis.currency.service.CurrencyService;
import af.storemis.supplier.dto.StatementResponse;
import af.storemis.supplier.dto.SupplierOpeningBalanceRequest;
import af.storemis.supplier.dto.SupplierRequest;
import af.storemis.supplier.dto.SupplierResponse;
import af.storemis.supplier.entity.Supplier;
import af.storemis.supplier.entity.SupplierEntryType;
import af.storemis.supplier.entity.SupplierLedgerEntry;
import af.storemis.supplier.repository.SupplierLedgerRepository;
import af.storemis.supplier.repository.SupplierRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierService {

    private final SupplierRepository suppliers;
    private final SupplierLedgerRepository ledgerRepository;
    private final SupplierLedgerService ledger;
    private final CurrencyService currency;
    private final AuditService audit;

    public SupplierService(SupplierRepository suppliers, SupplierLedgerRepository ledgerRepository, SupplierLedgerService ledger, CurrencyService currency,
            AuditService audit) {
        this.suppliers = suppliers;
        this.ledgerRepository = ledgerRepository;
        this.ledger = ledger;
        this.currency = currency;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Supplier get(Long id) {
        return suppliers.findById(id).orElseThrow(() -> new NotFoundException("Supplier", id));
    }

    /** For documents: the supplier must exist and be active. */
    @Transactional(readOnly = true)
    public Supplier requireActive(Long id) {
        Supplier s = get(id);
        if (!s.isActive()) throw new BusinessException(ErrorCode.SUPPLIER_INACTIVE, Map.of("supplier", s.getName()));
        return s;
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponse> search(String q, Boolean active, boolean withBalanceOnly, Pageable pageable) {
        Specification<Supplier> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("name")), like), cb.like(cb.lower(cb.coalesce(root.get("phone"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("contactPerson"), "")), like)));
            }
            if (active != null) ps.add(cb.equal(root.get("active"), active));
            if (withBalanceOnly) {
                var sub = query.subquery(Long.class);
                var e = sub.from(SupplierLedgerEntry.class);
                sub.select(e.get("supplierId")).where(cb.equal(e.get("supplierId"), root.get("id")));
                ps.add(cb.exists(sub));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Page<Supplier> page = suppliers.findAll(spec, pageable);
        Map<Long, Map<String, BigDecimal>> balances = balancesFor(page.getContent().stream().map(Supplier::getId).toList());
        return page.map(s -> SupplierResponse.from(s, balances.get(s.getId())));
    }

    @Transactional(readOnly = true)
    public SupplierResponse describe(Long id) {
        Supplier s = get(id);
        return SupplierResponse.from(s, balancesFor(List.of(id)).get(id));
    }

    @Transactional(readOnly = true)
    public Map<Long, Map<String, BigDecimal>> balancesFor(List<Long> ids) {
        Map<Long, Map<String, BigDecimal>> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        for (Object[] r : ledgerRepository.latestBalances(ids)) {
            result.computeIfAbsent(((Number) r[0]).longValue(), k -> new TreeMap<>()).put(((String) r[1]).trim(), (BigDecimal) r[2]);
        }
        return result;
    }

    /** Total payable to all suppliers, per currency (never summed across currencies). */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> totalPayables() {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        for (Object[] r : ledgerRepository.totalPayables()) m.put(((String) r[0]).trim(), (BigDecimal) r[1]);
        return m;
    }

    @Transactional
    public SupplierResponse create(SupplierRequest r) {
        Supplier s = new Supplier();
        apply(s, r);
        suppliers.save(s);
        audit.log(AuditEvent.of(AuditAction.CREATE, AuditModule.SUPPLIERS, "Supplier", s.getId(), truncate(s.getName()), "Supplier created"));
        return SupplierResponse.from(s, Map.of());
    }

    @Transactional
    public SupplierResponse update(Long id, SupplierRequest r) {
        Supplier s = get(id);
        s.checkVersion(r.version());
        apply(s, r);
        suppliers.saveAndFlush(s);
        audit.log(AuditEvent.of(AuditAction.UPDATE, AuditModule.SUPPLIERS, "Supplier", s.getId(), truncate(s.getName()), "Supplier updated"));
        return describe(id);
    }

    @Transactional
    public SupplierResponse setActive(Long id, boolean active) {
        Supplier s = get(id);
        if (s.isActive() != active) {
            s.setActive(active);
            audit.log(AuditEvent.of(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE, AuditModule.SUPPLIERS, "Supplier", s.getId(),
                    truncate(s.getName()), active ? "Supplier activated" : "Supplier deactivated"));
        }
        return describe(id);
    }

    /** Debt carried over from before the system was used. Once per supplier and currency, before any other entry in it. */
    @Transactional
    public SupplierResponse openingBalance(Long id, SupplierOpeningBalanceRequest r) {
        Supplier s = get(id);
        String cur = currency.requireActive(r.currency()).getCode();
        suppliers.lock(id);
        if (ledgerRepository.existsBySupplierIdAndCurrencyCode(id, cur)) {
            throw new BusinessException(ErrorCode.OPENING_BALANCE_EXISTS, Map.of("account", s.getName(), "currency", cur));
        }
        ledger.credit(id, cur, SupplierEntryType.OPENING_BALANCE, r.amount(), "SUPPLIER", id, null, r.notes());
        audit.log(AuditEvent.of(AuditAction.CREATE, AuditModule.SUPPLIERS, "Supplier", id, truncate(s.getName()), "Supplier opening balance")
                .withDetails(Map.of("currency", cur, "amount", Money.amount(r.amount()).toPlainString())));
        return describe(id);
    }

    @Transactional(readOnly = true)
    public StatementResponse statement(Long id, String cur, LocalDate from, LocalDate to) {
        get(id);
        String code = currency.requireActive(cur).getCode();
        BigDecimal opening = ledgerRepository.balanceBefore(id, code, from).orElse(Money.ZERO);
        List<SupplierLedgerEntry> entries = ledgerRepository.statement(id, code, from, to);
        BigDecimal debit = Money.ZERO;
        BigDecimal credit = Money.ZERO;
        List<StatementResponse.Line> lines = new ArrayList<>();
        for (SupplierLedgerEntry e : entries) {
            debit = debit.add(e.getDebit());
            credit = credit.add(e.getCredit());
            lines.add(new StatementResponse.Line(e.getId(), e.getBusinessDate(), e.getEntryType().name(), e.getReferenceNo(), e.getSourceType(),
                    e.getSourceId(), e.getDebit(), e.getCredit(), e.getBalanceAfter(), e.getNotes()));
        }
        BigDecimal closing = entries.isEmpty() ? opening : entries.get(entries.size() - 1).getBalanceAfter();
        return new StatementResponse(code, from, to, opening, debit, credit, closing, lines);
    }

    private static void apply(Supplier s, SupplierRequest r) {
        s.setName(r.name().trim());
        s.setPhone(blankToNull(r.phone()));
        s.setAddress(blankToNull(r.address()));
        s.setContactPerson(blankToNull(r.contactPerson()));
        s.setNotes(blankToNull(r.notes()));
    }

    private static String truncate(String s) {
        return s.length() <= 50 ? s : s.substring(0, 50);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
