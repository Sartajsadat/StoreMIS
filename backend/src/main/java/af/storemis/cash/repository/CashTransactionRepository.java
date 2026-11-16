package af.storemis.cash.repository;

import af.storemis.cash.entity.CashTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface CashTransactionRepository extends JpaRepository<CashTransaction, Long>, JpaSpecificationExecutor<CashTransaction> {

    boolean existsByCashAccountId(Long cashAccountId);

    /** Balance of an account at the end of the day before {@code date}. */
    @Query("""
            select coalesce(sum(case when t.direction = af.storemis.cash.entity.CashDirection.IN then t.amount else -t.amount end), 0)
            from CashTransaction t where t.cashAccountId = :accountId and t.businessDate < :date""")
    BigDecimal balanceBefore(Long accountId, LocalDate date);

    /** Rows: [accountId, txnType, direction, sum(amount), count]. */
    @Query("""
            select t.cashAccountId, t.txnType, t.direction, sum(t.amount), count(t) from CashTransaction t
            where t.businessDate = :date group by t.cashAccountId, t.txnType, t.direction""")
    List<Object[]> totalsByType(LocalDate date);

    /** Rows: [accountId, createdBy, direction, sum(amount)]. */
    @Query("""
            select t.cashAccountId, t.createdBy, t.direction, sum(t.amount) from CashTransaction t
            where t.businessDate = :date group by t.cashAccountId, t.createdBy, t.direction""")
    List<Object[]> totalsByUser(LocalDate date);
}
