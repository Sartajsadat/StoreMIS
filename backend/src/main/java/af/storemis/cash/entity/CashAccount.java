package af.storemis.cash.entity;

import af.storemis.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** A cash drawer / safe in one currency. Its balance changes only through CashPostingService. */
@Entity
@Table(name = "cash_accounts")
public class CashAccount extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "currency_code", nullable = false, columnDefinition = "bpchar(3)", updatable = false)
    private String currencyCode;

    @Column(name = "account_type", nullable = false)
    private String accountType;

    @Column(name = "current_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal currentBalance;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAccount;

    @Column(name = "active", nullable = false)
    private boolean active;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getAccountType() {
        return accountType;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    /** Only for CashPostingService, under the row lock, together with a cash_transactions row. */
    public void applyBalance(BigDecimal balance) {
        this.currentBalance = balance;
    }

    public boolean isDefaultAccount() {
        return defaultAccount;
    }

    public boolean isActive() {
        return active;
    }
}
