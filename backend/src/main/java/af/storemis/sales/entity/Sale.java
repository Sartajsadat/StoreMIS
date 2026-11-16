package af.storemis.sales.entity;

import af.storemis.common.entity.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A sale / invoice. DRAFT = a held cart. Once CONFIRMED it never changes except through payments, returns and
 * cancellation, which are separate records.
 */
@Entity
@Table(name = "sales")
public class Sale extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_no")
    private String invoiceNo;

    @Column(name = "client_request_id")
    private UUID clientRequestId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "sale_type", nullable = false)
    private String saleType;

    @Column(name = "sale_date", nullable = false)
    private OffsetDateTime saleDate;

    @Column(name = "business_date")
    private LocalDate businessDate;

    @Column(name = "currency_code", nullable = false, columnDefinition = "bpchar(3)")
    private String currencyCode;

    @Column(name = "exchange_rate", nullable = false, precision = 19, scale = 6)
    private BigDecimal exchangeRate;

    @Column(name = "subtotal", nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "item_discount_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal itemDiscountTotal = BigDecimal.ZERO;

    @Column(name = "overall_discount", nullable = false, precision = 19, scale = 2)
    private BigDecimal overallDiscount = BigDecimal.ZERO;

    @Column(name = "grand_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @Column(name = "amount_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "returned_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal returnedAmount = BigDecimal.ZERO;

    @Column(name = "return_settled_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal returnSettledAmount = BigDecimal.ZERO;

    @Column(name = "balance_due", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceDue = BigDecimal.ZERO;

    @Column(name = "total_cost_base", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCostBase = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SaleStatus status = SaleStatus.DRAFT;

    @Column(name = "payment_status", nullable = false)
    private String paymentStatus = "UNPAID";

    @Column(name = "notes")
    private String notes;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<SaleItem> items = new ArrayList<>();

    public void setHeader(Long customerId, String saleType, String currencyCode, BigDecimal exchangeRate, String notes, OffsetDateTime saleDate) {
        this.customerId = customerId;
        this.saleType = saleType;
        this.currencyCode = currencyCode;
        this.exchangeRate = exchangeRate;
        this.notes = notes;
        this.saleDate = saleDate;
    }

    public void setTotals(BigDecimal subtotal, BigDecimal itemDiscountTotal, BigDecimal overallDiscount, BigDecimal grandTotal) {
        this.subtotal = subtotal;
        this.itemDiscountTotal = itemDiscountTotal;
        this.overallDiscount = overallDiscount;
        this.grandTotal = grandTotal;
        refreshBalance();
    }

    public void confirm(String invoiceNo, UUID clientRequestId, LocalDate businessDate, OffsetDateTime at, BigDecimal totalCostBase) {
        this.invoiceNo = invoiceNo;
        this.clientRequestId = clientRequestId;
        this.businessDate = businessDate;
        this.saleDate = at;
        this.totalCostBase = totalCostBase;
        this.status = SaleStatus.CONFIRMED;
        refreshBalance();
    }

    public void cancel(String reason, OffsetDateTime at, Long by) {
        this.status = SaleStatus.CANCELLED;
        this.cancelReason = reason;
        this.cancelledAt = at;
        this.cancelledBy = by;
        this.balanceDue = BigDecimal.ZERO;
    }

    public void addPayment(BigDecimal applied) {
        this.amountPaid = this.amountPaid.add(applied);
        refreshBalance();
    }

    public void removePayment(BigDecimal applied) {
        this.amountPaid = this.amountPaid.subtract(applied);
        refreshBalance();
    }

    /**
     * Goods came back worth {@code total}; {@code settled} of it was paid back (cash refund or credit) instead of
     * reducing what is still owed.
     */
    public void applyReturn(BigDecimal total, BigDecimal settled) {
        this.returnedAmount = this.returnedAmount.add(total);
        this.returnSettledAmount = this.returnSettledAmount.add(settled);
        refreshBalance();
    }

    public BigDecimal getReturnSettledAmount() {
        return returnSettledAmount;
    }

    private void refreshBalance() {
        if (status == SaleStatus.CANCELLED) return;
        this.balanceDue = grandTotal.subtract(returnedAmount).subtract(amountPaid).add(returnSettledAmount).max(BigDecimal.ZERO);
        this.paymentStatus = balanceDue.signum() <= 0 ? "PAID" : amountPaid.signum() == 0 ? "UNPAID" : "PARTIALLY_PAID";
    }

    public Long getId() {
        return id;
    }

    public String getInvoiceNo() {
        return invoiceNo;
    }

    public UUID getClientRequestId() {
        return clientRequestId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getSaleType() {
        return saleType;
    }

    public OffsetDateTime getSaleDate() {
        return saleDate;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public BigDecimal getExchangeRate() {
        return exchangeRate;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getItemDiscountTotal() {
        return itemDiscountTotal;
    }

    public BigDecimal getOverallDiscount() {
        return overallDiscount;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public BigDecimal getReturnedAmount() {
        return returnedAmount;
    }

    public BigDecimal getBalanceDue() {
        return balanceDue;
    }

    public BigDecimal getTotalCostBase() {
        return totalCostBase;
    }

    public SaleStatus getStatus() {
        return status;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public String getNotes() {
        return notes;
    }

    public OffsetDateTime getCancelledAt() {
        return cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public List<SaleItem> getItems() {
        return items;
    }
}
