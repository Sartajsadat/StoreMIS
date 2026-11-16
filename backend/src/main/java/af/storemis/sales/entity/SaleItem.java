package af.storemis.sales.entity;

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

/** A sale line. Name, code, unit, price and cost are copied at confirmation and never change afterwards. */
@Entity
@Table(name = "sale_items")
public class SaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @Column(name = "line_no", nullable = false)
    private short lineNo;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "product_code")
    private String productCode;

    @Column(name = "unit_name", nullable = false)
    private String unitName;

    @Column(name = "quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "overall_discount_share", nullable = false, precision = 19, scale = 2)
    private BigDecimal overallDiscountShare;

    @Column(name = "unit_cost_base", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCostBase = BigDecimal.ZERO;

    @Column(name = "line_cost_base", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineCostBase = BigDecimal.ZERO;

    @Column(name = "returned_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal returnedQuantity = BigDecimal.ZERO;

    protected SaleItem() {}

    public SaleItem(Sale sale, short lineNo, Long productId, String productName, String productCode, String unitName, BigDecimal quantity,
            BigDecimal unitPrice, BigDecimal discountAmount, BigDecimal lineTotal, BigDecimal overallDiscountShare) {
        this.sale = sale;
        this.lineNo = lineNo;
        this.productId = productId;
        this.productName = productName;
        this.productCode = productCode;
        this.unitName = unitName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.discountAmount = discountAmount;
        this.lineTotal = lineTotal;
        this.overallDiscountShare = overallDiscountShare;
    }

    /** Cost of goods sold, captured from the weighted average at confirmation. */
    public void applyCost(BigDecimal unitCostBase, BigDecimal lineCostBase) {
        this.unitCostBase = unitCostBase;
        this.lineCostBase = lineCostBase;
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

    public String getProductCode() {
        return productCode;
    }

    public String getUnitName() {
        return unitName;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
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

    public BigDecimal getUnitCostBase() {
        return unitCostBase;
    }

    public BigDecimal getLineCostBase() {
        return lineCostBase;
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
