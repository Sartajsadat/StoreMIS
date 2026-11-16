package af.storemis.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Cached stock balance and weighted average cost (base currency) of one product.
 * Only InventoryPostingService changes it, under a row lock, together with a stock_movements row.
 */
@Entity
@Table(name = "product_stock")
public class ProductStock {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "quantity_on_hand", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantityOnHand = BigDecimal.ZERO;

    @Column(name = "avg_unit_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal avgUnitCost = BigDecimal.ZERO;

    @Column(name = "inventory_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal inventoryValue = BigDecimal.ZERO;

    @Column(name = "last_movement_at")
    private OffsetDateTime lastMovementAt;

    protected ProductStock() {}

    public ProductStock(Long productId) {
        this.productId = productId;
    }

    /** Called only by InventoryPostingService while holding the row lock, together with a new ledger row. */
    public void applyPosting(BigDecimal quantity, BigDecimal avgCost, BigDecimal value, OffsetDateTime at) {
        this.quantityOnHand = quantity;
        this.avgUnitCost = avgCost;
        this.inventoryValue = value;
        this.lastMovementAt = at;
    }

    public Long getProductId() {
        return productId;
    }

    public BigDecimal getQuantityOnHand() {
        return quantityOnHand;
    }

    public BigDecimal getAvgUnitCost() {
        return avgUnitCost;
    }

    public BigDecimal getInventoryValue() {
        return inventoryValue;
    }

    public OffsetDateTime getLastMovementAt() {
        return lastMovementAt;
    }
}
