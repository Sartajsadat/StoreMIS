package af.storemis.customer.entity;

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

/** Append-only receivable ledger line. debit = the customer owes more; credit = owes less. */
@Entity
@Table(name = "customer_ledger_entries")
public class CustomerLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "currency_code", nullable = false, columnDefinition = "bpchar(3)", updatable = false)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false)
    private CustomerEntryType entryType;

    @Column(name = "debit", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal debit;

    @Column(name = "credit", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal credit;

    @Column(name = "balance_after", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "source_type", nullable = false, updatable = false)
    private String sourceType;

    @Column(name = "source_id", updatable = false)
    private Long sourceId;

    @Column(name = "reference_no", updatable = false)
    private String referenceNo;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(name = "notes", updatable = false)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    protected CustomerLedgerEntry() {}

    public CustomerLedgerEntry(Long customerId, String currencyCode, CustomerEntryType entryType, BigDecimal debit, BigDecimal credit,
            BigDecimal balanceAfter, String sourceType, Long sourceId, String referenceNo, LocalDate businessDate, String notes, OffsetDateTime createdAt,
            Long createdBy) {
        this.customerId = customerId;
        this.currencyCode = currencyCode;
        this.entryType = entryType;
        this.debit = debit;
        this.credit = credit;
        this.balanceAfter = balanceAfter;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.referenceNo = referenceNo;
        this.businessDate = businessDate;
        this.notes = notes;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public CustomerEntryType getEntryType() {
        return entryType;
    }

    public BigDecimal getDebit() {
        return debit;
    }

    public BigDecimal getCredit() {
        return credit;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
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
