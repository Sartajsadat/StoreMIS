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
import java.util.UUID;

/** A posted stock adjustment (immutable). */
@Entity
@Table(name = "stock_adjustments")
public class StockAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "adjustment_no", nullable = false, unique = true, updatable = false)
    private String adjustmentNo;

    @Column(name = "client_request_id", unique = true, updatable = false)
    private UUID clientRequestId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, updatable = false)
    private AdjustmentType adjustmentType;

    @Column(name = "quantity", nullable = false, updatable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_cost_base", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal unitCostBase;

    @Column(name = "reason", nullable = false, updatable = false)
    private String reason;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    protected StockAdjustment() {}

    public StockAdjustment(String adjustmentNo, UUID clientRequestId, Long productId, AdjustmentType adjustmentType, BigDecimal quantity,
            BigDecimal unitCostBase, String reason, LocalDate businessDate, OffsetDateTime createdAt, Long createdBy) {
        this.adjustmentNo = adjustmentNo;
        this.clientRequestId = clientRequestId;
        this.productId = productId;
        this.adjustmentType = adjustmentType;
        this.quantity = quantity;
        this.unitCostBase = unitCostBase;
        this.reason = reason;
        this.businessDate = businessDate;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public String getAdjustmentNo() {
        return adjustmentNo;
    }

    public UUID getClientRequestId() {
        return clientRequestId;
    }

    public Long getProductId() {
        return productId;
    }

    public AdjustmentType getAdjustmentType() {
        return adjustmentType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitCostBase() {
        return unitCostBase;
    }

    public String getReason() {
        return reason;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }
}
