package af.storemis.returns.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "purchase_return_items")
public class PurchaseReturnItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_return_id", nullable = false, updatable = false)
    private PurchaseReturn parent;

    @Column(name = "purchase_item_id", nullable = false, updatable = false)
    private Long purchaseItemId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, updatable = false)
    private String productName;

    @Column(name = "unit_name", updatable = false)
    private String unitName;

    @Column(name = "quantity", nullable = false, updatable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_net_price", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal unitNetPrice;

    @Column(name = "line_total", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "unit_cost_base", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal unitCostBase;

    @Column(name = "reason", updatable = false)
    private String reason;

    protected PurchaseReturnItem() {}

    public PurchaseReturnItem(PurchaseReturn parent, Long purchaseItemId, Long productId, String productName, String unitName, BigDecimal quantity, BigDecimal unitNetPrice,
            BigDecimal lineTotal, BigDecimal unitCostBase, String reason) {
        this.parent = parent;
        this.purchaseItemId = purchaseItemId;
        this.productId = productId;
        this.productName = productName;
        this.unitName = unitName;
        this.quantity = quantity;
        this.unitNetPrice = unitNetPrice;
        this.lineTotal = lineTotal;
        this.unitCostBase = unitCostBase;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public Long getPurchaseItemId() {
        return purchaseItemId;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public String getUnitName() {
        return unitName;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitNetPrice() {
        return unitNetPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public BigDecimal getUnitCostBase() {
        return unitCostBase;
    }

    public String getReason() {
        return reason;
    }
}
