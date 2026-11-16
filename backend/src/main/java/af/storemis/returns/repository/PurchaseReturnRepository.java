package af.storemis.returns.repository;

import af.storemis.returns.entity.PurchaseReturn;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface PurchaseReturnRepository extends JpaRepository<PurchaseReturn, Long>, JpaSpecificationExecutor<PurchaseReturn> {

    Optional<PurchaseReturn> findByClientRequestId(UUID clientRequestId);

    List<PurchaseReturn> findByPurchaseIdOrderByIdAsc(Long purchaseId);

    /** Value already returned for one original line (to avoid rounding drift on the last return). */
    @Query("select coalesce(sum(i.lineTotal), 0) from PurchaseReturnItem i where i.purchaseItemId = :purchaseItemId")
    BigDecimal returnedValue(Long purchaseItemId);
}
