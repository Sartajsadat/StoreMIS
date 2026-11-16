package af.storemis.sales.service;

import af.storemis.audit.entity.AuditAction;
import af.storemis.audit.entity.AuditModule;
import af.storemis.audit.service.AuditEvent;
import af.storemis.audit.service.AuditService;
import af.storemis.cash.entity.CashAccount;
import af.storemis.cash.repository.CashAccountRepository;
import af.storemis.cash.service.CashPostingService;
import af.storemis.common.exception.BusinessException;
import af.storemis.common.exception.ErrorCode;
import af.storemis.common.exception.NotFoundException;
import af.storemis.common.money.Money;
import af.storemis.common.security.AppPrincipal;
import af.storemis.common.security.Permission;
import af.storemis.common.security.SecurityUtils;
import af.storemis.common.time.BusinessClock;
import af.storemis.currency.service.CurrencyService;
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
import af.storemis.payment.dto.PaymentLine;
import af.storemis.payment.entity.CustomerPaymentAllocation;
import af.storemis.payment.repository.CustomerPaymentAllocationRepository;
import af.storemis.payment.service.CustomerPaymentService;
import af.storemis.product.entity.Product;
import af.storemis.product.repository.ProductRepository;
import af.storemis.sales.dto.CancelSaleRequest;
import af.storemis.sales.dto.ConfirmDraftRequest;
import af.storemis.sales.dto.PriceHint;
import af.storemis.sales.dto.ReceiptResponse;
import af.storemis.sales.dto.SaleRequest;
import af.storemis.sales.dto.SaleResponse;
import af.storemis.sales.entity.Sale;
import af.storemis.sales.entity.SaleItem;
import af.storemis.sales.entity.SaleStatus;
import af.storemis.sales.repository.SaleRepository;
import af.storemis.settings.entity.BusinessSettings;
import af.storemis.settings.service.SettingsService;
import af.storemis.user.service.UserDirectory;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sales. Confirming a sale posts, in one transaction: stock out at weighted average cost (COGS captured per line),
 * the customer receivable for the full total, and every payment taken at the till (cash in, per currency).
 */
@Service
public class SalesService {

    private final SaleRepository sales;
    private final ProductRepository products;
    private final ProductStockRepository stock;
    private final CustomerService customers;
    private final CustomerLedgerService ledger;
    private final CustomerPaymentService payments;
    private final CustomerPaymentAllocationRepository allocations;
    private final CashAccountRepository cashAccounts;
    private final InventoryPostingService inventory;
    private final CurrencyService currency;
    private final SettingsService settings;
    private final DocumentNumberService numbers;
    private final UserDirectory users;
    private final AuditService audit;
    private final BusinessClock clock;
    private final CashPostingService dayGuard;

    public SalesService(SaleRepository sales, ProductRepository products, ProductStockRepository stock, CustomerService customers,
            CustomerLedgerService ledger, CustomerPaymentService payments, CustomerPaymentAllocationRepository allocations,
            CashAccountRepository cashAccounts, InventoryPostingService inventory, CurrencyService currency, SettingsService settings,
            DocumentNumberService numbers, UserDirectory users, AuditService audit, BusinessClock clock,
            CashPostingService dayGuard) {
        this.dayGuard = dayGuard;
        this.sales = sales;
        this.products = products;
        this.stock = stock;
        this.customers = customers;
        this.ledger = ledger;
        this.payments = payments;
        this.allocations = allocations;
        this.cashAccounts = cashAccounts;
        this.inventory = inventory;
        this.currency = currency;
        this.settings = settings;
        this.numbers = numbers;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- POS

    /** One-step POS sale. Retrying with the same clientRequestId returns the first result instead of selling twice. */
    @Transactional
    public SaleResponse confirmNew(SaleRequest r) {
        if (r.clientRequestId() != null) {
            Optional<Sale> existing = sales.findByClientRequestId(r.clientRequestId());
            if (existing.isPresent()) return toResponse(existing.get());
        }
        Sale sale = new Sale();
        fill(sale, r);
        sales.save(sale);
        post(sale, r.payments(), r.clientRequestId());
        return toResponse(sale);
    }

    @Transactional
    public SaleResponse createDraft(SaleRequest r) {
        Sale sale = new Sale();
        fill(sale, r);
        sales.save(sale);
        return toResponse(sale);
    }

    @Transactional
    public SaleResponse updateDraft(Long id, SaleRequest r) {
        Sale sale = lockOwnDraft(id);
        sale.checkVersion(r.version());
        fill(sale, r);
        sales.saveAndFlush(sale);
        return toResponse(sale);
    }

    @Transactional
    public void deleteDraft(Long id) {
        sales.delete(lockOwnDraft(id));
    }

    @Transactional(readOnly = true)
    public List<SaleResponse> myDrafts() {
        return sales.findDraftsOf(SecurityUtils.requirePrincipal().id()).stream().map(this::toResponse).toList();
    }

    @Transactional
    public SaleResponse confirmDraft(Long id, ConfirmDraftRequest r) {
        if (r.clientRequestId() != null) {
            Optional<Sale> existing = sales.findByClientRequestId(r.clientRequestId());
            if (existing.isPresent()) return toResponse(existing.get());
        }
        Sale sale = lockOwnDraft(id);
        // Re-validate against today's state (products may have been deactivated while the cart was held).
        validateProducts(sale.getItems().stream().map(SaleItem::getProductId).toList());
        customers.requireActive(sale.getCustomerId());
        post(sale, r.payments(), r.clientRequestId());
        return toResponse(sale);
    }

    /**
     * Cancels a confirmed sale (Admin, with reason). Goods return to stock at the cost they left with, the receivable is
     * removed, and payments are refunded from the same cash accounts or kept as customer credit.
     */
    @Transactional
    public SaleResponse cancel(Long id, CancelSaleRequest r) {
        Sale sale = sales.lock(id).orElseThrow(() -> new NotFoundException("Sale", id));
        if (sale.getStatus() != SaleStatus.CONFIRMED || sale.getReturnedAmount().signum() > 0) {
            throw new BusinessException(ErrorCode.INVALID_STATE, Map.of("status", sale.getStatus().name()));
        }
        Customer customer = customers.get(sale.getCustomerId());
        boolean refund = customer.isWalkIn() || "REFUND".equals(r.paymentHandling());
        inventory.lock(sale.getItems().stream().map(SaleItem::getProductId).toList());
        PostingContext ctx = new PostingContext(StockSourceType.SALE, sale.getId(), sale.getInvoiceNo(), clock.today(), r.reason());
        for (SaleItem item : sale.getItems()) {
            inventory.receive(MovementType.SALE_CANCEL, item.getProductId(), item.getQuantity(), item.getUnitCostBase(), ctx, item.getId());
        }
        ledger.credit(sale.getCustomerId(), sale.getCurrencyCode(), CustomerEntryType.SALE_CANCEL, sale.getGrandTotal().subtract(sale.getReturnedAmount()),
                "SALE", sale.getId(), sale.getInvoiceNo(), r.reason());
        payments.releaseForCancelledSale(sale, allocations.findBySaleId(sale.getId()), refund);
        sale.cancel(r.reason().trim(), clock.now(), SecurityUtils.currentUserId().orElse(null));
        sales.saveAndFlush(sale);
        audit.log(AuditEvent.of(AuditAction.CANCEL, AuditModule.SALES, "Sale", sale.getId(), sale.getInvoiceNo(), "Sale cancelled")
                .withDetails(Map.of("reason", r.reason(), "paymentHandling", refund ? "REFUND" : "CREDIT")));
        return toResponse(sale);
    }

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public SaleResponse get(Long id) {
        Sale sale = sales.findById(id).orElseThrow(() -> new NotFoundException("Sale", id));
        checkCanView(sale);
        return toResponse(sale);
    }

    @Transactional(readOnly = true)
    public ReceiptResponse receipt(Long id) {
        SaleResponse sale = get(id);
        BusinessSettings s = settings.getEntity();
        String logo = s.getLogoPath() == null ? null : "/api/v1/files/public/" + s.getLogoPath();
        return new ReceiptResponse(sale, new ReceiptResponse.Business(s.getBusinessName(), s.getAddress(), s.getPhone(), s.getEmail(), logo,
                s.isShowLogoOnReceipt(), s.getReceiptFormat(), s.getInvoiceHeaderNote(), s.getInvoiceFooterNote(), s.getBaseCurrencyCode()));
    }

    @Transactional(readOnly = true)
    public List<SaleResponse> openForCustomer(Long customerId) {
        return sales.findOpenForCustomer(customerId).stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> totalsForCustomer(Long customerId) {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        for (Object[] r : sales.totalsForCustomer(customerId)) m.put(((String) r[0]).trim(), (BigDecimal) r[1]);
        return m;
    }

    @Transactional(readOnly = true)
    public PriceHint priceHint(Long productId, Long customerId) {
        Optional<Object[]> last = sales.lastPrice(productId, null);
        Optional<Object[]> forCustomer = customerId == null ? Optional.empty() : sales.lastPrice(productId, customerId);
        boolean showCost = SecurityUtils.currentPrincipal().map(p -> p.has(Permission.VIEW_COST_PRICES)).orElse(false);
        BigDecimal avg = showCost ? stock.findById(productId).map(ProductStock::getAvgUnitCost).orElse(null) : null;
        return new PriceHint(last.map(o -> (BigDecimal) o[0]).orElse(null), last.map(o -> ((String) o[1]).trim()).orElse(null),
                last.map(o -> (java.time.OffsetDateTime) o[2]).orElse(null), forCustomer.map(o -> (BigDecimal) o[0]).orElse(null),
                forCustomer.map(o -> ((String) o[1]).trim()).orElse(null), forCustomer.map(o -> (java.time.OffsetDateTime) o[2]).orElse(null), avg,
                currency.baseCurrency());
    }

    /** Entry Users see only their own sales unless they hold VIEW_ALL_SALES. */
    @Transactional(readOnly = true)
    public Page<SaleResponse> search(String q, Long customerId, SaleStatus status, String paymentStatus, String saleType, String currencyCode, Long userId,
            LocalDate from, LocalDate to, Pageable pageable) {
        AppPrincipal me = SecurityUtils.requirePrincipal();
        Long onlyUser = me.has(Permission.VIEW_ALL_SALES) ? userId : me.id();
        Specification<Sale> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (onlyUser != null) ps.add(cb.equal(root.get("createdBy"), onlyUser));
            if (customerId != null) ps.add(cb.equal(root.get("customerId"), customerId));
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            else ps.add(cb.notEqual(root.get("status"), SaleStatus.DRAFT));
            if (paymentStatus != null && !paymentStatus.isBlank()) ps.add(cb.equal(root.get("paymentStatus"), paymentStatus));
            if (saleType != null && !saleType.isBlank()) ps.add(cb.equal(root.get("saleType"), saleType));
            if (currencyCode != null && !currencyCode.isBlank()) ps.add(cb.equal(root.get("currencyCode"), currencyCode.toUpperCase()));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("businessDate"), from));
            if (to != null) ps.add(cb.lessThanOrEqualTo(root.get("businessDate"), to));
            if (q != null && !q.isBlank()) ps.add(cb.like(cb.lower(cb.coalesce(root.get("invoiceNo"), "")), "%" + q.trim().toLowerCase() + "%"));
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return sales.findAll(spec, pageable).map(this::summary);
    }

    // ---------------------------------------------------------------- internals

    private void fill(Sale sale, SaleRequest r) {
        Customer customer = r.customerId() == null ? customers.walkIn() : customers.requireActive(r.customerId());
        String cur = currency.requireActive(r.currency()).getCode();
        BigDecimal rate = currency.resolveRate(cur, r.exchangeRate());
        Map<Long, Product> productMap = validateProducts(r.items().stream().map(SaleRequest.Item::productId).toList());
        boolean anyDiscount = Money.positive(r.overallDiscount()) || r.items().stream().anyMatch(i -> Money.positive(i.discount()));
        if (anyDiscount && !SecurityUtils.requirePrincipal().has(Permission.GIVE_DISCOUNT)) {
            throw new BusinessException(ErrorCode.DISCOUNT_NOT_ALLOWED);
        }
        List<SaleCalculator.LineInput> inputs = new ArrayList<>();
        for (SaleRequest.Item i : r.items()) {
            InventoryPostingService.checkWholeQuantity(productMap.get(i.productId()), i.quantity());
            inputs.add(new SaleCalculator.LineInput(i.quantity(), i.unitPrice(), Money.nz(i.discount())));
        }
        SaleCalculator.Result calc = SaleCalculator.calculate(inputs, Money.nz(r.overallDiscount()));
        String type = r.saleType() != null ? r.saleType() : customer.getCustomerType();
        sale.setHeader(customer.getId(), type, cur, rate, blankToNull(r.notes()), clock.now());
        sale.getItems().clear();
        sales.flush();
        for (int idx = 0; idx < r.items().size(); idx++) {
            SaleRequest.Item in = r.items().get(idx);
            Product p = productMap.get(in.productId());
            SaleCalculator.LineResult lr = calc.lines().get(idx);
            sale.getItems().add(new SaleItem(sale, (short) (idx + 1), p.getId(), p.getName(), p.getCode(), p.getUnit().getName(), in.quantity(),
                    Money.amount(in.unitPrice()), lr.discount(), lr.lineTotal(), lr.overallShare()));
        }
        sale.setTotals(calc.subtotal(), calc.itemDiscountTotal(), calc.overallDiscount(), calc.grandTotal());
    }

    private Map<Long, Product> validateProducts(List<Long> ids) {
        Map<Long, Product> map = products.findAllById(ids).stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        for (Long id : ids) {
            Product p = map.get(id);
            if (p == null) throw new NotFoundException("Product", id);
            if (!p.isActive()) throw new BusinessException(ErrorCode.PRODUCT_INACTIVE, Map.of("product", p.getName()));
        }
        return map;
    }

    private void post(Sale sale, List<PaymentLine> paymentLines, UUID clientRequestId) {
        dayGuard.ensureDayOpen(clock.today());
        Customer customer = customers.get(sale.getCustomerId());
        String number = numbers.next(DocumentType.SALE);
        boolean blockBelowCost = settings.getEntity().isBlockSaleBelowCost();

        inventory.lock(sale.getItems().stream().map(SaleItem::getProductId).toList());
        PostingContext ctx = new PostingContext(StockSourceType.SALE, sale.getId(), number, clock.today(), customer.getName());
        BigDecimal totalCost = BigDecimal.ZERO;
        for (SaleItem item : sale.getItems()) {
            InventoryPostingService.Posted posted = inventory.issue(MovementType.SALE, item.getProductId(), item.getQuantity(), ctx, item.getId());
            item.applyCost(posted.unitCost(), posted.totalCost());
            totalCost = totalCost.add(posted.totalCost());
            if (blockBelowCost) {
                BigDecimal netUnitBase = item.getLineTotal().subtract(item.getOverallDiscountShare()).multiply(sale.getExchangeRate())
                        .divide(item.getQuantity(), 4, RoundingMode.HALF_UP);
                if (netUnitBase.compareTo(posted.unitCost()) < 0) {
                    throw new BusinessException(ErrorCode.SALE_BELOW_COST, Map.of("product", item.getProductName()));
                }
            }
        }
        sale.confirm(number, clientRequestId, clock.today(), clock.now(), totalCost);
        ledger.debit(customer.getId(), sale.getCurrencyCode(), CustomerEntryType.SALE, sale.getGrandTotal(), "SALE", sale.getId(), number, null);

        if (paymentLines != null) {
            for (PaymentLine line : paymentLines) payments.payAtSale(sale, line, customer.isWalkIn());
        }
        if (customer.isWalkIn() && sale.getBalanceDue().signum() > 0) {
            throw new BusinessException(ErrorCode.WALK_IN_MUST_PAY, Map.of("due", sale.getBalanceDue().toPlainString(), "currency", sale.getCurrencyCode()));
        }
        sales.saveAndFlush(sale);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("customer", customer.getName());
        details.put("total", sale.getGrandTotal().toPlainString() + " " + sale.getCurrencyCode());
        details.put("paid", sale.getAmountPaid().toPlainString());
        details.put("items", sale.getItems().size());
        audit.log(AuditEvent.of(AuditAction.CONFIRM, AuditModule.SALES, "Sale", sale.getId(), number, "Sale confirmed").withDetails(details));
    }

    private Sale lockOwnDraft(Long id) {
        Sale sale = sales.lock(id).orElseThrow(() -> new NotFoundException("Sale", id));
        if (sale.getStatus() != SaleStatus.DRAFT) throw new BusinessException(ErrorCode.INVALID_STATE, Map.of("status", sale.getStatus().name()));
        AppPrincipal me = SecurityUtils.requirePrincipal();
        if (!me.isAdmin() && !Objects.equals(sale.getCreatedBy(), me.id())) throw new AccessDeniedException("Not your held sale");
        return sale;
    }

    private void checkCanView(Sale sale) {
        AppPrincipal me = SecurityUtils.requirePrincipal();
        if (me.has(Permission.VIEW_ALL_SALES) || me.has(Permission.RECEIVE_CUSTOMER_PAYMENTS) || Objects.equals(sale.getCreatedBy(), me.id())) return;
        throw new AccessDeniedException("Not your sale");
    }

    private SaleResponse summary(Sale s) {
        return build(s, false);
    }

    private SaleResponse toResponse(Sale s) {
        return build(s, true);
    }

    private SaleResponse build(Sale s, boolean full) {
        Customer c = customers.get(s.getCustomerId());
        boolean showCost = SecurityUtils.currentPrincipal().map(p -> p.has(Permission.VIEW_COST_PRICES)).orElse(false);
        String by = users.names(List.of(s.getCreatedBy() == null ? -1L : s.getCreatedBy())).get(s.getCreatedBy());
        List<SaleResponse.Item> items = !full ? List.of() : s.getItems().stream().map(i -> new SaleResponse.Item(i.getId(), i.getLineNo(), i.getProductId(),
                i.getProductName(), i.getProductCode(), i.getUnitName(), i.getQuantity(), i.getUnitPrice(), i.getDiscountAmount(), i.getLineTotal(),
                showCost ? i.getUnitCostBase() : null, i.getReturnedQuantity())).toList();
        List<SaleResponse.Payment> pays = List.of();
        if (full && s.getId() != null) {
            List<CustomerPaymentAllocation> allocs = allocations.findBySaleId(s.getId());
            Map<Long, String> accountNames = cashAccounts.findAllById(allocs.stream().map(a -> a.getPayment().getCashAccountId()).distinct().toList())
                    .stream().collect(Collectors.toMap(CashAccount::getId, CashAccount::getName));
            pays = allocs.stream().map(a -> new SaleResponse.Payment(a.getPayment().getId(), a.getPayment().getPaymentNo(), a.getPayment().getPaymentKind(),
                    a.getPayment().getPaymentDate(), a.getPayment().getCurrencyCode(), a.getPaidAmount(), a.getAppliedAmount(), a.getPayment().getExchangeRate(),
                    accountNames.get(a.getPayment().getCashAccountId()), a.getPayment().getStatus().name(), a.isReleased())).toList();
        }
        return new SaleResponse(s.getId(), s.getInvoiceNo(), c.getId(), c.getName(), c.getPhone(), c.isWalkIn(), s.getSaleType(), s.getSaleDate(),
                s.getBusinessDate(), s.getCurrencyCode(), s.getExchangeRate(), s.getSubtotal(), s.getItemDiscountTotal(), s.getOverallDiscount(),
                s.getGrandTotal(), s.getAmountPaid(), s.getReturnedAmount(), s.getBalanceDue(), showCost ? s.getTotalCostBase() : null, s.getStatus(),
                s.getPaymentStatus(), s.getNotes(), s.getCancelReason(), s.getCreatedBy(), by, s.getVersion(), items, pays);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
