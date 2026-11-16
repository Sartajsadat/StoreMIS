package af.storemis.payment.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Money paid to a supplier, allocated to one or more purchases; any rest is an advance (supplier credit). */
@Entity
@Table(name = "supplier_payments")
public class SupplierPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_no", nullable = false, unique = true, updatable = false)
    private String paymentNo;

    @Column(name = "client_request_id", unique = true, updatable = false)
    private UUID clientRequestId;

    @Column(name = "supplier_id", nullable = false, updatable = false)
    private Long supplierId;

    @Column(name = "payment_kind", nullable = false, updatable = false)
    private String paymentKind;

    @Column(name = "payment_date", nullable = false, updatable = false)
    private OffsetDateTime paymentDate;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(name = "currency_code", nullable = false, columnDefinition = "bpchar(3)", updatable = false)
    private String currencyCode;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "exchange_rate", nullable = false, updatable = false, precision = 19, scale = 6)
    private BigDecimal exchangeRate;

    @Column(name = "cash_account_id", nullable = false, updatable = false)
    private Long cashAccountId;

    @Column(name = "unallocated_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal unallocatedAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status = PaymentStatus.POSTED;

    @Column(name = "notes", updatable = false)
    private String notes;

    @Column(name = "reversed_at")
    private OffsetDateTime reversedAt;

    @Column(name = "reversed_by")
    private Long reversedBy;

    @Column(name = "reversal_reason")
    private String reversalReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL)
    private List<SupplierPaymentAllocation> allocations = new ArrayList<>();

    protected SupplierPayment() {}

    public SupplierPayment(String paymentNo, UUID clientRequestId, Long supplierId, String paymentKind, OffsetDateTime paymentDate, LocalDate businessDate,
            String currencyCode, BigDecimal amount, BigDecimal exchangeRate, Long cashAccountId, String notes, Long createdBy) {
        this.paymentNo = paymentNo;
        this.clientRequestId = clientRequestId;
        this.supplierId = supplierId;
        this.paymentKind = paymentKind;
        this.paymentDate = paymentDate;
        this.businessDate = businessDate;
        this.currencyCode = currencyCode;
        this.amount = amount;
        this.exchangeRate = exchangeRate;
        this.cashAccountId = cashAccountId;
        this.notes = notes;
        this.createdAt = paymentDate;
        this.createdBy = createdBy;
    }

    public void setUnallocatedAmount(BigDecimal unallocatedAmount) {
        this.unallocatedAmount = unallocatedAmount;
    }

    public void reverse(String reason, OffsetDateTime at, Long by) {
        this.status = PaymentStatus.REVERSED;
        this.reversalReason = reason;
        this.reversedAt = at;
        this.reversedBy = by;
    }

    public Long getId() {
        return id;
    }

    public String getPaymentNo() {
        return paymentNo;
    }

    public UUID getClientRequestId() {
        return clientRequestId;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public String getPaymentKind() {
        return paymentKind;
    }

    public OffsetDateTime getPaymentDate() {
        return paymentDate;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getExchangeRate() {
        return exchangeRate;
    }

    public Long getCashAccountId() {
        return cashAccountId;
    }

    public BigDecimal getUnallocatedAmount() {
        return unallocatedAmount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public OffsetDateTime getReversedAt() {
        return reversedAt;
    }

    public String getReversalReason() {
        return reversalReason;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public List<SupplierPaymentAllocation> getAllocations() {
        return allocations;
    }
}
