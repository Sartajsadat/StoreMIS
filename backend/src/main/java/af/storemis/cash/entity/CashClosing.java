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

/** The end-of-day count of one cash account. While CLOSED, nothing can be posted on or before its date. */
@Entity
@Table(name = "cash_closings")
public class CashClosing {

    public static final String CLOSED = "CLOSED";
    public static final String REOPENED = "REOPENED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(name = "cash_account_id", nullable = false, updatable = false)
    private Long cashAccountId;

    @Column(name = "opening_balance", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal openingBalance;

    @Column(name = "total_in", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal totalIn;

    @Column(name = "total_out", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal totalOut;

    @Column(name = "expected_balance", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal expectedBalance;

    @Column(name = "counted_balance", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal countedBalance;

    @Column(name = "difference", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal difference;

    @Column(name = "difference_txn_id", updatable = false)
    private Long differenceTxnId;

    @Column(name = "status", nullable = false)
    private String status = CLOSED;

    @Column(name = "notes", updatable = false)
    private String notes;

    @Column(name = "closed_by", updatable = false)
    private Long closedBy;

    @Column(name = "closed_at", nullable = false, updatable = false)
    private OffsetDateTime closedAt;

    @Column(name = "reopened_by")
    private Long reopenedBy;

    @Column(name = "reopened_at")
    private OffsetDateTime reopenedAt;

    @Column(name = "reopen_reason")
    private String reopenReason;

    protected CashClosing() {}

    public CashClosing(LocalDate businessDate, Long cashAccountId, BigDecimal openingBalance, BigDecimal totalIn, BigDecimal totalOut,
            BigDecimal expectedBalance, BigDecimal countedBalance, Long differenceTxnId, String notes, Long closedBy, OffsetDateTime closedAt) {
        this.businessDate = businessDate;
        this.cashAccountId = cashAccountId;
        this.openingBalance = openingBalance;
        this.totalIn = totalIn;
        this.totalOut = totalOut;
        this.expectedBalance = expectedBalance;
        this.countedBalance = countedBalance;
        this.difference = countedBalance.subtract(expectedBalance);
        this.differenceTxnId = differenceTxnId;
        this.notes = notes;
        this.closedBy = closedBy;
        this.closedAt = closedAt;
    }

    public void reopen(String reason, Long by, OffsetDateTime at) {
        this.status = REOPENED;
        this.reopenReason = reason;
        this.reopenedBy = by;
        this.reopenedAt = at;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public Long getCashAccountId() {
        return cashAccountId;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public BigDecimal getTotalIn() {
        return totalIn;
    }

    public BigDecimal getTotalOut() {
        return totalOut;
    }

    public BigDecimal getExpectedBalance() {
        return expectedBalance;
    }

    public BigDecimal getCountedBalance() {
        return countedBalance;
    }

    public BigDecimal getDifference() {
        return difference;
    }

    public String getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public Long getClosedBy() {
        return closedBy;
    }

    public OffsetDateTime getClosedAt() {
        return closedAt;
    }

    public Long getReopenedBy() {
        return reopenedBy;
    }

    public OffsetDateTime getReopenedAt() {
        return reopenedAt;
    }

    public String getReopenReason() {
        return reopenReason;
    }
}
