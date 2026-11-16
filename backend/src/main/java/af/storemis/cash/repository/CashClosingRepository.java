package af.storemis.cash.repository;

import af.storemis.cash.entity.CashClosing;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CashClosingRepository extends JpaRepository<CashClosing, Long> {

    /** The latest business date that is currently closed. */
    @Query("select max(c.businessDate) from CashClosing c where c.status = 'CLOSED'")
    Optional<LocalDate> lastClosedDate();

    List<CashClosing> findByBusinessDateOrderByIdAsc(LocalDate businessDate);

    List<CashClosing> findByBusinessDateAndStatus(LocalDate businessDate, String status);

    @Query("select c from CashClosing c where c.businessDate between :from and :to order by c.businessDate desc, c.id asc")
    List<CashClosing> findBetween(LocalDate from, LocalDate to);
}
