package af.storemis.customer.service;

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
import af.storemis.customer.dto.CustomerOpeningBalanceRequest;
import af.storemis.customer.dto.CustomerRequest;
import af.storemis.customer.dto.CustomerResponse;
import af.storemis.customer.entity.Customer;
import af.storemis.customer.entity.CustomerEntryType;
import af.storemis.customer.entity.CustomerLedgerEntry;
import af.storemis.customer.repository.CustomerLedgerRepository;
import af.storemis.customer.repository.CustomerRepository;
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
public class CustomerService {

    private final CustomerRepository customers;
    private final CustomerLedgerRepository ledgerRepository;
    private final CustomerLedgerService ledger;
    private final CurrencyService currency;
    private final AuditService audit;

    public CustomerService(CustomerRepository customers, CustomerLedgerRepository ledgerRepository, CustomerLedgerService ledger, CurrencyService currency,
            AuditService audit) {
        this.customers = customers;
        this.ledgerRepository = ledgerRepository;
        this.ledger = ledger;
        this.currency = currency;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Customer get(Long id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("Customer", id));
    }

    /** The built-in Walk-in Customer used for anonymous cash sales. */
    @Transactional(readOnly = true)
    public Customer walkIn() {
        return customers.findFirstByWalkInTrue().orElseThrow(() -> new NotFoundException("Customer", "walk-in"));
    }

    /** For documents: the customer must exist and be active. */
    @Transactional(readOnly = true)
    public Customer requireActive(Long id) {
        Customer s = get(id);
        if (!s.isActive()) throw new BusinessException(ErrorCode.CUSTOMER_INACTIVE, Map.of("customer", s.getName()));
        return s;
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> search(String q, Boolean active, String type, boolean withBalanceOnly, Pageable pageable) {
        Specification<Customer> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("name")), like), cb.like(cb.lower(cb.coalesce(root.get("phone"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("address"), "")), like)));
            }
            if (active != null) ps.add(cb.equal(root.get("active"), active));
            if (type != null && !type.isBlank()) ps.add(cb.equal(root.get("customerType"), type));
            if (withBalanceOnly) {
                var sub = query.subquery(Long.class);
                var e = sub.from(CustomerLedgerEntry.class);
                sub.select(e.get("customerId")).where(cb.equal(e.get("customerId"), root.get("id")));
                ps.add(cb.exists(sub));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Page<Customer> page = customers.findAll(spec, pageable);
        Map<Long, Map<String, BigDecimal>> balances = balancesFor(page.getContent().stream().map(Customer::getId).toList());
        return page.map(s -> CustomerResponse.from(s, balances.get(s.getId())));
    }

    @Transactional(readOnly = true)
    public CustomerResponse describe(Long id) {
        Customer s = get(id);
        return CustomerResponse.from(s, balancesFor(List.of(id)).get(id));
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

    /** Total receivable from all customers, per currency (never summed across currencies). */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> totalReceivables() {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        for (Object[] r : ledgerRepository.totalReceivables()) m.put(((String) r[0]).trim(), (BigDecimal) r[1]);
        return m;
    }

    @Transactional
    public CustomerResponse create(CustomerRequest r) {
        Customer s = new Customer();
        apply(s, r);
        customers.save(s);
        audit.log(AuditEvent.of(AuditAction.CREATE, AuditModule.CUSTOMERS, "Customer", s.getId(), truncate(s.getName()), "Customer created"));
        return CustomerResponse.from(s, Map.of());
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest r) {
        Customer s = get(id);
        s.checkVersion(r.version());
        apply(s, r);
        customers.saveAndFlush(s);
        audit.log(AuditEvent.of(AuditAction.UPDATE, AuditModule.CUSTOMERS, "Customer", s.getId(), truncate(s.getName()), "Customer updated"));
        return describe(id);
    }

    @Transactional
    public CustomerResponse setActive(Long id, boolean active) {
        Customer s = get(id);
        if (s.isWalkIn() && !active) throw new BusinessException(ErrorCode.INVALID_STATE, Map.of("customer", s.getName()));
        if (s.isActive() != active) {
            s.setActive(active);
            audit.log(AuditEvent.of(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE, AuditModule.CUSTOMERS, "Customer", s.getId(),
                    truncate(s.getName()), active ? "Customer activated" : "Customer deactivated"));
        }
        return describe(id);
    }

    /** Debt carried over from before the system was used. Once per customer and currency, before any other entry in it. */
    @Transactional
    public CustomerResponse openingBalance(Long id, CustomerOpeningBalanceRequest r) {
        Customer s = get(id);
        if (s.isWalkIn()) throw new BusinessException(ErrorCode.INVALID_STATE, Map.of("customer", s.getName()));
        String cur = currency.requireActive(r.currency()).getCode();
        customers.lock(id);
        if (ledgerRepository.existsByCustomerIdAndCurrencyCode(id, cur)) {
            throw new BusinessException(ErrorCode.OPENING_BALANCE_EXISTS, Map.of("account", s.getName(), "currency", cur));
        }
        ledger.credit(id, cur, CustomerEntryType.OPENING_BALANCE, r.amount(), "SUPPLIER", id, null, r.notes());
        audit.log(AuditEvent.of(AuditAction.CREATE, AuditModule.CUSTOMERS, "Customer", id, truncate(s.getName()), "Customer opening balance")
                .withDetails(Map.of("currency", cur, "amount", Money.amount(r.amount()).toPlainString())));
        return describe(id);
    }

    @Transactional(readOnly = true)
    public StatementResponse statement(Long id, String cur, LocalDate from, LocalDate to) {
        get(id);
        String code = currency.requireActive(cur).getCode();
        BigDecimal opening = ledgerRepository.balanceBefore(id, code, from).orElse(Money.ZERO);
        List<CustomerLedgerEntry> entries = ledgerRepository.statement(id, code, from, to);
        BigDecimal debit = Money.ZERO;
        BigDecimal credit = Money.ZERO;
        List<StatementResponse.Line> lines = new ArrayList<>();
        for (CustomerLedgerEntry e : entries) {
            debit = debit.add(e.getDebit());
            credit = credit.add(e.getCredit());
            lines.add(new StatementResponse.Line(e.getId(), e.getBusinessDate(), e.getEntryType().name(), e.getReferenceNo(), e.getSourceType(),
                    e.getSourceId(), e.getDebit(), e.getCredit(), e.getBalanceAfter(), e.getNotes()));
        }
        BigDecimal closing = entries.isEmpty() ? opening : entries.get(entries.size() - 1).getBalanceAfter();
        return new StatementResponse(code, from, to, opening, debit, credit, closing, lines);
    }

    private static void apply(Customer s, CustomerRequest r) {
        s.setName(r.name().trim());
        s.setPhone(blankToNull(r.phone()));
        s.setAddress(blankToNull(r.address()));
        s.setCustomerType(r.customerType() == null ? "RETAIL" : r.customerType());
        s.setNotes(blankToNull(r.notes()));
    }

    private static String truncate(String s) {
        return s.length() <= 50 ? s : s.substring(0, 50);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
