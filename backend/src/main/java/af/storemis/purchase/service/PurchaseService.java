package af.storemis.purchase.service;

import af.storemis.audit.entity.AuditAction;
import af.storemis.audit.entity.AuditModule;
import af.storemis.audit.service.AuditEvent;
import af.storemis.audit.service.AuditService;
import af.storemis.common.exception.BusinessException;
import af.storemis.common.exception.ErrorCode;
import af.storemis.common.exception.NotFoundException;
import af.storemis.common.money.Money;
import af.storemis.common.security.SecurityUtils;
import af.storemis.common.time.BusinessClock;
import af.storemis.currency.service.CurrencyService;
import af.storemis.inventory.entity.MovementType;
import af.storemis.inventory.entity.StockSourceType;
import af.storemis.inventory.service.InventoryPostingService;
import af.storemis.inventory.service.PostingContext;
import af.storemis.numbering.DocumentNumberService;
import af.storemis.numbering.DocumentType;
import af.storemis.payment.entity.SupplierPaymentAllocation;
import af.storemis.payment.repository.SupplierPaymentAllocationRepository;
import af.storemis.payment.service.SupplierPaymentService;
import af.storemis.product.entity.Product;
import af.storemis.product.repository.ProductRepository;
import af.storemis.purchase.dto.CancelPurchaseRequest;
import af.storemis.purchase.dto.ConfirmPurchaseRequest;
import af.storemis.purchase.dto.PurchaseRequest;
import af.storemis.purchase.dto.PurchaseResponse;
import af.storemis.purchase.entity.Purchase;
import af.storemis.purchase.entity.PurchaseItem;
import af.storemis.purchase.entity.PurchaseStatus;
import af.storemis.purchase.repository.PurchaseRepository;
import af.storemis.supplier.entity.Supplier;
import af.storemis.supplier.entity.SupplierEntryType;
import af.storemis.supplier.service.SupplierLedgerService;
import af.storemis.supplier.service.SupplierService;
import af.storemis.user.service.UserDirectory;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purchases: drafts are editable; confirming posts everything at once —
 * stock in at landed cost (weighted average), the supplier payable, and any payment made now (cash out).
 */
@Service
public class PurchaseService {

    private final PurchaseRepository purchases;
    private final ProductRepository products;
    private final SupplierService suppliers;
    private final SupplierLedgerService ledger;
    private final SupplierPaymentService payments;
    private final SupplierPaymentAllocationRepository allocations;
    private final InventoryPostingService inventory;
    private final CurrencyService currency;
    private final DocumentNumberService numbers;
    private final UserDirectory users;
    private final AuditService audit;
    private final BusinessClock clock;

    public PurchaseService(PurchaseRepository purchases, ProductRepository products, SupplierService suppliers, SupplierLedgerService ledger,
            SupplierPaymentService payments, SupplierPaymentAllocationRepository allocations, InventoryPostingService inventory, CurrencyService currency,
            DocumentNumberService numbers, UserDirectory users, AuditService audit, BusinessClock clock) {
        this.purchases = purchases;
        this.products = products;
        this.suppliers = suppliers;
        this.ledger = ledger;
        this.payments = payments;
        this.allocations = allocations;
        this.inventory = inventory;
        this.currency = currency;
        this.numbers = numbers;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public PurchaseResponse createDraft(PurchaseRequest r) {
        Purchase p = new Purchase();
        fill(p, r);
        purchases.save(p);
        audit.log(AuditEvent.of(AuditAction.CREATE, AuditModule.PURCHASES, "Purchase", p.getId(), "DRAFT-" + p.getId(), "Purchase draft created"));
        return toResponse(p);
    }

    @Transactional
    public PurchaseResponse updateDraft(Long id, PurchaseRequest r) {
        Purchase p = lockDraft(id);
        p.checkVersion(r.version());
        fill(p, r);
        purchases.saveAndFlush(p);
        return toResponse(p);
    }

    /** Drafts are not financial records yet, so they may be discarded. */
    @Transactional
    public void deleteDraft(Long id) {
        Purchase p = lockDraft(id);
        purchases.delete(p);
        audit.log(AuditEvent.of(AuditAction.CANCEL, AuditModule.PURCHASES, "Purchase", id, "DRAFT-" + id, "Purchase draft discarded"));
    }

    @Transactional
    public PurchaseResponse confirm(Long id, ConfirmPurchaseRequest r) {
        Purchase p = purchases.lock(id).orElseThrow(() -> new NotFoundException("Purchase", id));
        if (p.getStatus() == PurchaseStatus.CONFIRMED) return toResponse(p); // a retried request changes nothing
        if (p.getStatus() != PurchaseStatus.DRAFT) throw new BusinessException(ErrorCode.INVALID_STATE, Map.of("status", p.getStatus().name()));
        Supplier supplier = suppliers.requireActive(p.getSupplierId());
        BigDecimal paid = Money.amount(r == null ? null : r.amountPaid());
        if (paid.compareTo(p.getGrandTotal()) > 0) throw new BusinessException(ErrorCode.AMOUNT_EXCEEDS_TOTAL, Map.of("total", p.getGrandTotal().toPlainString()));
        if (paid.signum() > 0 && (r.cashAccountId() == null)) throw new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("field", "cashAccountId"));
        Map<Long, Product> productMap = products.findAllById(p.getItems().stream().map(PurchaseItem::getProductId).toList()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        for (PurchaseItem item : p.getItems()) {
            Product product = productMap.get(item.getProductId());
            if (product == null || !product.isActive()) {
                throw new BusinessException(ErrorCode.PRODUCT_INACTIVE, Map.of("product", item.getProductName()));
            }
        }

        String number = numbers.next(DocumentType.PURCHASE);
        p.confirm(number, clock.today(), clock.now(), SecurityUtils.currentUserId().orElse(null));
        inventory.lock(productMap.keySet());
        PostingContext ctx = new PostingContext(StockSourceType.PURCHASE, p.getId(), number, p.getBusinessDate(), supplier.getName());
        for (PurchaseItem item : p.getItems()) {
            inventory.receive(MovementType.PURCHASE, item.getProductId(), item.getQuantity(), item.getLandedUnitCostBase(), ctx, item.getId());
        }
        ledger.credit(supplier.getId(), p.getCurrencyCode(), SupplierEntryType.PURCHASE, p.getGrandTotal(), "PURCHASE", p.getId(), number,
                p.getSupplierInvoiceNo());
        if (paid.signum() > 0) payments.payAtPurchase(p, paid, r.cashAccountId());
        purchases.saveAndFlush(p);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("supplier", supplier.getName());
        details.put("total", p.getGrandTotal().toPlainString() + " " + p.getCurrencyCode());
        details.put("paid", paid.toPlainString());
        audit.log(AuditEvent.of(AuditAction.CONFIRM, AuditModule.PURCHASES, "Purchase", p.getId(), number, "Purchase confirmed").withDetails(details));
        return toResponse(p);
    }

    /**
     * Cancels a confirmed purchase (Admin, with reason). The goods leave stock at the cost they came in, the payable is
     * removed, and any payment is refunded in cash or kept as supplier credit. Not possible if the goods were already sold.
     */
    @Transactional
    public PurchaseResponse cancel(Long id, CancelPurchaseRequest r) {
        Purchase p = purchases.lock(id).orElseThrow(() -> new NotFoundException("Purchase", id));
        if (p.getStatus() != PurchaseStatus.CONFIRMED || p.getReturnedAmount().signum() > 0) {
            throw new BusinessException(ErrorCode.INVALID_STATE, Map.of("status", p.getStatus().name()));
        }
        inventory.lock(p.getItems().stream().map(PurchaseItem::getProductId).toList());
        PostingContext ctx = new PostingContext(StockSourceType.PURCHASE, p.getId(), p.getPurchaseNo(), clock.today(), r.reason());
        for (PurchaseItem item : p.getItems()) {
            inventory.removeAtCost(MovementType.PURCHASE_CANCEL, item.getProductId(), item.getQuantity(), item.getLandedUnitCostBase(), ctx, item.getId());
        }
        ledger.debit(p.getSupplierId(), p.getCurrencyCode(), SupplierEntryType.PURCHASE_CANCEL, p.getGrandTotal(), "PURCHASE", p.getId(),
                p.getPurchaseNo(), r.reason());
        payments.releaseForCancelledPurchase(p, allocations.findByPurchaseId(p.getId()), "REFUND".equals(r.paymentHandling()));
        p.cancel(r.reason().trim(), clock.now(), SecurityUtils.currentUserId().orElse(null));
        purchases.saveAndFlush(p);
        audit.log(AuditEvent.of(AuditAction.CANCEL, AuditModule.PURCHASES, "Purchase", p.getId(), p.getPurchaseNo(), "Purchase cancelled")
                .withDetails(Map.of("reason", r.reason(), "paymentHandling", r.paymentHandling())));
        return toResponse(p);
    }

    @Transactional(readOnly = true)
    public PurchaseResponse get(Long id) {
        return toResponse(purchases.findById(id).orElseThrow(() -> new NotFoundException("Purchase", id)));
    }

    @Transactional(readOnly = true)
    public List<PurchaseResponse> openForSupplier(Long supplierId) {
        return purchases.findOpenForSupplier(supplierId).stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public Page<PurchaseResponse> search(String q, Long supplierId, PurchaseStatus status, String paymentStatus, String currencyCode, LocalDate from,
            LocalDate to, Pageable pageable) {
        Specification<Purchase> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (supplierId != null) ps.add(cb.equal(root.get("supplierId"), supplierId));
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            if (paymentStatus != null && !paymentStatus.isBlank()) ps.add(cb.equal(root.get("paymentStatus"), paymentStatus));
            if (currencyCode != null && !currencyCode.isBlank()) ps.add(cb.equal(root.get("currencyCode"), currencyCode.toUpperCase()));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("purchaseDate"), from));
            if (to != null) ps.add(cb.lessThanOrEqualTo(root.get("purchaseDate"), to));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(cb.coalesce(root.get("purchaseNo"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("supplierInvoiceNo"), "")), like)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Page<Purchase> page = purchases.findAll(spec, pageable);
        return page.map(this::summary);
    }

    // ---- internals ----

    private Purchase lockDraft(Long id) {
        Purchase p = purchases.lock(id).orElseThrow(() -> new NotFoundException("Purchase", id));
        if (p.getStatus() != PurchaseStatus.DRAFT) throw new BusinessException(ErrorCode.INVALID_STATE, Map.of("status", p.getStatus().name()));
        return p;
    }

    private void fill(Purchase p, PurchaseRequest r) {
        suppliers.requireActive(r.supplierId());
        String cur = currency.requireActive(r.currency()).getCode();
        BigDecimal rate = currency.resolveRate(cur, r.exchangeRate());
        Map<Long, Product> productMap = products.findAllById(r.items().stream().map(PurchaseRequest.Item::productId).toList()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<PurchaseCalculator.LineInput> inputs = new ArrayList<>();
        for (PurchaseRequest.Item i : r.items()) {
            Product product = productMap.get(i.productId());
            if (product == null) throw new NotFoundException("Product", i.productId());
            if (!product.isActive()) throw new BusinessException(ErrorCode.PRODUCT_INACTIVE, Map.of("product", product.getName()));
            InventoryPostingService.checkWholeQuantity(product, i.quantity());
            inputs.add(new PurchaseCalculator.LineInput(i.quantity(), i.unitCost(), Money.nz(i.discount())));
        }
        PurchaseCalculator.Result calc = PurchaseCalculator.calculate(inputs, Money.nz(r.overallDiscount()), rate);

        p.setHeader(r.supplierId(), blankToNull(r.supplierInvoiceNo()), r.purchaseDate(), cur, rate, blankToNull(r.notes()));
        p.getItems().clear();
        purchases.flush(); // remove old lines before inserting renumbered ones (unique purchase_id + line_no)
        for (int i = 0; i < r.items().size(); i++) {
            PurchaseRequest.Item in = r.items().get(i);
            Product product = productMap.get(in.productId());
            PurchaseItem item = new PurchaseItem(p, (short) (i + 1), product.getId(), product.getName(), product.getUnit().getName(), in.quantity(),
                    in.unitCost(), Money.amount(in.discount()));
            PurchaseCalculator.LineResult lr = calc.lines().get(i);
            item.applyTotals(lr.lineTotal(), lr.overallShare(), lr.landedUnitCostBase());
            p.getItems().add(item);
        }
        p.setTotals(calc.subtotal(), calc.itemDiscountTotal(), calc.overallDiscount(), calc.grandTotal());
    }

    private PurchaseResponse summary(Purchase p) {
        return build(p, false);
    }

    private PurchaseResponse toResponse(Purchase p) {
        return build(p, true);
    }

    private PurchaseResponse build(Purchase p, boolean full) {
        String supplierName = suppliers.get(p.getSupplierId()).getName();
        String by = users.names(List.of(p.getCreatedBy() == null ? -1L : p.getCreatedBy())).get(p.getCreatedBy());
        List<PurchaseResponse.Item> items = !full ? List.of() : p.getItems().stream().map(i -> new PurchaseResponse.Item(i.getId(), i.getLineNo(),
                i.getProductId(), i.getProductName(), i.getUnitName(), i.getQuantity(), i.getUnitCost(), i.getDiscountAmount(), i.getLineTotal(),
                i.getOverallDiscountShare(), i.getLandedUnitCostBase(), i.getReturnedQuantity())).toList();
        List<PurchaseResponse.Payment> pays = !full || p.getId() == null ? List.of() : allocations.findByPurchaseId(p.getId()).stream()
                .map(SupplierPaymentAllocation.class::cast)
                .map(a -> new PurchaseResponse.Payment(a.getPayment().getId(), a.getPayment().getPaymentNo(), a.getPayment().getPaymentDate(),
                        a.getPayment().getCurrencyCode(), a.getPaidAmount(), a.getAppliedAmount(), a.getPayment().getStatus().name(), a.isReleased()))
                .toList();
        return new PurchaseResponse(p.getId(), p.getPurchaseNo(), p.getSupplierId(), supplierName, p.getSupplierInvoiceNo(), p.getPurchaseDate(),
                p.getBusinessDate(), p.getCurrencyCode(), p.getExchangeRate(), p.getSubtotal(), p.getItemDiscountTotal(), p.getOverallDiscount(),
                p.getGrandTotal(), p.getAmountPaid(), p.getReturnedAmount(), p.getBalanceDue(), p.getStatus(), p.getPaymentStatus(), p.getNotes(),
                p.getCancelReason(), p.getCreatedAt(), by, p.getConfirmedAt(), p.getVersion(), items, pays);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
