package af.storemis.expense.repository;

import af.storemis.expense.entity.Expense;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    Optional<Expense> findByClientRequestId(UUID clientRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Expense e where e.id = :id")
    Optional<Expense> lock(Long id);

    /** Rows: [currencyCode, sum(amount), sum(amount × rate)] of posted expenses in the period. */
    @Query("""
            select e.currencyCode, sum(e.amount), sum(e.amount * e.exchangeRate) from Expense e
            where e.status = 'POSTED' and e.businessDate between :from and :to group by e.currencyCode""")
    List<Object[]> totals(LocalDate from, LocalDate to);

    @Query("select count(e) > 0 from Expense e where e.categoryId = :categoryId")
    boolean existsByCategory(Long categoryId);
}
