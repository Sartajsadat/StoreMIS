package af.storemis.inventory.service;

import af.storemis.common.exception.BusinessException;
import af.storemis.common.exception.ErrorCode;
import af.storemis.common.exception.NotFoundException;
import af.storemis.common.security.SecurityUtils;
import af.storemis.common.time.BusinessClock;
import af.storemis.inventory.entity.MovementType;
import af.storemis.inventory.entity.ProductStock;
import af.storemis.inventory.entity.StockMovement;
import af.storemis.inventory.repository.ProductStockRepository;
import af.storemis.inventory.repository.StockMovementRepository;
import af.storemis.inventory.service.StockMath.Balance;
import af.storemis.product.entity.Product;
import af.storemis.product.repository.ProductRepository;
import af.storemis.settings.service.SettingsService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only code that changes stock. Every call locks the product's stock row, writes one ledger row and updates the
 * cached balance — all inside the caller's transaction (sale, purchase, return, adjustment), so stock and documents
 * always commit or roll back together.
 *
 * <p>Callers that touch several products should call {@link #lock(Collection)} first with all product ids, which takes
 * the locks in ascending order and so prevents deadlocks between concurrent documents.
 */
@Service
public class InventoryPostingService {

    /** Result of a posting: the cost applied and the balance afterwards. */
    public record Posted(StockMovement movement, BigDecimal unitCost, BigDecimal totalCost) {}

    private final ProductStockRepository stockRepository;
    private final StockMovementRepository movements;
    private final ProductRepository products;
    private final SettingsService settings;
    private final BusinessClock clock;

    public InventoryPostingService(ProductStockRepository stockRepository, StockMovementRepository movements, ProductRepository products,
            SettingsService settings, BusinessClock clock) {
        this.stockRepository = stockRepository;
        this.movements = movements;
        this.products = products;
        this.settings = settings;
        this.clock = clock;
    }

    /** Locks the stock rows of all given products (ascending id order). Returns them by product id. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<Long, ProductStock> lock(Collection<Long> productIds) {
        Map<Long, ProductStock> locked = new LinkedHashMap<>();
        for (ProductStock s : stockRepository.lockAll(new TreeSet<>(productIds))) locked.put(s.getProductId(), s);
        return locked;
    }

    /** Creates the empty stock row for a new product. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void initialize(Long productId) {
        stockRepository.save(new ProductStock(productId));
    }

    /** Goods in at a known unit cost (opening stock, purchases, sale returns, positive adjustments). */
    @Transactional(propagation = Propagation.MANDATORY)
    public Posted receive(MovementType type, Long productId, BigDecimal quantity, BigDecimal unitCost, PostingContext ctx, Long sourceItemId) {
        requirePositive(quantity);
        if (unitCost == null || unitCost.signum() < 0) throw new BusinessException(ErrorCode.UNIT_COST_REQUIRED);
        Product product = product(productId);
        checkWholeQuantity(product, quantity);
        ProductStock stock = lockOne(productId);
        Balance after = StockMath.receive(balance(stock), quantity, unitCost);
        BigDecimal cost = StockMath.money(unitCost);
        return write(type, stock, StockMath.qty(quantity), cost, StockMath.money(quantity.multiply(unitCost)), after, ctx, sourceItemId);
    }

    /** Goods out at the current weighted average cost (sales, decreases, damage). Returns the cost applied (for COGS). */
    @Transactional(propagation = Propagation.MANDATORY)
    public Posted issue(MovementType type, Long productId, BigDecimal quantity, PostingContext ctx, Long sourceItemId) {
        requirePositive(quantity);
        Product product = product(productId);
        checkWholeQuantity(product, quantity);
        ProductStock stock = lockOne(productId);
        ensureAvailable(product, stock, quantity, settings.getEntity().isAllowNegativeStock());
        Balance after = StockMath.issue(balance(stock), quantity);
        BigDecimal cost = stock.getAvgUnitCost();
        return write(type, stock, StockMath.qty(quantity).negate(), cost, StockMath.money(quantity.multiply(cost)).negate(), after, ctx, sourceItemId);
    }

    /** Goods out at a specific historical cost (purchase returns and cancellations). Never allowed below zero. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Posted removeAtCost(MovementType type, Long productId, BigDecimal quantity, BigDecimal unitCost, PostingContext ctx, Long sourceItemId) {
        requirePositive(quantity);
        Product product = product(productId);
        checkWholeQuantity(product, quantity);
        ProductStock stock = lockOne(productId);
        ensureAvailable(product, stock, quantity, false);
        Balance after = StockMath.removeAtCost(balance(stock), quantity, unitCost);
        BigDecimal cost = StockMath.money(unitCost);
        return write(type, stock, StockMath.qty(quantity).negate(), cost, StockMath.money(quantity.multiply(unitCost)).negate(), after, ctx, sourceItemId);
    }

    private Posted write(MovementType type, ProductStock stock, BigDecimal signedQty, BigDecimal unitCost, BigDecimal signedTotal, Balance after,
            PostingContext ctx, Long sourceItemId) {
        OffsetDateTime now = clock.now();
        StockMovement m = movements.save(new StockMovement(stock.getProductId(), type, signedQty, unitCost, signedTotal, after.quantity(), after.avgCost(),
                after.value(), ctx.sourceType(), ctx.sourceId(), sourceItemId, ctx.referenceNo(), ctx.businessDate(), ctx.notes(), now,
                SecurityUtils.currentUserId().orElse(null)));
        stock.applyPosting(after.quantity(), after.avgCost(), after.value(), now);
        return new Posted(m, unitCost, signedTotal.abs());
    }

    private ProductStock lockOne(Long productId) {
        return stockRepository.lockAll(java.util.List.of(productId)).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("ProductStock", productId));
    }

    private Product product(Long productId) {
        return products.findById(productId).orElseThrow(() -> new NotFoundException("Product", productId));
    }

    private static Balance balance(ProductStock s) {
        return new Balance(s.getQuantityOnHand(), s.getAvgUnitCost(), s.getInventoryValue());
    }

    private static void ensureAvailable(Product product, ProductStock stock, BigDecimal quantity, boolean allowNegative) {
        if (!allowNegative && stock.getQuantityOnHand().compareTo(quantity) < 0) {
            throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT,
                    Map.of("product", product.getName(), "available", stock.getQuantityOnHand().stripTrailingZeros().toPlainString(), "productId", product.getId()));
        }
    }

    private static void requirePositive(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) throw new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("field", "quantity"));
    }

    /** Pieces, boxes, sets… cannot be split; metres, kilograms and litres can. */
    public static void checkWholeQuantity(Product product, BigDecimal quantity) {
        if (!product.getUnit().isAllowDecimal() && quantity.stripTrailingZeros().scale() > 0) {
            throw new BusinessException(ErrorCode.QUANTITY_NOT_INTEGER, Map.of("product", product.getName()));
        }
    }
}
