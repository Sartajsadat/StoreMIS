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
import af.storemis.common.security.Permission;
import af.storemis.common.security.SecurityUtils;
import af.storemis.common.time.BusinessClock;
import af.storemis.customer.entity.Customer;
import af.storemis.customer.entity.CustomerEntryType;
import af.storemis.customer.service.CustomerLedgerService;
import af.storemis.customer.service.CustomerService;
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
import af.storemis.returns.entity.SalesReturn;
import af.storemis.returns.entity.SalesReturnItem;
import af.storemis.returns.repository.SalesReturnRepository;
import af.storemis.sales.entity.Sale;
import af.storemis.sales.entity.SaleItem;
import af.storemis.sales.entity.SaleStatus;
import af.storemis.sales.repository.SaleRepository;
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
 * Sales returns (customer brings goods back). The value first reduces what is still owed on the invoice; the rest is
 * refunded in cash or kept as customer credit. Sellable goods go back to stock at their original cost; damaged goods
 * do not (their cost becomes an inventory loss in the P&L).
 */
@Service
public class SalesReturnService {

    private final SalesReturnRepository returns;
    private final SaleRepository sales;
    private final ProductRepository products;
    private final ProductStockRepository stock;
    private final CustomerService customers;
    private final CustomerLedgerService ledger;
    private final InventoryPostingService inventory;
    private final CashPostingService cash;
    private final CashAccountRepository cashAccounts;
    private final DocumentNumberService numbers;
    private final UserDirectory users;
    private final AuditService audit;
    private final BusinessClock clock;

    public SalesReturnService(SalesReturnRepository returns, SaleRepository sales, ProductRepository products, ProductStockRepository stock,
            CustomerService customers, CustomerLedgerService ledger, InventoryPostingService inventory, CashPostingService cash,
            CashAccountRepository cashAccounts, DocumentNumberService numbers, UserDirectory users, AuditService audit, BusinessClock clock) {
        this.returns = returns;
        this.sales = sales;
        this.products = products;
        this.stock = stock;
        this.customers = customers;
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
    public ReturnableDocument returnable(Long saleId) {
        return describe(sales.findById(saleId).orElseThrow(() -> new NotFoundException("Sale", saleId)));
    }

    @Transactional(readOnly = true)
    public ReturnableDocument lookup(String invoiceNo) {
        return describe(sales.findByInvoiceNoIgnoreCase(invoiceNo == null ? "" : invoiceNo.trim())
                .orElseThrow(() -> new NotFoundException("Sale", invoiceNo)));
    }

    private ReturnableDocument describe(Sale s) {
        if (s.getStatus() == SaleStatus.DRAFT) throw new NotFoundException("Sale", s.getId());
        Customer c = customers.get(s.getCustomerId());
        List<Long> productIds = s.getItems().stream().map(SaleItem::getProductId).toList();
        Map<Long, Product> prods = products.findAllById(productIds).stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, BigDecimal> onHand = stock.findByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(ProductStock::getProductId, ProductStock::getQuantityOnHand));
        List<ReturnableDocument.Line> lines = s.getItems().stream().map(i -> {
            Product p = prods.get(i.getProductId());
            BigDecimal net = ReturnMath.netLine(i.getLineTotal(), i.getOverallDiscountShare());
            return new ReturnableDocument.Line(i.getId(), i.getProductId(), i.getProductName(), i.getUnitName(), p != null && p.getUnit().isAllowDecimal(),
                    i.getQuantity(), i.getReturnedQuantity(), s.getStatus() == SaleStatus.CONFIRMED ? i.getReturnableQuantity() : BigDecimal.ZERO,
                    ReturnMath.unitNet(net, i.getQuantity()), onHand.get(i.getProductId()));
        }).toList();
        return new ReturnableDocument(s.getId(), s.getInvoiceNo(), c.getId(), c.getName(), c.isWalkIn(), s.getBusinessDate(), s.getCurrencyCode(),
                s.getGrandTotal(), s.getAmountPaid(), s.getReturnedAmount(), s.getBalanceDue(), s.getStatus().name(), lines);
    }

    @Transactional
    public ReturnResponse create(ReturnRequest r) {
        if (r.clientRequestId() != null) {
            Optional<SalesReturn> existing = returns.findByClientRequestId(r.clientRequestId());
            if (existing.isPresent()) return toResponse(existing.get());
        }
        LocalDate today = clock.today();
        cash.ensureDayOpen(today);
        Sale sale = sales.lock(r.documentId()).orElseThrow(() -> new NotFoundException("Sale", r.documentId()));
        if (sale.getStatus() != SaleStatus.CONFIRMED) throw new BusinessException(ErrorCode.INVALID_STATE, Map.of("status", sale.getStatus().name()));
        Customer customer = customers.get(sale.getCustomerId());
        Map<Long, SaleItem> saleItems = sale.getItems().stream().collect(Collectors.toMap(SaleItem::getId, Function.identity()));
        Set<Long> seen = new HashSet<>();
        for (ReturnLine l : r.items()) {
            if (!saleItems.containsKey(l.itemId()) || !seen.add(l.itemId())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("field", "items", "itemId", l.itemId()));
            }
        }
        inventory.lock(r.items().stream().map(l -> saleItems.get(l.itemId()).getProductId()).toList());

        SalesReturn ret = returns.save(new SalesReturn(numbers.next(DocumentType.SALES_RETURN), r.clientRequestId(), sale.getId(), customer.getId(),
                clock.now(), today, sale.getCurrencyCode(), sale.getExchangeRate(), r.reason().trim(), SecurityUtils.currentUserId().orElse(null)));
        PostingContext ctx = new PostingContext(StockSourceType.SALES_RETURN, ret.getId(), ret.getReturnNo(), today, sale.getInvoiceNo());
        BigDecimal total = Money.ZERO;
        BigDecimal cost = Money.ZERO;
        for (ReturnLine l : r.items()) {
            SaleItem item = saleItems.get(l.itemId());
            BigDecimal qty = l.quantity();
            if (qty.compareTo(item.getReturnableQuantity()) > 0) {
                throw new BusinessException(ErrorCode.RETURN_QUANTITY_EXCEEDED,
                        Map.of("product", item.getProductName(), "available", item.getReturnableQuantity().stripTrailingZeros().toPlainString()));
            }
            Product product = products.findById(item.getProductId()).orElseThrow(() -> new NotFoundException("Product", item.getProductId()));
            InventoryPostingService.checkWholeQuantity(product, qty);
            BigDecimal net = ReturnMath.netLine(item.getLineTotal(), item.getOverallDiscountShare());
            BigDecimal value = ReturnMath.value(net, item.getQuantity(), returns.returnedValue(item.getId()), item.getReturnableQuantity(), qty);
            String condition = l.condition() == null ? "SELLABLE" : l.condition();
            SalesReturnItem ri = new SalesReturnItem(ret, item.getId(), item.getProductId(), item.getProductName(), item.getUnitName(), qty,
                    ReturnMath.unitNet(net, item.getQuantity()), value, condition, item.getUnitCostBase(), blankToNull(l.reason()));
            ret.getItems().add(ri);
            item.addReturned(qty);
            total = total.add(value);
            cost = cost.add(Money.amount(qty.multiply(item.getUnitCostBase())));
        }
        returns.saveAndFlush(ret);
        for (SalesReturnItem ri : ret.getItems()) {
            if ("SELLABLE".equals(ri.getCondition())) {
                inventory.receive(MovementType.SALE_RETURN, ri.getProductId(), ri.getQuantity(), ri.getUnitCostBase(), ctx, ri.getId());
            }
        }

        // Settlement: first what is still owed, then a cash refund or customer credit.
        BigDecimal reduction = Money.min(total, sale.getBalanceDue());
        BigDecimal rest = total.subtract(reduction);
        BigDecimal refund = Money.ZERO;
        BigDecimal credit = Money.ZERO;
        Long accountId = null;
        if (rest.signum() > 0) {
            boolean doRefund = customer.isWalkIn() || "REFUND".equals(r.settlement());
            if (!doRefund && !"CREDIT".equals(r.settlement())) throw new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("field", "settlement"));
            if (doRefund) {
                if (r.refundCashAccountId() == null) throw new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("field", "refundCashAccountId"));
                accountId = cash.requireAccount(r.refundCashAccountId(), sale.getCurrencyCode()).getId();
                refund = rest;
                cash.post(accountId, CashDirection.OUT, refund, CashTxnType.SALE_REFUND, sale.getExchangeRate(),
                        new CashSource("SALES_RETURN", ret.getId(), ret.getReturnNo()), "Refund " + ret.getReturnNo() + " (" + sale.getInvoiceNo() + ")");
            } else {
                credit = rest;
            }
        }
        ret.settle(total, cost, reduction, refund, accountId, credit);
        sale.applyReturn(total, rest);
        ledger.credit(customer.getId(), sale.getCurrencyCode(), CustomerEntryType.SALES_RETURN, total, "SALES_RETURN", ret.getId(), ret.getReturnNo(),
                sale.getInvoiceNo());
        if (refund.signum() > 0) {
            ledger.debit(customer.getId(), sale.getCurrencyCode(), CustomerEntryType.CREDIT_REFUND, refund, "SALES_RETURN", ret.getId(), ret.getReturnNo(),
                    "Cash refund");
        }
        returns.saveAndFlush(ret);
        sales.saveAndFlush(sale);
        audit.log(AuditEvent.of(AuditAction.RETURN, AuditModule.RETURNS, "SalesReturn", ret.getId(), ret.getReturnNo(), "Sales return")
                .withDetails(Map.of("invoice", sale.getInvoiceNo(), "total", total.toPlainString(), "currency", sale.getCurrencyCode(), "refund",
                        refund.toPlainString(), "credit", credit.toPlainString(), "reason", r.reason())));
        return toResponse(ret);
    }

    @Transactional(readOnly = true)
    public ReturnResponse get(Long id) {
        return toResponse(returns.findById(id).orElseThrow(() -> new NotFoundException("SalesReturn", id)));
    }

    @Transactional(readOnly = true)
    public List<ReturnResponse> forSale(Long saleId) {
        return returns.findBySaleIdOrderByIdAsc(saleId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<ReturnResponse> search(String q, Long customerId, LocalDate from, LocalDate to, Pageable pageable) {
        Specification<SalesReturn> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (customerId != null) ps.add(cb.equal(root.get("customerId"), customerId));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("businessDate"), from));
            if (to != null) ps.add(cb.lessThanOrEqualTo(root.get("businessDate"), to));
            if (q != null && !q.isBlank()) ps.add(cb.like(cb.lower(root.get("returnNo")), "%" + q.trim().toLowerCase() + "%"));
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return returns.findAll(spec, pageable).map(this::toResponse);
    }

    private ReturnResponse toResponse(SalesReturn r) {
        Sale sale = sales.findById(r.getSaleId()).orElseThrow();
        Customer c = customers.get(r.getCustomerId());
        boolean showCost = SecurityUtils.currentPrincipal().map(p -> p.has(Permission.VIEW_COST_PRICES)).orElse(false);
        String account = r.getRefundCashAccountId() == null ? null : cashAccounts.findById(r.getRefundCashAccountId()).map(CashAccount::getName).orElse(null);
        String by = users.names(r.getCreatedBy() == null ? List.of() : List.of(r.getCreatedBy())).get(r.getCreatedBy());
        List<ReturnResponse.Item> items = r.getItems().stream().map(i -> new ReturnResponse.Item(i.getId(), i.getSaleItemId(), i.getProductId(),
                i.getProductName(), i.getUnitName(), i.getQuantity(), i.getUnitNetPrice(), i.getLineTotal(), i.getCondition(), i.getReason())).toList();
        return new ReturnResponse(r.getId(), r.getReturnNo(), "SALES", sale.getId(), sale.getInvoiceNo(), c.getId(), c.getName(), r.getReturnDate(),
                r.getBusinessDate(), r.getCurrencyCode(), r.getExchangeRate(), r.getTotalAmount(), showCost ? r.getTotalCostBase() : null,
                r.getReceivableReduction(), r.getCashRefundAmount(), r.getRefundCashAccountId(), account, r.getCreditAmount(), r.getReason(), by, items);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
