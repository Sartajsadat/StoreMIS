package af.storemis.cash.service;

import af.storemis.cash.entity.CashAccount;
import af.storemis.cash.entity.CashDirection;
import af.storemis.cash.entity.CashTransaction;
import af.storemis.cash.entity.CashTxnType;
import af.storemis.cash.repository.CashAccountRepository;
import af.storemis.cash.repository.CashClosingRepository;
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
import java.time.LocalDate;
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
    private final CashClosingRepository closings;

    public CashPostingService(CashAccountRepository accounts, CashTransactionRepository transactions, DocumentNumberService numbers,
            SettingsService settings, BusinessClock clock, CashClosingRepository closings) {
        this.closings = closings;
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

    /** Refuses postings dated on or before the last closed business day ({@code DAY_CLOSED}). */
    public void ensureDayOpen(LocalDate businessDate) {
        closings.lastClosedDate().ifPresent(last -> {
            if (!businessDate.isAfter(last)) throw new BusinessException(ErrorCode.DAY_CLOSED, Map.of("date", last.toString()));
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CashTransaction post(Long accountId, CashDirection direction, BigDecimal amount, CashTxnType type, BigDecimal exchangeRate, CashSource source,
            String description) {
        LocalDate today = clock.today();
        ensureDayOpen(today);
        return write(accountId, direction, amount, type, exchangeRate, source, description, today);
    }

    /** Posting dated to a specific business day; used by the daily closing itself (the day is not closed yet). */
    @Transactional(propagation = Propagation.MANDATORY)
    public CashTransaction postOn(LocalDate businessDate, Long accountId, CashDirection direction, BigDecimal amount, CashTxnType type,
            BigDecimal exchangeRate, CashSource source, String description) {
        ensureDayOpen(businessDate);
        return write(accountId, direction, amount, type, exchangeRate, source, description, businessDate);
    }

    private CashTransaction write(Long accountId, CashDirection direction, BigDecimal amount, CashTxnType type, BigDecimal exchangeRate,
            CashSource source, String description, LocalDate businessDate) {
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
                exchangeRate == null ? BigDecimal.ONE : exchangeRate, source.type(), source.id(), source.referenceNo(), null, businessDate,
                description, clock.now(), SecurityUtils.currentUserId().orElse(null)));
    }
}
