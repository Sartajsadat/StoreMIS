package af.storemis.cash.entity;

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

/** One line of the append-only cash ledger. */
@Entity
@Table(name = "cash_transactions")
public class CashTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "txn_no", nullable = false, unique = true, updatable = false)
    private String txnNo;

    @Column(name = "cash_account_id", nullable = false, updatable = false)
    private Long cashAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false, updatable = false)
    private CashTxnType txnType;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, updatable = false)
    private CashDirection direction;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "exchange_rate", nullable = false, updatable = false, precision = 19, scale = 6)
    private BigDecimal exchangeRate;

    @Column(name = "source_type", nullable = false, updatable = false)
    private String sourceType;

    @Column(name = "source_id", updatable = false)
    private Long sourceId;

    @Column(name = "reference_no", updatable = false)
    private String referenceNo;

    @Column(name = "reversal_of_id", updatable = false)
    private Long reversalOfId;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(name = "description", updatable = false)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    protected CashTransaction() {}

    public CashTransaction(String txnNo, Long cashAccountId, CashTxnType txnType, CashDirection direction, BigDecimal amount, BigDecimal balanceAfter,
            BigDecimal exchangeRate, String sourceType, Long sourceId, String referenceNo, Long reversalOfId, LocalDate businessDate, String description,
            OffsetDateTime createdAt, Long createdBy) {
        this.txnNo = txnNo;
        this.cashAccountId = cashAccountId;
        this.txnType = txnType;
        this.direction = direction;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.exchangeRate = exchangeRate;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.referenceNo = referenceNo;
        this.reversalOfId = reversalOfId;
        this.businessDate = businessDate;
        this.description = description;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public String getTxnNo() {
        return txnNo;
    }

    public Long getCashAccountId() {
        return cashAccountId;
    }

    public CashTxnType getTxnType() {
        return txnType;
    }

    public CashDirection getDirection() {
        return direction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public BigDecimal getExchangeRate() {
        return exchangeRate;
    }

    public String getSourceType() {
        return sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public String getReferenceNo() {
        return referenceNo;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public String getDescription() {
        return description;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }
}
