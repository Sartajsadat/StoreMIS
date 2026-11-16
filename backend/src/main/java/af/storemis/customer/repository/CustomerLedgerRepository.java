package af.storemis.customer.repository;

import af.storemis.customer.entity.CustomerLedgerEntry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CustomerLedgerRepository extends JpaRepository<CustomerLedgerEntry, Long> {

    Optional<CustomerLedgerEntry> findFirstByCustomerIdAndCurrencyCodeOrderByIdDesc(Long customerId, String currencyCode);

    boolean existsByCustomerIdAndCurrencyCode(Long customerId, String currencyCode);

    /** Latest balance per customer and currency: rows of [customerId, currency, balance]. */
    @Query(value = """
            select distinct on (customer_id, currency_code) customer_id, currency_code, balance_after
            from customer_ledger_entries where customer_id in (:ids)
            order by customer_id, currency_code, id desc""", nativeQuery = true)
    List<Object[]> latestBalances(Collection<Long> ids);

    /** Total receivable from customers per currency (positive balances only): rows of [currency, total]. */
    @Query(value = """
            select currency_code, sum(balance_after) from (
              select distinct on (customer_id, currency_code) currency_code, balance_after
              from customer_ledger_entries order by customer_id, currency_code, id desc) t
            where balance_after > 0 group by currency_code order by currency_code""", nativeQuery = true)
    List<Object[]> totalReceivables();

    @Query("""
            select e from CustomerLedgerEntry e where e.customerId = :customerId and e.currencyCode = :currency
              and e.businessDate >= :from and e.businessDate <= :to order by e.id""")
    List<CustomerLedgerEntry> statement(Long customerId, String currency, LocalDate from, LocalDate to);

    @Query(value = """
            select balance_after from customer_ledger_entries where customer_id = :customerId and currency_code = :currency
              and business_date < :from order by id desc limit 1""", nativeQuery = true)
    Optional<BigDecimal> balanceBefore(Long customerId, String currency, LocalDate from);
}
