package af.storemis.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** One line of the append-only stock ledger. Never updated or deleted; mistakes are corrected with new movements. */
@Entity
@Table(name = "stock_movements")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, updatable = false)
    private MovementType movementType;

    @Column(name = "quantity", nullable = false, updatable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_cost_base", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal unitCostBase;

    @Column(name = "total_cost_base", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal totalCostBase;

    @Column(name = "quantity_after", nullable = false, updatable = false, precision = 14, scale = 3)
    private BigDecimal quantityAfter;

    @Column(name = "avg_cost_after", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal avgCostAfter;

    @Column(name = "value_after", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal valueAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, updatable = false)
    private StockSourceType sourceType;

    @Column(name = "source_id", nullable = false, updatable = false)
    private Long sourceId;

    @Column(name = "source_item_id", updatable = false)
    private Long sourceItemId;

    @Column(name = "reference_no", nullable = false, updatable = false)
    private String referenceNo;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(name = "notes", updatable = false)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    protected StockMovement() {}

    public StockMovement(Long productId, MovementType movementType, BigDecimal quantity, BigDecimal unitCostBase, BigDecimal totalCostBase,
            BigDecimal quantityAfter, BigDecimal avgCostAfter, BigDecimal valueAfter, StockSourceType sourceType, Long sourceId, Long sourceItemId,
            String referenceNo, LocalDate businessDate, String notes, OffsetDateTime createdAt, Long createdBy) {
        this.productId = productId;
        this.movementType = movementType;
        this.quantity = quantity;
        this.unitCostBase = unitCostBase;
        this.totalCostBase = totalCostBase;
        this.quantityAfter = quantityAfter;
        this.avgCostAfter = avgCostAfter;
        this.valueAfter = valueAfter;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.sourceItemId = sourceItemId;
        this.referenceNo = referenceNo;
        this.businessDate = businessDate;
        this.notes = notes;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public MovementType getMovementType() {
        return movementType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitCostBase() {
        return unitCostBase;
    }

    public BigDecimal getTotalCostBase() {
        return totalCostBase;
    }

    public BigDecimal getQuantityAfter() {
        return quantityAfter;
    }

    public BigDecimal getAvgCostAfter() {
        return avgCostAfter;
    }

    public BigDecimal getValueAfter() {
        return valueAfter;
    }

    public StockSourceType getSourceType() {
        return sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public Long getSourceItemId() {
        return sourceItemId;
    }

    public String getReferenceNo() {
        return referenceNo;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public String getNotes() {
        return notes;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }
}
