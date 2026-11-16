package af.storemis.inventory.repository;

import af.storemis.inventory.entity.StockAdjustment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, Long>, JpaSpecificationExecutor<StockAdjustment> {

    Optional<StockAdjustment> findByClientRequestId(UUID clientRequestId);
}
