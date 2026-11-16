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
import af.storemis.numbering.DocumentNumberService;
import af.storemis.numbering.DocumentType;
import af.storemis.payment.dto.SupplierPaymentRequest;
import af.storemis.payment.dto.SupplierPaymentResponse;
import af.storemis.payment.entity.PaymentStatus;
import af.storemis.payment.entity.SupplierPayment;
import af.storemis.payment.entity.SupplierPaymentAllocation;
import af.storemis.payment.repository.SupplierPaymentRepository;
import af.storemis.purchase.entity.Purchase;
import af.storemis.purchase.entity.PurchaseStatus;
import af.storemis.purchase.repository.PurchaseRepository;
import af.storemis.supplier.entity.Supplier;
import af.storemis.supplier.entity.SupplierEntryType;
import af.storemis.supplier.service.SupplierLedgerService;
import af.storemis.supplier.service.SupplierService;
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
 * Supplier payments: cash goes out of one account, the money is applied to purchases (oldest first or a chosen one),
 * and the supplier's payable ledger goes down — all in one transaction.
 */
@Service
public class SupplierPaymentService {

    public static final String KIND_AT_PURCHASE = "AT_PURCHASE";
    public static final String KIND_PAYMENT = "PAYMENT";

    private final SupplierPaymentRepository payments;
    private final PurchaseRepository purchases;
    private final CashAccountRepository cashAccounts;
    private final CashPostingService cash;
    private final SupplierLedgerService ledger;
    private final SupplierService suppliers;
    private final CurrencyService currency;
    private final DocumentNumberService numbers;
    private final UserDirectory users;
    private final AuditService audit;
    private final BusinessClock clock;

    public SupplierPaymentService(SupplierPaymentRepository payments, PurchaseRepository purchases, CashAccountRepository cashAccounts,
            CashPostingService cash, SupplierLedgerService ledger, SupplierService suppliers, CurrencyService currency, DocumentNumberService numbers,
            UserDirectory users, AuditService audit, BusinessClock clock) {
        this.payments = payments;
        this.purchases = purchases;
        this.cashAccounts = cashAccounts;
        this.cash = cash;
        this.ledger = ledger;
        this.suppliers = suppliers;
        this.currency = currency;
        this.numbers = numbers;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    /** Payment made while confirming a purchase: same currency, fully applied to that purchase. */
    @Transactional(propagation = Propagation.MANDATORY)
    public SupplierPayment payAtPurchase(Purchase purchase, BigDecimal amount, Long cashAccountId) {
        cash.requireAccount(cashAccountId, purchase.getCurrencyCode());
        SupplierPayment p = new SupplierPayment(numbers.next(DocumentType.SUPPLIER_PAYMENT), null, purchase.getSupplierId(), KIND_AT_PURCHASE,
                clock.now(), clock.today(), purchase.getCurrencyCode(), Money.amount(amount), purchase.getExchangeRate(), cashAccountId, null,
                SecurityUtils.currentUserId().orElse(null));
        payments.save(p);
        cash.post(cashAccountId, CashDirection.OUT, p.getAmount(), CashTxnType.PURCHASE_PAYMENT, p.getExchangeRate(),
                new CashSource("SUPPLIER_PAYMENT", p.getId(), p.getPaymentNo()), "Payment at purchase " + purchase.getPurchaseNo());
        allocate(p, purchase, p.getAmount(), p.getAmount(), BigDecimal.ONE);
        return p;
    }

    @Transactional
    public SupplierPaymentResponse create(SupplierPaymentRequest r) {
        if (r.clientRequestId() != null) {
            Optional<SupplierPayment> existing = payments.findByClientRequestId(r.clientRequestId());
            if (existing.isPresent()) return toResponse(existing.get());
        }
        Supplier supplier = suppliers.get(r.supplierId());
        String cur = currency.requireActive(r.currency()).getCode();
        cash.requireAccount(r.cashAccountId(), cur);
        BigDecimal rate = currency.resolveRate(cur, r.exchangeRate());
        BigDecimal amount = Money.amount(r.amount());

        SupplierPayment p = payments.save(new SupplierPayment(numbers.next(DocumentType.SUPPLIER_PAYMENT), r.clientRequestId(), supplier.getId(),
                KIND_PAYMENT, clock.now(), clock.today(), cur, amount, rate, r.cashAccountId(), blankToNull(r.notes()),
                SecurityUtils.currentUserId().orElse(null)));
        cash.post(r.cashAccountId(), CashDirection.OUT, amount, CashTxnType.SUPPLIER_PAYMENT, rate, new CashSource("SUPPLIER_PAYMENT", p.getId(), p.getPaymentNo()),
                "Payment to " + supplier.getName());

        List<Purchase> targets = purchases.lockOpenForSupplier(supplier.getId());
        if (r.purchaseId() != null) {
            Purchase chosen = targets.stream().filter(x -> x.getId().equals(r.purchaseId())).findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.ALLOCATION_INVALID, Map.of("purchaseId", r.purchaseId())));
            targets = List.of(chosen);
        }
        BigDecimal remaining = amount;
        for (Purchase purchase : targets) {
            if (remaining.signum() <= 0) break;
            BigDecimal cross = purchase.getCurrencyCode().equals(cur) ? BigDecimal.ONE
                    : rate.divide(currency.currentRate(purchase.getCurrencyCode()), Money.RATE_SCALE, RoundingMode.HALF_UP);
            BigDecimal canApply = Money.amount(remaining.multiply(cross));
            BigDecimal applied = Money.min(canApply, purchase.getBalanceDue());
            if (applied.signum() <= 0) continue;
            BigDecimal paid = applied.compareTo(canApply) == 0 ? remaining : Money.amount(applied.divide(cross, 10, RoundingMode.HALF_UP));
            if (paid.compareTo(remaining) > 0) paid = remaining;
            allocate(p, purchase, applied, paid, cross);
            remaining = remaining.subtract(paid);
        }
        if (remaining.signum() > 0) {
            p.setUnallocatedAmount(remaining);
            ledger.debit(supplier.getId(), cur, SupplierEntryType.PAYMENT, remaining, "SUPPLIER_PAYMENT", p.getId(), p.getPaymentNo(), "Advance");
        }
        audit.log(AuditEvent.of(AuditAction.PAYMENT, AuditModule.PAYMENTS, "SupplierPayment", p.getId(), p.getPaymentNo(), "Supplier payment")
                .withDetails(Map.of("supplier", supplier.getName(), "amount", amount.toPlainString(), "currency", cur)));
        return toResponse(p);
    }

    private void allocate(SupplierPayment p, Purchase purchase, BigDecimal applied, BigDecimal paid, BigDecimal cross) {
        p.getAllocations().add(new SupplierPaymentAllocation(p, purchase.getId(), applied, paid, cross));
        purchase.addPayment(applied);
        ledger.debit(purchase.getSupplierId(), purchase.getCurrencyCode(), SupplierEntryType.PAYMENT, applied, "SUPPLIER_PAYMENT", p.getId(),
                p.getPaymentNo(), purchase.getPurchaseNo());
    }

    /**
     * Undo a payment (Admin, with reason): the cash comes back into the account and the purchases owe again.
     * Not possible once part of it was released by a purchase cancellation.
     */
    @Transactional
    public SupplierPaymentResponse reverse(Long id, String reason) {
        SupplierPayment p = payments.lock(id).orElseThrow(() -> new NotFoundException("SupplierPayment", id));
        if (p.getStatus() != PaymentStatus.POSTED || p.getAllocations().stream().anyMatch(SupplierPaymentAllocation::isReleased)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, Map.of("status", p.getStatus().name()));
        }
        cash.post(p.getCashAccountId(), CashDirection.IN, p.getAmount(), CashTxnType.REVERSAL, p.getExchangeRate(),
                new CashSource("SUPPLIER_PAYMENT", p.getId(), p.getPaymentNo()), "Reversal: " + reason);
        for (SupplierPaymentAllocation a : p.getAllocations()) {
            Purchase purchase = purchases.lock(a.getPurchaseId()).orElseThrow();
            purchase.removePayment(a.getAppliedAmount());
            ledger.credit(p.getSupplierId(), purchase.getCurrencyCode(), SupplierEntryType.PAYMENT_REVERSAL, a.getAppliedAmount(), "SUPPLIER_PAYMENT",
                    p.getId(), p.getPaymentNo(), reason);
        }
        if (p.getUnallocatedAmount().signum() > 0) {
            ledger.credit(p.getSupplierId(), p.getCurrencyCode(), SupplierEntryType.PAYMENT_REVERSAL, p.getUnallocatedAmount(), "SUPPLIER_PAYMENT",
                    p.getId(), p.getPaymentNo(), reason);
            p.setUnallocatedAmount(Money.ZERO);
        }
        p.reverse(reason, clock.now(), SecurityUtils.currentUserId().orElse(null));
        audit.log(AuditEvent.of(AuditAction.REVERSE, AuditModule.PAYMENTS, "SupplierPayment", p.getId(), p.getPaymentNo(), "Supplier payment reversed")
                .withDetails(Map.of("reason", reason)));
        return toResponse(p);
    }

    /**
     * Called when a confirmed purchase is cancelled. Each payment applied to it is either refunded in cash
     * (back into the default account of the payment's currency) or kept by the supplier as credit for future purchases.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseForCancelledPurchase(Purchase purchase, List<SupplierPaymentAllocation> allocations, boolean refund) {
        for (SupplierPaymentAllocation a : allocations) {
            SupplierPayment p = a.getPayment();
            if (a.isReleased() || p.getStatus() != PaymentStatus.POSTED) continue;
            a.release();
            if (refund) {
                CashAccount account = cash.defaultAccount(p.getCurrencyCode());
                cash.post(account.getId(), CashDirection.IN, a.getPaidAmount(), CashTxnType.SUPPLIER_REFUND, p.getExchangeRate(),
                        new CashSource("PURCHASE", purchase.getId(), purchase.getPurchaseNo()), "Refund for cancelled " + purchase.getPurchaseNo());
                ledger.credit(purchase.getSupplierId(), purchase.getCurrencyCode(), SupplierEntryType.SUPPLIER_REFUND, a.getAppliedAmount(), "PURCHASE",
                        purchase.getId(), purchase.getPurchaseNo(), p.getPaymentNo());
            } else {
                p.setUnallocatedAmount(p.getUnallocatedAmount().add(a.getPaidAmount()));
            }
        }
    }

    @Transactional(readOnly = true)
    public Page<SupplierPaymentResponse> search(Long supplierId, PaymentStatus status, LocalDate from, LocalDate to, String q, Pageable pageable) {
        Specification<SupplierPayment> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (supplierId != null) ps.add(cb.equal(root.get("supplierId"), supplierId));
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("businessDate"), from));
            if (to != null) ps.add(cb.lessThanOrEqualTo(root.get("businessDate"), to));
            if (q != null && !q.isBlank()) ps.add(cb.like(cb.lower(root.get("paymentNo")), "%" + q.trim().toLowerCase() + "%"));
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return payments.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public SupplierPaymentResponse get(Long id) {
        return toResponse(payments.findById(id).orElseThrow(() -> new NotFoundException("SupplierPayment", id)));
    }

    @Transactional(readOnly = true)
    public SupplierPaymentResponse toResponse(SupplierPayment p) {
        Map<Long, Purchase> purchaseMap = purchases.findAllById(p.getAllocations().stream().map(SupplierPaymentAllocation::getPurchaseId).toList())
                .stream().collect(Collectors.toMap(Purchase::getId, Function.identity()));
        List<SupplierPaymentResponse.Allocation> allocs = p.getAllocations().stream().map(a -> {
            Purchase pu = purchaseMap.get(a.getPurchaseId());
            return new SupplierPaymentResponse.Allocation(a.getPurchaseId(), pu == null ? null : pu.getPurchaseNo(), pu == null ? null : pu.getCurrencyCode(),
                    a.getAppliedAmount(), a.getPaidAmount(), a.getCrossRate(), a.isReleased());
        }).toList();
        String supplierName = suppliers.get(p.getSupplierId()).getName();
        String accountName = cashAccounts.findById(p.getCashAccountId()).map(CashAccount::getName).orElse(null);
        String by = users.names(List.of(p.getCreatedBy() == null ? -1L : p.getCreatedBy())).get(p.getCreatedBy());
        return new SupplierPaymentResponse(p.getId(), p.getPaymentNo(), p.getSupplierId(), supplierName, p.getPaymentKind(), p.getPaymentDate(),
                p.getBusinessDate(), p.getCurrencyCode(), p.getAmount(), p.getExchangeRate(), p.getCashAccountId(), accountName, p.getUnallocatedAmount(),
                p.getStatus(), p.getNotes(), p.getReversalReason(), by, allocs);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** True if the purchase is in a state where payments can be applied. */
    public static boolean payable(Purchase p) {
        return p.getStatus() == PurchaseStatus.CONFIRMED && p.getBalanceDue().signum() > 0;
    }
}
