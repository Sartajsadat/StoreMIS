package af.storemis.returns.service;

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
import af.storemis.inventory.entity.MovementType;
import af.storemis.inventory.entity.ProductStock;
import af.storemis.inventory.entity.StockSourceType;
import af.storemis.inventory.repository.ProductStockRepository;
import af.storemis.inventory.service.InventoryPostingService;
import af.storemis.inventory.service.PostingContext;
import af.storemis.numbering.DocumentNumberService;
import af.storemis.numbering.DocumentType;
import af.storemis.product.entity.Product;
import af.storemis.product.repository.ProductRepository;
import af.storemis.returns.dto.ReturnLine;
import af.storemis.returns.dto.ReturnRequest;
import af.storemis.returns.dto.ReturnResponse;
import af.storemis.returns.dto.ReturnableDocument;
import af.storemis.returns.entity.PurchaseReturn;
import af.storemis.returns.entity.PurchaseReturnItem;
import af.storemis.returns.repository.PurchaseReturnRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purchase returns (goods sent back to the supplier). The value first reduces what we still owe on the purchase; the
 * rest comes back as a cash refund from the supplier or stays as supplier credit. Goods leave stock at their original
 * landed cost and only while they are still in stock.
 */
@Service
public class PurchaseReturnService {

    private final PurchaseReturnRepository returns;
    private final PurchaseRepository purchases;
    private final ProductRepository products;
    private final ProductStockRepository stock;
    private final SupplierService suppliers;
    private final SupplierLedgerService ledger;
    private final InventoryPostingService inventory;
    private final CashPostingService cash;
    private final CashAccountRepository cashAccounts;
    private final DocumentNumberService numbers;
    private final UserDirectory users;
    private final AuditService audit;
    private final BusinessClock clock;

    public PurchaseReturnService(PurchaseReturnRepository returns, PurchaseRepository purchases, ProductRepository products,
            ProductStockRepository stock, SupplierService suppliers, SupplierLedgerService ledger, InventoryPostingService inventory, CashPostingService cash,
            CashAccountRepository cashAccounts, DocumentNumberService numbers, UserDirectory users, AuditService audit, BusinessClock clock) {
        this.returns = returns;
        this.purchases = purchases;
        this.products = products;
        this.stock = stock;
        this.suppliers = suppliers;
        this.ledger = ledger;
        this.inventory = inventory;
        this.cash = cash;
        this.cashAccounts = cashAccounts;
        this.numbers = numbers;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReturnableDocument returnable(Long purchaseId) {
        return describe(purchases.findById(purchaseId).orElseThrow(() -> new NotFoundException("Purchase", purchaseId)));
    }

    @Transactional(readOnly = true)
    public ReturnableDocument lookup(String purchaseNo) {
        return describe(purchases.findByPurchaseNoIgnoreCase(purchaseNo == null ? "" : purchaseNo.trim())
                .orElseThrow(() -> new NotFoundException("Purchase", purchaseNo)));
    }

    private ReturnableDocument describe(Purchase s) {
        if (s.getStatus() == PurchaseStatus.DRAFT) throw new NotFoundException("Purchase", s.getId());
        Supplier c = suppliers.get(s.getSupplierId());
        List<Long> productIds = s.getItems().stream().map(PurchaseItem::getProductId).toList();
        Map<Long, Product> prods = products.findAllById(productIds).stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, BigDecimal> onHand = stock.findByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(ProductStock::getProductId, ProductStock::getQuantityOnHand));
        List<ReturnableDocument.Line> lines = s.getItems().stream().map(i -> {
            Product p = prods.get(i.getProductId());
            BigDecimal net = ReturnMath.netLine(i.getLineTotal(), i.getOverallDiscountShare());
            return new ReturnableDocument.Line(i.getId(), i.getProductId(), i.getProductName(), i.getUnitName(), p != null && p.getUnit().isAllowDecimal(),
                    i.getQuantity(), i.getReturnedQuantity(), s.getStatus() == PurchaseStatus.CONFIRMED ? i.getReturnableQuantity() : BigDecimal.ZERO,
                    ReturnMath.unitNet(net, i.getQuantity()), onHand.get(i.getProductId()));
        }).toList();
        return new ReturnableDocument(s.getId(), s.getPurchaseNo(), c.getId(), c.getName(), false, s.getBusinessDate(), s.getCurrencyCode(),
                s.getGrandTotal(), s.getAmountPaid(), s.getReturnedAmount(), s.getBalanceDue(), s.getStatus().name(), lines);
    }

    @Transactional
    public ReturnResponse create(ReturnRequest r) {
        if (r.clientRequestId() != null) {
            Optional<PurchaseReturn> existing = returns.findByClientRequestId(r.clientRequestId());
            if (existing.isPresent()) return toResponse(existing.get());
        }
        LocalDate today = clock.today();
        cash.ensureDayOpen(today);
        Purchase purchase = purchases.lock(r.documentId()).orElseThrow(() -> new NotFoundException("Purchase", r.documentId()));
        if (purchase.getStatus() != PurchaseStatus.CONFIRMED) throw new BusinessException(ErrorCode.INVALID_STATE, Map.of("status", purchase.getStatus().name()));
        Supplier supplier = suppliers.get(purchase.getSupplierId());
        Map<Long, PurchaseItem> items = purchase.getItems().stream().collect(Collectors.toMap(PurchaseItem::getId, Function.identity()));
        Set<Long> seen = new HashSet<>();
        for (ReturnLine l : r.items()) {
            if (!items.containsKey(l.itemId()) || !seen.add(l.itemId())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("field", "items", "itemId", l.itemId()));
            }
        }
        inventory.lock(r.items().stream().map(l -> items.get(l.itemId()).getProductId()).toList());

        PurchaseReturn ret = returns.save(new PurchaseReturn(numbers.next(DocumentType.PURCHASE_RETURN), r.clientRequestId(), purchase.getId(), supplier.getId(),
                clock.now(), today, purchase.getCurrencyCode(), purchase.getExchangeRate(), r.reason().trim(), SecurityUtils.currentUserId().orElse(null)));
        PostingContext ctx = new PostingContext(StockSourceType.PURCHASE_RETURN, ret.getId(), ret.getReturnNo(), today, purchase.getPurchaseNo());
        BigDecimal total = Money.ZERO;
        BigDecimal cost = Money.ZERO;
        for (ReturnLine l : r.items()) {
            PurchaseItem item = items.get(l.itemId());
            BigDecimal qty = l.quantity();
            if (qty.compareTo(item.getReturnableQuantity()) > 0) {
                throw new BusinessException(ErrorCode.RETURN_QUANTITY_EXCEEDED,
                        Map.of("product", item.getProductName(), "available", item.getReturnableQuantity().stripTrailingZeros().toPlainString()));
            }
            Product product = products.findById(item.getProductId()).orElseThrow(() -> new NotFoundException("Product", item.getProductId()));
            InventoryPostingService.checkWholeQuantity(product, qty);
            BigDecimal net = ReturnMath.netLine(item.getLineTotal(), item.getOverallDiscountShare());
            BigDecimal value = ReturnMath.value(net, item.getQuantity(), returns.returnedValue(item.getId()), item.getReturnableQuantity(), qty);
            PurchaseReturnItem ri = new PurchaseReturnItem(ret, item.getId(), item.getProductId(), item.getProductName(), item.getUnitName(), qty,
                    ReturnMath.unitNet(net, item.getQuantity()), value, item.getLandedUnitCostBase(), blankToNull(l.reason()));
            ret.getItems().add(ri);
            item.addReturned(qty);
            total = total.add(value);
            cost = cost.add(Money.amount(qty.multiply(item.getLandedUnitCostBase())));
        }
        returns.saveAndFlush(ret);
        for (PurchaseReturnItem ri : ret.getItems()) {
            inventory.removeAtCost(MovementType.PURCHASE_RETURN, ri.getProductId(), ri.getQuantity(), ri.getUnitCostBase(), ctx, ri.getId());
        }

        // Settlement: first what we still owe, then a cash refund from the supplier or supplier credit.
        BigDecimal reduction = Money.min(total, purchase.getBalanceDue());
        BigDecimal rest = total.subtract(reduction);
        BigDecimal refund = Money.ZERO;
        BigDecimal credit = Money.ZERO;
        Long accountId = null;
        if (rest.signum() > 0) {
            boolean doRefund = "REFUND".equals(r.settlement());
            if (!doRefund && !"CREDIT".equals(r.settlement())) throw new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("field", "settlement"));
            if (doRefund) {
                if (r.refundCashAccountId() == null) throw new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("field", "refundCashAccountId"));
                accountId = cash.requireAccount(r.refundCashAccountId(), purchase.getCurrencyCode()).getId();
                refund = rest;
                cash.post(accountId, CashDirection.IN, refund, CashTxnType.SUPPLIER_REFUND, purchase.getExchangeRate(),
                        new CashSource("PURCHASE_RETURN", ret.getId(), ret.getReturnNo()), "Refund " + ret.getReturnNo() + " (" + purchase.getPurchaseNo() + ")");
            } else {
                credit = rest;
            }
        }
        ret.settle(total, cost, reduction, refund, accountId, credit);
        purchase.applyReturn(total, rest);
        ledger.debit(supplier.getId(), purchase.getCurrencyCode(), SupplierEntryType.PURCHASE_RETURN, total, "PURCHASE_RETURN", ret.getId(),
                ret.getReturnNo(), purchase.getPurchaseNo());
        if (refund.signum() > 0) {
            ledger.credit(supplier.getId(), purchase.getCurrencyCode(), SupplierEntryType.SUPPLIER_REFUND, refund, "PURCHASE_RETURN", ret.getId(),
                    ret.getReturnNo(), "Cash refund");
        }
        returns.saveAndFlush(ret);
        purchases.saveAndFlush(purchase);
        audit.log(AuditEvent.of(AuditAction.RETURN, AuditModule.RETURNS, "PurchaseReturn", ret.getId(), ret.getReturnNo(), "Purchase return")
                .withDetails(Map.of("purchase", purchase.getPurchaseNo(), "total", total.toPlainString(), "currency", purchase.getCurrencyCode(), "refund",
                        refund.toPlainString(), "credit", credit.toPlainString(), "reason", r.reason())));
        return toResponse(ret);
    }

    @Transactional(readOnly = true)
    public ReturnResponse get(Long id) {
        return toResponse(returns.findById(id).orElseThrow(() -> new NotFoundException("PurchaseReturn", id)));
    }

    @Transactional(readOnly = true)
    public List<ReturnResponse> forPurchase(Long purchaseId) {
        return returns.findByPurchaseIdOrderByIdAsc(purchaseId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<ReturnResponse> search(String q, Long supplierId, LocalDate from, LocalDate to, Pageable pageable) {
        Specification<PurchaseReturn> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (supplierId != null) ps.add(cb.equal(root.get("supplierId"), supplierId));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("businessDate"), from));
            if (to != null) ps.add(cb.lessThanOrEqualTo(root.get("businessDate"), to));
            if (q != null && !q.isBlank()) ps.add(cb.like(cb.lower(root.get("returnNo")), "%" + q.trim().toLowerCase() + "%"));
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return returns.findAll(spec, pageable).map(this::toResponse);
    }

    private ReturnResponse toResponse(PurchaseReturn r) {
        Purchase purchase = purchases.findById(r.getPurchaseId()).orElseThrow();
        Supplier c = suppliers.get(r.getSupplierId());
        String account = r.getRefundCashAccountId() == null ? null : cashAccounts.findById(r.getRefundCashAccountId()).map(CashAccount::getName).orElse(null);
        String by = users.names(r.getCreatedBy() == null ? List.of() : List.of(r.getCreatedBy())).get(r.getCreatedBy());
        List<ReturnResponse.Item> items = r.getItems().stream().map(i -> new ReturnResponse.Item(i.getId(), i.getPurchaseItemId(), i.getProductId(),
                i.getProductName(), i.getUnitName(), i.getQuantity(), i.getUnitNetPrice(), i.getLineTotal(), null, i.getReason())).toList();
        return new ReturnResponse(r.getId(), r.getReturnNo(), "PURCHASE", purchase.getId(), purchase.getPurchaseNo(), c.getId(), c.getName(), r.getReturnDate(),
                r.getBusinessDate(), r.getCurrencyCode(), r.getExchangeRate(), r.getTotalAmount(), r.getTotalCostBase(),
                r.getPayableReduction(), r.getCashRefundAmount(), r.getRefundCashAccountId(), account, r.getCreditAmount(), r.getReason(), by, items);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
