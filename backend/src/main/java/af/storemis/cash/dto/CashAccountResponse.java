package af.storemis.cash.dto;

import af.storemis.cash.entity.CashAccount;
import java.math.BigDecimal;

/** balance is null for users who may only pick an account (not see its balance). */
public record CashAccountResponse(Long id, String name, String currency, String accountType, BigDecimal balance, boolean isDefault, boolean active,
        boolean hasTransactions) {

    public static CashAccountResponse of(CashAccount a, boolean showBalance, boolean hasTransactions) {
        return new CashAccountResponse(a.getId(), a.getName(), a.getCurrencyCode(), a.getAccountType(), showBalance ? a.getCurrentBalance() : null,
                a.isDefaultAccount(), a.isActive(), hasTransactions);
    }
}
