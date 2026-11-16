package af.storemis.cash.repository;

import af.storemis.cash.entity.CashAccount;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CashAccountRepository extends JpaRepository<CashAccount, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CashAccount a where a.id = :id")
    Optional<CashAccount> lock(Long id);

    List<CashAccount> findAllByOrderByIdAsc();

    Optional<CashAccount> findFirstByCurrencyCodeAndDefaultAccountTrue(String currencyCode);
}
