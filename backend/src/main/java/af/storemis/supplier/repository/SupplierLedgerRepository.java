package af.storemis.supplier.repository;

import af.storemis.supplier.entity.SupplierLedgerEntry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SupplierLedgerRepository extends JpaRepository<SupplierLedgerEntry, Long> {

    Optional<SupplierLedgerEntry> findFirstBySupplierIdAndCurrencyCodeOrderByIdDesc(Long supplierId, String currencyCode);

    boolean existsBySupplierIdAndCurrencyCode(Long supplierId, String currencyCode);

    /** Latest balance per supplier and currency: rows of [supplierId, currency, balance]. */
    @Query(value = """
            select distinct on (supplier_id, currency_code) supplier_id, currency_code, balance_after
            from supplier_ledger_entries where supplier_id in (:ids)
            order by supplier_id, currency_code, id desc""", nativeQuery = true)
    List<Object[]> latestBalances(Collection<Long> ids);

    /** Total owed to suppliers per currency (positive balances only): rows of [currency, total]. */
    @Query(value = """
            select currency_code, sum(balance_after) from (
              select distinct on (supplier_id, currency_code) currency_code, balance_after
              from supplier_ledger_entries order by supplier_id, currency_code, id desc) t
            where balance_after > 0 group by currency_code order by currency_code""", nativeQuery = true)
    List<Object[]> totalPayables();

    @Query("""
            select e from SupplierLedgerEntry e where e.supplierId = :supplierId and e.currencyCode = :currency
              and e.businessDate >= :from and e.businessDate <= :to order by e.id""")
    List<SupplierLedgerEntry> statement(Long supplierId, String currency, LocalDate from, LocalDate to);

    @Query(value = """
            select balance_after from supplier_ledger_entries where supplier_id = :supplierId and currency_code = :currency
              and business_date < :from order by id desc limit 1""", nativeQuery = true)
    Optional<BigDecimal> balanceBefore(Long supplierId, String currency, LocalDate from);
}
