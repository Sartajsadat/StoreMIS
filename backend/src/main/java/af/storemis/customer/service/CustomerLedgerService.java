package af.storemis.customer.service;

import af.storemis.common.exception.NotFoundException;
import af.storemis.common.money.Money;
import af.storemis.common.security.SecurityUtils;
import af.storemis.common.time.BusinessClock;
import af.storemis.customer.entity.CustomerEntryType;
import af.storemis.customer.entity.CustomerLedgerEntry;
import af.storemis.customer.repository.CustomerLedgerRepository;
import af.storemis.customer.repository.CustomerRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Posts to the customer receivable ledger; the customer row is locked so running balances never interleave. */
@Service
public class CustomerLedgerService {

    private final CustomerLedgerRepository ledger;
    private final CustomerRepository customers;
    private final BusinessClock clock;

    public CustomerLedgerService(CustomerLedgerRepository ledger, CustomerRepository customers, BusinessClock clock) {
        this.ledger = ledger;
        this.customers = customers;
        this.clock = clock;
    }

    /** The customer owes more (sale, opening balance, reversed payment, cash refunded to them). */
    @Transactional(propagation = Propagation.MANDATORY)
    public CustomerLedgerEntry debit(Long customerId, String currency, CustomerEntryType type, BigDecimal amount, String sourceType, Long sourceId,
            String reference, String notes) {
        return post(customerId, currency, type, Money.amount(amount), Money.ZERO, sourceType, sourceId, reference, notes);
    }

    /** The customer owes less (payment, return, cancellation). */
    @Transactional(propagation = Propagation.MANDATORY)
    public CustomerLedgerEntry credit(Long customerId, String currency, CustomerEntryType type, BigDecimal amount, String sourceType, Long sourceId,
            String reference, String notes) {
        return post(customerId, currency, type, Money.ZERO, Money.amount(amount), sourceType, sourceId, reference, notes);
    }

    @Transactional(readOnly = true)
    public BigDecimal balance(Long customerId, String currency) {
        return ledger.findFirstByCustomerIdAndCurrencyCodeOrderByIdDesc(customerId, currency).map(CustomerLedgerEntry::getBalanceAfter).orElse(Money.ZERO);
    }

    private CustomerLedgerEntry post(Long customerId, String currency, CustomerEntryType type, BigDecimal debit, BigDecimal credit, String sourceType,
            Long sourceId, String reference, String notes) {
        if (debit.signum() == 0 && credit.signum() == 0) return null;
        customers.lock(customerId).orElseThrow(() -> new NotFoundException("Customer", customerId));
        BigDecimal after = balance(customerId, currency).add(debit).subtract(credit);
        return ledger.save(new CustomerLedgerEntry(customerId, currency, type, debit, credit, after, sourceType, sourceId, reference, clock.today(), notes,
                clock.now(), SecurityUtils.currentUserId().orElse(null)));
    }
}
