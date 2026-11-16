package af.storemis.cash.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A same-currency transfer or a currency exchange between two cash accounts (two cash legs). */
@Entity
@Table(name = "cash_transfers")
public class CashTransfer {

    public static final String TRANSFER = "TRANSFER";
    public static final String EXCHANGE = "EXCHANGE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_no", nullable = false, unique = true, updatable = false)
    private String transferNo;

    @Column(name = "client_request_id", updatable = false)
    private UUID clientRequestId;

    @Column(name = "transfer_type", nullable = false, updatable = false)
    private String transferType;

    @Column(name = "from_account_id", nullable = false, updatable = false)
    private Long fromAccountId;

    @Column(name = "to_account_id", nullable = false, updatable = false)
    private Long toAccountId;

    @Column(name = "from_amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal fromAmount;

    @Column(name = "to_amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal toAmount;

    @Column(name = "exchange_rate", nullable = false, updatable = false, precision = 19, scale = 6)
    private BigDecimal exchangeRate;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(name = "notes", updatable = false)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    protected CashTransfer() {}

    public CashTransfer(String transferNo, UUID clientRequestId, String transferType, Long fromAccountId, Long toAccountId, BigDecimal fromAmount,
            BigDecimal toAmount, BigDecimal exchangeRate, LocalDate businessDate, String notes, OffsetDateTime createdAt, Long createdBy) {
        this.transferNo = transferNo;
        this.clientRequestId = clientRequestId;
        this.transferType = transferType;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.fromAmount = fromAmount;
        this.toAmount = toAmount;
        this.exchangeRate = exchangeRate;
        this.businessDate = businessDate;
        this.notes = notes;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public String getTransferNo() {
        return transferNo;
    }

    public String getTransferType() {
        return transferType;
    }

    public Long getFromAccountId() {
        return fromAccountId;
    }

    public Long getToAccountId() {
        return toAccountId;
    }

    public BigDecimal getFromAmount() {
        return fromAmount;
    }

    public BigDecimal getToAmount() {
        return toAmount;
    }

    public BigDecimal getExchangeRate() {
        return exchangeRate;
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
