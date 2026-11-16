package af.storemis.inventory.service;

import af.storemis.common.security.Permission;
import af.storemis.common.security.SecurityUtils;
import af.storemis.common.time.BusinessClock;
import af.storemis.inventory.dto.InventorySummary;
import af.storemis.inventory.dto.IntegrityReport;
import af.storemis.inventory.dto.MovementResponse;
import af.storemis.inventory.dto.StockAlerts;
import af.storemis.inventory.dto.StockInfo;
import af.storemis.inventory.dto.StockStatus;
import af.storemis.inventory.entity.MovementType;
import af.storemis.inventory.entity.ProductStock;
import af.storemis.inventory.entity.StockMovement;
import af.storemis.inventory.repository.ProductStockRepository;
import af.storemis.inventory.repository.StockMovementRepository;
import af.storemis.inventory.repository.StockQueryRepository;
import af.storemis.product.dto.ProductResponse;
import af.storemis.product.entity.Product;
import af.storemis.product.repository.ProductRepository;
import af.storemis.settings.service.SettingsService;
import af.storemis.user.service.UserDirectory;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InventoryQueryService {

    private final StockQueryRepository stockQueries;
    private final ProductStockRepository stockRepository;
    private final StockMovementRepository movements;
    private final ProductRepository products;
    private final UserDirectory userDirectory;
    private final SettingsService settings;
    private final BusinessClock clock;

    public InventoryQueryService(StockQueryRepository stockQueries, ProductStockRepository stockRepository, StockMovementRepository movements,
            ProductRepository products, UserDirectory userDirectory, SettingsService settings, BusinessClock clock) {
        this.stockQueries = stockQueries;
        this.stockRepository = stockRepository;
        this.movements = movements;
        this.products = products;
        this.userDirectory = userDirectory;
        this.settings = settings;
        this.clock = clock;
    }

    public static boolean canSeeCost() {
        return SecurityUtils.currentPrincipal().map(p -> p.has(Permission.VIEW_COST_PRICES)).orElse(false);
    }

    /** Products with their stock, filtered by text, category, active flag and stock status. */
    public Page<ProductResponse> searchProducts(String q, Long categoryId, Boolean active, StockStatus status, Pageable pageable) {
        String pattern = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
        boolean showCost = canSeeCost();
        return stockQueries.search(pattern, categoryId, active, status == null ? null : status.name(), pageable)
                .map(row -> ProductResponse.from((Product) row[0], StockInfo.of((ProductStock) row[1], ((Product) row[0]).getMinStockLevel(), showCost)));
    }

    public ProductResponse describe(Product p) {
        ProductStock s = stockRepository.findById(p.getId()).orElse(null);
        return ProductResponse.from(p, StockInfo.of(s, p.getMinStockLevel(), canSeeCost()));
    }

    public List<ProductResponse> describeAll(List<Product> list) {
        Map<Long, ProductStock> stock = stockRepository.findByProductIdIn(list.stream().map(Product::getId).toList()).stream()
                .collect(Collectors.toMap(ProductStock::getProductId, Function.identity()));
        boolean showCost = canSeeCost();
        return list.stream().map(p -> ProductResponse.from(p, StockInfo.of(stock.get(p.getId()), p.getMinStockLevel(), showCost))).toList();
    }

    public InventorySummary summary() {
        boolean showCost = canSeeCost();
        return new InventorySummary(stockQueries.countActive(), stockQueries.countLow(), stockQueries.countOut(),
                showCost ? stockQueries.totalValue() : null, settings.getEntity().getBaseCurrencyCode());
    }

    public StockAlerts alerts(int limit) {
        List<StockAlerts.Item> items = stockQueries.alerts(PageRequest.of(0, Math.min(Math.max(limit, 1), 50))).stream().map(row -> {
            Product p = (Product) row[0];
            ProductStock s = (ProductStock) row[1];
            return new StockAlerts.Item(p.getId(), p.getName(), p.getCode(), s.getQuantityOnHand(), p.getMinStockLevel(), p.getUnit().getCode(),
                    p.getUnit().getName(), StockStatus.of(s.getQuantityOnHand(), p.getMinStockLevel()));
        }).toList();
        return new StockAlerts(stockQueries.countLow(), stockQueries.countOut(), items);
    }

    public Page<MovementResponse> movements(Long productId, MovementType type, LocalDate from, LocalDate to, String q, Pageable pageable) {
        Specification<StockMovement> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (productId != null) ps.add(cb.equal(root.get("productId"), productId));
            if (type != null) ps.add(cb.equal(root.get("movementType"), type));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("businessDate"), from));
            if (to != null) ps.add(cb.lessThanOrEqualTo(root.get("businessDate"), to));
            if (q != null && !q.isBlank()) ps.add(cb.like(cb.lower(root.get("referenceNo")), "%" + q.trim().toLowerCase() + "%"));
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Page<StockMovement> page = movements.findAll(spec, pageable);
        Map<Long, Product> productMap = productMap(page.getContent().stream().map(StockMovement::getProductId).toList());
        Map<Long, String> userNames = userDirectory.names(page.getContent().stream().map(StockMovement::getCreatedBy).toList());
        boolean showCost = canSeeCost();
        return page.map(m -> {
            Product p = productMap.get(m.getProductId());
            return new MovementResponse(m.getId(), m.getProductId(), p.getName(), p.getCode(), p.getUnit().getCode(), p.getUnit().getName(),
                    m.getMovementType(), m.getQuantity(), showCost ? m.getUnitCostBase() : null, showCost ? m.getTotalCostBase() : null,
                    m.getQuantityAfter(), showCost ? m.getAvgCostAfter() : null, showCost ? m.getValueAfter() : null, m.getSourceType(), m.getSourceId(),
                    m.getReferenceNo(), m.getBusinessDate(), m.getNotes(), m.getCreatedAt(), userNames.get(m.getCreatedBy()));
        });
    }

    public IntegrityReport integrityCheck() {
        List<IntegrityReport.Mismatch> mismatches = stockRepository.findLedgerMismatches().stream()
                .map(r -> new IntegrityReport.Mismatch(((Number) r[0]).longValue(), (BigDecimal) r[1], (BigDecimal) r[2])).toList();
        return new IntegrityReport(mismatches.isEmpty(), mismatches);
    }

    Map<Long, Product> productMap(Collection<Long> ids) {
        return products.findAllById(ids.stream().distinct().toList()).stream().collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    public LocalDate today() {
        return clock.today();
    }
}
