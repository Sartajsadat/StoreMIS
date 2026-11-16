package af.storemis.purchase.entity;

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

/**
 * A purchase. Drafts can be edited freely; once CONFIRMED the document and its lines never change
 * (payments, returns and cancellation are separate records).
 */
@Entity
@Table(name = "purchases")
public class Purchase extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_no")
    private String purchaseNo;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "supplier_invoice_no")
    private String supplierInvoiceNo;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PurchaseStatus status = PurchaseStatus.DRAFT;

    @Column(name = "payment_status", nullable = false)
    private String paymentStatus = "UNPAID";

    @Column(name = "notes")
    private String notes;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<PurchaseItem> items = new ArrayList<>();

    public void setHeader(Long supplierId, String supplierInvoiceNo, LocalDate purchaseDate, String currencyCode, BigDecimal exchangeRate, String notes) {
        this.supplierId = supplierId;
        this.supplierInvoiceNo = supplierInvoiceNo;
        this.purchaseDate = purchaseDate;
        this.currencyCode = currencyCode;
        this.exchangeRate = exchangeRate;
        this.notes = notes;
    }

    public void setTotals(BigDecimal subtotal, BigDecimal itemDiscountTotal, BigDecimal overallDiscount, BigDecimal grandTotal) {
        this.subtotal = subtotal;
        this.itemDiscountTotal = itemDiscountTotal;
        this.overallDiscount = overallDiscount;
        this.grandTotal = grandTotal;
        refreshBalance();
    }

    public void confirm(String purchaseNo, LocalDate businessDate, OffsetDateTime at, Long by) {
        this.purchaseNo = purchaseNo;
        this.businessDate = businessDate;
        this.status = PurchaseStatus.CONFIRMED;
        this.confirmedAt = at;
        this.confirmedBy = by;
    }

    public void cancel(String reason, OffsetDateTime at, Long by) {
        this.status = PurchaseStatus.CANCELLED;
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
        if (status == PurchaseStatus.CANCELLED) return;
        this.balanceDue = grandTotal.subtract(returnedAmount).subtract(amountPaid).add(returnSettledAmount).max(BigDecimal.ZERO);
        this.paymentStatus = balanceDue.signum() <= 0 ? "PAID" : amountPaid.signum() == 0 ? "UNPAID" : "PARTIALLY_PAID";
    }

    public Long getId() {
        return id;
    }

    public String getPurchaseNo() {
        return purchaseNo;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public String getSupplierInvoiceNo() {
        return supplierInvoiceNo;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
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

    public PurchaseStatus getStatus() {
        return status;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public String getNotes() {
        return notes;
    }

    public OffsetDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public Long getConfirmedBy() {
        return confirmedBy;
    }

    public OffsetDateTime getCancelledAt() {
        return cancelledAt;
    }

    public Long getCancelledBy() {
        return cancelledBy;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public List<PurchaseItem> getItems() {
        return items;
    }
}
