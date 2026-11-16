package af.storemis.returns.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Goods given back against a confirmed document. Immutable once posted. */
@Entity
@Table(name = "purchase_returns")
public class PurchaseReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "return_no", nullable = false, unique = true, updatable = false)
    private String returnNo;

    @Column(name = "client_request_id", updatable = false)
    private UUID clientRequestId;

    @Column(name = "purchase_id", nullable = false, updatable = false)
    private Long purchaseId;

    @Column(name = "supplier_id", nullable = false, updatable = false)
    private Long supplierId;

    @Column(name = "return_date", nullable = false, updatable = false)
    private OffsetDateTime returnDate;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(name = "currency_code", nullable = false, updatable = false, columnDefinition = "bpchar(3)")
    private String currencyCode;

    @Column(name = "exchange_rate", nullable = false, updatable = false, precision = 19, scale = 6)
    private BigDecimal exchangeRate;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "total_cost_base", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalCostBase = BigDecimal.ZERO;

    @Column(name = "payable_reduction", nullable = false, precision = 19, scale = 2)
    private BigDecimal payableReduction = BigDecimal.ZERO;

    @Column(name = "cash_refund_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal cashRefundAmount = BigDecimal.ZERO;

    @Column(name = "refund_cash_account_id")
    private Long refundCashAccountId;

    @Column(name = "credit_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal creditAmount = BigDecimal.ZERO;

    @Column(name = "reason", nullable = false, updatable = false)
    private String reason;

    @Column(name = "status", nullable = false)
    private String status = "POSTED";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<PurchaseReturnItem> items = new ArrayList<>();

    protected PurchaseReturn() {}

    public PurchaseReturn(String returnNo, UUID clientRequestId, Long purchaseId, Long supplierId, OffsetDateTime returnDate, LocalDate businessDate, String currencyCode,
            BigDecimal exchangeRate, String reason, Long createdBy) {
        this.returnNo = returnNo;
        this.clientRequestId = clientRequestId;
        this.purchaseId = purchaseId;
        this.supplierId = supplierId;
        this.returnDate = returnDate;
        this.businessDate = businessDate;
        this.currencyCode = currencyCode;
        this.exchangeRate = exchangeRate;
        this.reason = reason;
        this.createdAt = returnDate;
        this.createdBy = createdBy;
    }

    public void settle(BigDecimal total, BigDecimal totalCostBase, BigDecimal payableReduction, BigDecimal cashRefund, Long refundAccountId, BigDecimal credit) {
        this.totalAmount = total;
        this.totalCostBase = totalCostBase;
        this.payableReduction = payableReduction;
        this.cashRefundAmount = cashRefund;
        this.refundCashAccountId = refundAccountId;
        this.creditAmount = credit;
    }

    public Long getId() {
        return id;
    }

    public String getReturnNo() {
        return returnNo;
    }

    public Long getPurchaseId() {
        return purchaseId;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public OffsetDateTime getReturnDate() {
        return returnDate;
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

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getTotalCostBase() {
        return totalCostBase;
    }

    public BigDecimal getPayableReduction() {
        return payableReduction;
    }

    public BigDecimal getCashRefundAmount() {
        return cashRefundAmount;
    }

    public Long getRefundCashAccountId() {
        return refundCashAccountId;
    }

    public BigDecimal getCreditAmount() {
        return creditAmount;
    }

    public String getReason() {
        return reason;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public List<PurchaseReturnItem> getItems() {
        return items;
    }
}
