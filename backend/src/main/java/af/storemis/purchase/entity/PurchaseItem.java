package af.storemis.purchase.entity;

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
@Table(name = "purchase_items")
public class PurchaseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false)
    private Purchase purchase;

    @Column(name = "line_no", nullable = false)
    private short lineNo;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "unit_name", nullable = false)
    private String unitName;

    @Column(name = "quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "overall_discount_share", nullable = false, precision = 19, scale = 2)
    private BigDecimal overallDiscountShare;

    @Column(name = "landed_unit_cost_base", nullable = false, precision = 19, scale = 4)
    private BigDecimal landedUnitCostBase;

    @Column(name = "returned_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal returnedQuantity = BigDecimal.ZERO;

    protected PurchaseItem() {}

    public PurchaseItem(Purchase purchase, short lineNo, Long productId, String productName, String unitName, BigDecimal quantity, BigDecimal unitCost,
            BigDecimal discountAmount) {
        this.purchase = purchase;
        this.lineNo = lineNo;
        this.productId = productId;
        this.productName = productName;
        this.unitName = unitName;
        this.quantity = quantity;
        this.unitCost = unitCost;
        this.discountAmount = discountAmount;
    }

    public void applyTotals(BigDecimal lineTotal, BigDecimal overallShare, BigDecimal landedUnitCostBase) {
        this.lineTotal = lineTotal;
        this.overallDiscountShare = overallShare;
        this.landedUnitCostBase = landedUnitCostBase;
    }

    public Long getId() {
        return id;
    }

    public short getLineNo() {
        return lineNo;
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

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public BigDecimal getOverallDiscountShare() {
        return overallDiscountShare;
    }

    public BigDecimal getLandedUnitCostBase() {
        return landedUnitCostBase;
    }

    public BigDecimal getReturnedQuantity() {
        return returnedQuantity;
    }

    /** What can still be returned. */
    public BigDecimal getReturnableQuantity() {
        return getQuantity().subtract(returnedQuantity);
    }

    public void addReturned(BigDecimal quantity) {
        this.returnedQuantity = this.returnedQuantity.add(quantity);
    }
}
