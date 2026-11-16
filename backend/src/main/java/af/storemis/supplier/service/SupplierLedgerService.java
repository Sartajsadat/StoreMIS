package af.storemis.supplier.service;

import af.storemis.common.exception.NotFoundException;
import af.storemis.common.money.Money;
import af.storemis.common.security.SecurityUtils;
import af.storemis.common.time.BusinessClock;
import af.storemis.supplier.entity.SupplierEntryType;
import af.storemis.supplier.entity.SupplierLedgerEntry;
import af.storemis.supplier.repository.SupplierLedgerRepository;
import af.storemis.supplier.repository.SupplierRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Posts to the supplier payable ledger. The supplier row is locked so running balances never interleave. */
@Service
public class SupplierLedgerService {

    private final SupplierLedgerRepository ledger;
    private final SupplierRepository suppliers;
    private final BusinessClock clock;

    public SupplierLedgerService(SupplierLedgerRepository ledger, SupplierRepository suppliers, BusinessClock clock) {
        this.ledger = ledger;
        this.suppliers = suppliers;
        this.clock = clock;
    }

    /** We owe more (purchase, reversed payment, opening balance, refund received). */
    @Transactional(propagation = Propagation.MANDATORY)
    public SupplierLedgerEntry credit(Long supplierId, String currency, SupplierEntryType type, BigDecimal amount, String sourceType, Long sourceId,
            String reference, String notes) {
        return post(supplierId, currency, type, Money.ZERO, Money.amount(amount), sourceType, sourceId, reference, notes);
    }

    /** We owe less (payment, return, cancellation). */
    @Transactional(propagation = Propagation.MANDATORY)
    public SupplierLedgerEntry debit(Long supplierId, String currency, SupplierEntryType type, BigDecimal amount, String sourceType, Long sourceId,
            String reference, String notes) {
        return post(supplierId, currency, type, Money.amount(amount), Money.ZERO, sourceType, sourceId, reference, notes);
    }

    @Transactional(readOnly = true)
    public BigDecimal balance(Long supplierId, String currency) {
        return ledger.findFirstBySupplierIdAndCurrencyCodeOrderByIdDesc(supplierId, currency).map(SupplierLedgerEntry::getBalanceAfter)
                .orElse(Money.ZERO);
    }

    private SupplierLedgerEntry post(Long supplierId, String currency, SupplierEntryType type, BigDecimal debit, BigDecimal credit, String sourceType,
            Long sourceId, String reference, String notes) {
        if (debit.signum() == 0 && credit.signum() == 0) return null;
        suppliers.lock(supplierId).orElseThrow(() -> new NotFoundException("Supplier", supplierId));
        BigDecimal after = balance(supplierId, currency).add(credit).subtract(debit);
        return ledger.save(new SupplierLedgerEntry(supplierId, currency, type, debit, credit, after, sourceType, sourceId, reference, clock.today(), notes,
                clock.now(), SecurityUtils.currentUserId().orElse(null)));
    }
}
