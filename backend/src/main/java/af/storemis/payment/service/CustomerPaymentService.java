package af.storemis.payment.service;

import af.storemis.audit.entity.AuditAction;
import af.storemis.audit.entity.AuditModule;
import af.storemis.audit.service.AuditEvent;
import af.storemis.audit.service.AuditService;
import af.storemis.cash.entity.CashAccount;
import af.storemis.cash.entity.CashDirection;
import af.storemis.cash.entity.CashTxnType;
import af.storemis.cash.repository.CashAccountRepository;
import af.storemis.cash.service.CashPostingService;
import af.storemis.cash.service.CashPostingService.CashSource;
import af.storemis.common.exception.BusinessException;
import af.storemis.common.exception.ErrorCode;
import af.storemis.common.exception.NotFoundException;
import af.storemis.common.money.Money;
import af.storemis.common.security.SecurityUtils;
import af.storemis.common.time.BusinessClock;
import af.storemis.currency.service.CurrencyService;
import af.storemis.customer.entity.Customer;
import af.storemis.customer.entity.CustomerEntryType;
import af.storemis.customer.service.CustomerLedgerService;
import af.storemis.customer.service.CustomerService;
import af.storemis.numbering.DocumentNumberService;
import af.storemis.numbering.DocumentType;
import af.storemis.payment.dto.CustomerPaymentRequest;
import af.storemis.payment.dto.CustomerPaymentResponse;
import af.storemis.payment.dto.PaymentLine;
import af.storemis.payment.entity.CustomerPayment;
import af.storemis.payment.entity.CustomerPaymentAllocation;
import af.storemis.payment.entity.PaymentStatus;
import af.storemis.payment.repository.CustomerPaymentRepository;
import af.storemis.sales.entity.Sale;
import af.storemis.sales.repository.SaleRepository;
import af.storemis.sales.service.SaleCalculator;
import af.storemis.user.service.UserDirectory;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer payments — at the till (AT_SALE) or later (COLLECTION). Cash comes into the account of the payment's
 * currency, the money is applied to invoices, and the customer's receivable goes down, all in one transaction.
 */
@Service
public class CustomerPaymentService {

    public static final String KIND_AT_SALE = "AT_SALE";
    public static final String KIND_COLLECTION = "COLLECTION";
    /** A cross-currency payment within this much of the amount due is treated as exact (rounding). */
    private static final BigDecimal ROUNDING_TOLERANCE = new BigDecimal("0.05");

    private final CustomerPaymentRepository payments;
    private final SaleRepository sales;
    private final CashAccountRepository cashAccounts;
    private final CashPostingService cash;
    private final CustomerLedgerService ledger;
    private final CustomerService customers;
    private final CurrencyService currency;
    private final DocumentNumberService numbers;
    private final UserDirectory users;
    private final AuditService audit;
    private final BusinessClock clock;

    public CustomerPaymentService(CustomerPaymentRepository payments, SaleRepository sales, CashAccountRepository cashAccounts, CashPostingService cash,
            CustomerLedgerService ledger, CustomerService customers, CurrencyService currency, DocumentNumberService numbers, UserDirectory users,
            AuditService audit, BusinessClock clock) {
        this.payments = payments;
        this.sales = sales;
        this.cashAccounts = cashAccounts;
        this.cash = cash;
        this.ledger = ledger;
        this.customers = customers;
        this.currency = currency;
        this.numbers = numbers;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Money taken at the till for a sale being confirmed. Returns the amount applied to the invoice (invoice currency).
     * Anything above what is still due becomes customer credit for a named customer; for the walk-in customer it is refused.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal payAtSale(Sale sale, PaymentLine line, boolean walkIn) {
        String cur = currency.requireActive(line.currency()).getCode();
        cash.requireAccount(line.cashAccountId(), cur);
        BigDecimal rate = cur.equals(sale.getCurrencyCode()) ? sale.getExchangeRate() : currency.resolveRate(cur, line.exchangeRate());
        BigDecimal amount = Money.amount(line.amount());
        BigDecimal cross = SaleCalculator.crossRate(cur, rate, sale.getCurrencyCode(), sale.getExchangeRate());
        BigDecimal canApply = Money.amount(amount.multiply(cross));
        BigDecimal due = sale.getBalanceDue();
        BigDecimal applied = canApply.subtract(due).abs().compareTo(ROUNDING_TOLERANCE) <= 0 && !cur.equals(sale.getCurrencyCode()) ? due : Money.min(canApply, due);
        BigDecimal excess = canApply.subtract(applied);
        if (excess.compareTo(ROUNDING_TOLERANCE) > 0 && walkIn) {
            throw new BusinessException(ErrorCode.OVERPAYMENT, Map.of("due", due.toPlainString(), "currency", sale.getCurrencyCode()));
        }

        CustomerPayment p = payments.save(new CustomerPayment(numbers.next(DocumentType.CUSTOMER_PAYMENT), null, sale.getCustomerId(), KIND_AT_SALE,
                clock.now(), clock.today(), cur, amount, rate, line.cashAccountId(), null, SecurityUtils.currentUserId().orElse(null)));
        cash.post(line.cashAccountId(), CashDirection.IN, amount, CashTxnType.SALE_RECEIPT, rate, new CashSource("CUSTOMER_PAYMENT", p.getId(), p.getPaymentNo()),
                "Sale " + sale.getInvoiceNo());
        if (applied.signum() > 0) {
            BigDecimal paidForApplied = excess.signum() > 0 && !walkIn ? Money.amount(applied.divide(cross, 10, RoundingMode.HALF_UP)) : amount;
            allocate(p, sale, applied, paidForApplied.min(amount), cross);
        }
        if (excess.signum() > 0 && !walkIn) {
            BigDecimal paidExcess = amount.subtract(p.getAllocations().stream().map(CustomerPaymentAllocation::getPaidAmount).reduce(Money.ZERO, BigDecimal::add));
            if (paidExcess.signum() > 0) {
                p.setUnallocatedAmount(paidExcess);
                ledger.credit(sale.getCustomerId(), cur, CustomerEntryType.PAYMENT, paidExcess, "CUSTOMER_PAYMENT", p.getId(), p.getPaymentNo(), "Advance");
            }
        }
        return applied;
    }

    /** Collect money later against open invoices (oldest first, or a chosen one). */
    @Transactional
    public CustomerPaymentResponse collect(CustomerPaymentRequest r) {
        if (r.clientRequestId() != null) {
            Optional<CustomerPayment> existing = payments.findByClientRequestId(r.clientRequestId());
            if (existing.isPresent()) return toResponse(existing.get());
        }
        Customer customer = customers.get(r.customerId());
        if (customer.isWalkIn()) throw new BusinessException(ErrorCode.INVALID_STATE, Map.of("customer", customer.getName()));
        String cur = currency.requireActive(r.currency()).getCode();
        cash.requireAccount(r.cashAccountId(), cur);
        BigDecimal rate = currency.resolveRate(cur, r.exchangeRate());
        BigDecimal amount = Money.amount(r.amount());

        CustomerPayment p = payments.save(new CustomerPayment(numbers.next(DocumentType.CUSTOMER_PAYMENT), r.clientRequestId(), customer.getId(),
                KIND_COLLECTION, clock.now(), clock.today(), cur, amount, rate, r.cashAccountId(), blankToNull(r.notes()),
                SecurityUtils.currentUserId().orElse(null)));
        cash.post(r.cashAccountId(), CashDirection.IN, amount, CashTxnType.CUSTOMER_COLLECTION, rate,
                new CashSource("CUSTOMER_PAYMENT", p.getId(), p.getPaymentNo()), "Payment from " + customer.getName());

        List<Sale> targets = sales.lockOpenForCustomer(customer.getId());
        if (r.saleId() != null) {
            Sale chosen = targets.stream().filter(x -> x.getId().equals(r.saleId())).findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.ALLOCATION_INVALID, Map.of("saleId", r.saleId())));
            targets = List.of(chosen);
        }
        BigDecimal remaining = amount;
        for (Sale sale : targets) {
            if (remaining.signum() <= 0) break;
            BigDecimal cross = sale.getCurrencyCode().equals(cur) ? BigDecimal.ONE
                    : rate.divide(currency.currentRate(sale.getCurrencyCode()), Money.RATE_SCALE, RoundingMode.HALF_UP);
            BigDecimal canApply = Money.amount(remaining.multiply(cross));
            BigDecimal applied = Money.min(canApply, sale.getBalanceDue());
            if (applied.signum() <= 0) continue;
            BigDecimal paid = applied.compareTo(canApply) == 0 ? remaining : Money.amount(applied.divide(cross, 10, RoundingMode.HALF_UP));
            if (paid.compareTo(remaining) > 0) paid = remaining;
            allocate(p, sale, applied, paid, cross);
            remaining = remaining.subtract(paid);
        }
        if (remaining.signum() > 0) {
            p.setUnallocatedAmount(remaining);
            ledger.credit(customer.getId(), cur, CustomerEntryType.PAYMENT, remaining, "CUSTOMER_PAYMENT", p.getId(), p.getPaymentNo(), "Advance");
        }
        audit.log(AuditEvent.of(AuditAction.PAYMENT, AuditModule.PAYMENTS, "CustomerPayment", p.getId(), p.getPaymentNo(), "Customer payment received")
                .withDetails(Map.of("customer", customer.getName(), "amount", amount.toPlainString(), "currency", cur)));
        return toResponse(p);
    }

    private void allocate(CustomerPayment p, Sale sale, BigDecimal applied, BigDecimal paid, BigDecimal cross) {
        p.getAllocations().add(new CustomerPaymentAllocation(p, sale.getId(), applied, paid, cross));
        sale.addPayment(applied);
        ledger.credit(sale.getCustomerId(), sale.getCurrencyCode(), CustomerEntryType.PAYMENT, applied, "CUSTOMER_PAYMENT", p.getId(), p.getPaymentNo(),
                sale.getInvoiceNo());
    }

    /** Admin undo: the cash leaves the drawer again and the invoices are owed again. */
    @Transactional
    public CustomerPaymentResponse reverse(Long id, String reason) {
        CustomerPayment p = payments.lock(id).orElseThrow(() -> new NotFoundException("CustomerPayment", id));
        if (p.getStatus() != PaymentStatus.POSTED || p.getAllocations().stream().anyMatch(CustomerPaymentAllocation::isReleased)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, Map.of("status", p.getStatus().name()));
        }
        cash.post(p.getCashAccountId(), CashDirection.OUT, p.getAmount(), CashTxnType.REVERSAL, p.getExchangeRate(),
                new CashSource("CUSTOMER_PAYMENT", p.getId(), p.getPaymentNo()), "Reversal: " + reason);
        for (CustomerPaymentAllocation a : p.getAllocations()) {
            Sale sale = sales.lock(a.getSaleId()).orElseThrow();
            sale.removePayment(a.getAppliedAmount());
            ledger.debit(p.getCustomerId(), sale.getCurrencyCode(), CustomerEntryType.PAYMENT_REVERSAL, a.getAppliedAmount(), "CUSTOMER_PAYMENT", p.getId(),
                    p.getPaymentNo(), reason);
        }
        if (p.getUnallocatedAmount().signum() > 0) {
            ledger.debit(p.getCustomerId(), p.getCurrencyCode(), CustomerEntryType.PAYMENT_REVERSAL, p.getUnallocatedAmount(), "CUSTOMER_PAYMENT", p.getId(),
                    p.getPaymentNo(), reason);
            p.setUnallocatedAmount(Money.ZERO);
        }
        p.reverse(reason, clock.now(), SecurityUtils.currentUserId().orElse(null));
        audit.log(AuditEvent.of(AuditAction.REVERSE, AuditModule.PAYMENTS, "CustomerPayment", p.getId(), p.getPaymentNo(), "Customer payment reversed")
                .withDetails(Map.of("reason", reason)));
        return toResponse(p);
    }

    /** When a sale is cancelled: each payment on it is refunded from the same cash account, or kept as customer credit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseForCancelledSale(Sale sale, List<CustomerPaymentAllocation> allocations, boolean refund) {
        for (CustomerPaymentAllocation a : allocations) {
            CustomerPayment p = a.getPayment();
            if (a.isReleased() || p.getStatus() != PaymentStatus.POSTED) continue;
            a.release();
            if (refund) {
                cash.post(p.getCashAccountId(), CashDirection.OUT, a.getPaidAmount(), CashTxnType.SALE_REFUND, p.getExchangeRate(),
                        new CashSource("SALE", sale.getId(), sale.getInvoiceNo()), "Refund for cancelled " + sale.getInvoiceNo());
                ledger.debit(sale.getCustomerId(), sale.getCurrencyCode(), CustomerEntryType.CREDIT_REFUND, a.getAppliedAmount(), "SALE", sale.getId(),
                        sale.getInvoiceNo(), p.getPaymentNo());
            } else {
                p.setUnallocatedAmount(p.getUnallocatedAmount().add(a.getPaidAmount()));
            }
        }
    }

    @Transactional(readOnly = true)
    public Page<CustomerPaymentResponse> search(Long customerId, PaymentStatus status, LocalDate from, LocalDate to, String q, Long createdBy,
            Pageable pageable) {
        Specification<CustomerPayment> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (customerId != null) ps.add(cb.equal(root.get("customerId"), customerId));
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("businessDate"), from));
            if (to != null) ps.add(cb.lessThanOrEqualTo(root.get("businessDate"), to));
            if (createdBy != null) ps.add(cb.equal(root.get("createdBy"), createdBy));
            if (q != null && !q.isBlank()) ps.add(cb.like(cb.lower(root.get("paymentNo")), "%" + q.trim().toLowerCase() + "%"));
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return payments.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CustomerPaymentResponse get(Long id) {
        return toResponse(payments.findById(id).orElseThrow(() -> new NotFoundException("CustomerPayment", id)));
    }

    @Transactional(readOnly = true)
    public CustomerPaymentResponse toResponse(CustomerPayment p) {
        Map<Long, Sale> saleMap = sales.findAllById(p.getAllocations().stream().map(CustomerPaymentAllocation::getSaleId).toList()).stream()
                .collect(Collectors.toMap(Sale::getId, Function.identity()));
        List<CustomerPaymentResponse.Allocation> allocs = p.getAllocations().stream().map(a -> {
            Sale s = saleMap.get(a.getSaleId());
            return new CustomerPaymentResponse.Allocation(a.getSaleId(), s == null ? null : s.getInvoiceNo(), s == null ? null : s.getCurrencyCode(),
                    a.getAppliedAmount(), a.getPaidAmount(), a.getCrossRate(), a.isReleased());
        }).toList();
        String customerName = customers.get(p.getCustomerId()).getName();
        String accountName = cashAccounts.findById(p.getCashAccountId()).map(CashAccount::getName).orElse(null);
        String by = users.names(List.of(p.getCreatedBy() == null ? -1L : p.getCreatedBy())).get(p.getCreatedBy());
        return new CustomerPaymentResponse(p.getId(), p.getPaymentNo(), p.getCustomerId(), customerName, p.getPaymentKind(), p.getPaymentDate(),
                p.getBusinessDate(), p.getCurrencyCode(), p.getAmount(), p.getExchangeRate(), p.getCashAccountId(), accountName, p.getUnallocatedAmount(),
                p.getStatus(), p.getNotes(), p.getReversalReason(), by, allocs);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
