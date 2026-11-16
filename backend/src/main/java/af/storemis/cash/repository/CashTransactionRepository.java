package af.storemis.cash.repository;

import af.storemis.cash.entity.CashTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CashTransactionRepository extends JpaRepository<CashTransaction, Long>, JpaSpecificationExecutor<CashTransaction> {

    boolean existsByCashAccountId(Long cashAccountId);
}
