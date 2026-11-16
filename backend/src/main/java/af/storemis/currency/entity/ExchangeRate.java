package af.storemis.currency.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** One rate entry: base-currency units per 1 unit of {@code currencyCode}. Rates are never edited; a new rate is a new row. */
@Entity
@Table(name = "exchange_rates")
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "currency_code", nullable = false, columnDefinition = "bpchar(3)", updatable = false)
    private String currencyCode;

    @Column(name = "rate", nullable = false, precision = 19, scale = 6, updatable = false)
    private BigDecimal rate;

    @Column(name = "effective_at", nullable = false, updatable = false)
    private OffsetDateTime effectiveAt;

    @Column(name = "notes", updatable = false)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    protected ExchangeRate() {}

    public ExchangeRate(String currencyCode, BigDecimal rate, OffsetDateTime effectiveAt, String notes, OffsetDateTime createdAt, Long createdBy) {
        this.currencyCode = currencyCode;
        this.rate = rate;
        this.effectiveAt = effectiveAt;
        this.notes = notes;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public OffsetDateTime getEffectiveAt() {
        return effectiveAt;
    }

    public String getNotes() {
        return notes;
    }

    public Long getCreatedBy() {
        return createdBy;
    }
}
