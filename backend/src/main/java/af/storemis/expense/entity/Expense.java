package af.storemis.expense.entity;

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

/** Money spent on running the business, paid from one cash account in that account's currency. */
@Entity
@Table(name = "expenses")
public class Expense {

    public static final String POSTED = "POSTED";
    public static final String VOIDED = "VOIDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expense_no", nullable = false, unique = true, updatable = false)
    private String expenseNo;

    @Column(name = "client_request_id", updatable = false)
    private UUID clientRequestId;

    @Column(name = "category_id", nullable = false, updatable = false)
    private Long categoryId;

    @Column(name = "expense_date", nullable = false, updatable = false)
    private LocalDate expenseDate;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(name = "description", nullable = false, updatable = false)
    private String description;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, updatable = false, columnDefinition = "bpchar(3)")
    private String currencyCode;

    @Column(name = "exchange_rate", nullable = false, updatable = false, precision = 19, scale = 6)
    private BigDecimal exchangeRate;

    @Column(name = "cash_account_id", nullable = false, updatable = false)
    private Long cashAccountId;

    @Column(name = "attachment_path")
    private String attachmentPath;

    @Column(name = "status", nullable = false)
    private String status = POSTED;

    @Column(name = "void_reason")
    private String voidReason;

    @Column(name = "voided_at")
    private OffsetDateTime voidedAt;

    @Column(name = "voided_by")
    private Long voidedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    protected Expense() {}

    public Expense(String expenseNo, UUID clientRequestId, Long categoryId, LocalDate expenseDate, LocalDate businessDate, String description,
            BigDecimal amount, String currencyCode, BigDecimal exchangeRate, Long cashAccountId, OffsetDateTime createdAt, Long createdBy) {
        this.expenseNo = expenseNo;
        this.clientRequestId = clientRequestId;
        this.categoryId = categoryId;
        this.expenseDate = expenseDate;
        this.businessDate = businessDate;
        this.description = description;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.exchangeRate = exchangeRate;
        this.cashAccountId = cashAccountId;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public void voidExpense(String reason, OffsetDateTime at, Long by) {
        this.status = VOIDED;
        this.voidReason = reason;
        this.voidedAt = at;
        this.voidedBy = by;
    }

    public Long getId() {
        return id;
    }

    public String getExpenseNo() {
        return expenseNo;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public BigDecimal getExchangeRate() {
        return exchangeRate;
    }

    public Long getCashAccountId() {
        return cashAccountId;
    }

    public String getAttachmentPath() {
        return attachmentPath;
    }

    public void setAttachmentPath(String attachmentPath) {
        this.attachmentPath = attachmentPath;
    }

    public String getStatus() {
        return status;
    }

    public String getVoidReason() {
        return voidReason;
    }

    public OffsetDateTime getVoidedAt() {
        return voidedAt;
    }

    public Long getVoidedBy() {
        return voidedBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }
}
