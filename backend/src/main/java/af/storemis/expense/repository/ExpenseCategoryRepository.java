package af.storemis.expense.repository;

import af.storemis.expense.entity.ExpenseCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {

    List<ExpenseCategory> findAllByOrderByIdAsc();

    @Query("select count(c) > 0 from ExpenseCategory c where lower(c.name) = lower(:name) and (:excludeId is null or c.id <> :excludeId)")
    boolean existsByNameIgnoreCase(String name, Long excludeId);
}
