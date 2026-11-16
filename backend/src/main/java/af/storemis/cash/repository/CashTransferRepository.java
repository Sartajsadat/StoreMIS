package af.storemis.cash.repository;

import af.storemis.cash.entity.CashTransfer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CashTransferRepository extends JpaRepository<CashTransfer, Long>, JpaSpecificationExecutor<CashTransfer> {

    Optional<CashTransfer> findByClientRequestId(UUID clientRequestId);
}
