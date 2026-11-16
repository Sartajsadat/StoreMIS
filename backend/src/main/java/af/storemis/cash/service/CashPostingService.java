package af.storemis.cash.service;

import af.storemis.cash.entity.CashAccount;
import af.storemis.cash.entity.CashDirection;
import af.storemis.cash.entity.CashTransaction;
import af.storemis.cash.entity.CashTxnType;
import af.storemis.cash.repository.CashAccountRepository;
import af.storemis.cash.repository.CashTransactionRepository;
import af.storemis.common.exception.BusinessException;
import af.storemis.common.exception.ErrorCode;
import af.storemis.common.exception.NotFoundException;
import af.storemis.common.money.Money;
import af.storemis.common.security.SecurityUtils;
import af.storemis.common.time.BusinessClock;
import af.storemis.numbering.DocumentNumberService;
import af.storemis.numbering.DocumentType;
import af.storemis.settings.service.SettingsService;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only code that changes cash balances. Locks the account row, checks the currency and the negative-cash rule,
 * appends a cash_transactions row with the balance after it, and updates the cached balance — all inside the
 * caller's transaction.
 */
@Service
public class CashPostingService {

    /** What a cash movement belongs to. */
    public record CashSource(String type, Long id, String referenceNo) {}

    private final CashAccountRepository accounts;
    private final CashTransactionRepository transactions;
    private final DocumentNumberService numbers;
    private final SettingsService settings;
    private final BusinessClock clock;

    public CashPostingService(CashAccountRepository accounts, CashTransactionRepository transactions, DocumentNumberService numbers,
            SettingsService settings, BusinessClock clock) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.numbers = numbers;
        this.settings = settings;
        this.clock = clock;
    }

    /** Returns the account if it exists, is active and holds the expected currency. */
    @Transactional(readOnly = true)
    public CashAccount requireAccount(Long accountId, String expectedCurrency) {
        CashAccount a = accounts.findById(accountId).orElseThrow(() -> new NotFoundException("CashAccount", accountId));
        if (!a.isActive()) throw new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("field", "cashAccountId"));
        if (expectedCurrency != null && !a.getCurrencyCode().equals(expectedCurrency)) {
            throw new BusinessException(ErrorCode.CURRENCY_MISMATCH, Map.of("account", a.getName(), "currency", expectedCurrency));
        }
        return a;
    }

    @Transactional(readOnly = true)
    public CashAccount defaultAccount(String currency) {
        return accounts.findFirstByCurrencyCodeAndDefaultAccountTrue(currency)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("field", "cashAccountId")));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CashTransaction post(Long accountId, CashDirection direction, BigDecimal amount, CashTxnType type, BigDecimal exchangeRate, CashSource source,
            String description) {
        BigDecimal value = Money.amount(amount);
        if (value.signum() <= 0) throw new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("field", "amount"));
        CashAccount account = accounts.lock(accountId).orElseThrow(() -> new NotFoundException("CashAccount", accountId));
        BigDecimal after = direction == CashDirection.IN ? account.getCurrentBalance().add(value) : account.getCurrentBalance().subtract(value);
        if (after.signum() < 0 && direction == CashDirection.OUT && !settings.getEntity().isAllowNegativeCash()) {
            throw new BusinessException(ErrorCode.CASH_INSUFFICIENT, Map.of("account", account.getName(),
                    "available", account.getCurrentBalance().toPlainString(), "currency", account.getCurrencyCode()));
        }
        account.applyBalance(after);
        return transactions.save(new CashTransaction(numbers.next(DocumentType.CASH_TXN), account.getId(), type, direction, value, after,
                exchangeRate == null ? BigDecimal.ONE : exchangeRate, source.type(), source.id(), source.referenceNo(), null, clock.today(),
                description, clock.now(), SecurityUtils.currentUserId().orElse(null)));
    }
}
