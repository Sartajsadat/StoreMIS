package af.storemis.returns.repository;

import af.storemis.returns.entity.SalesReturn;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface SalesReturnRepository extends JpaRepository<SalesReturn, Long>, JpaSpecificationExecutor<SalesReturn> {

    Optional<SalesReturn> findByClientRequestId(UUID clientRequestId);

    List<SalesReturn> findBySaleIdOrderByIdAsc(Long saleId);

    /** Value already returned for one original line (to avoid rounding drift on the last return). */
    @Query("select coalesce(sum(i.lineTotal), 0) from SalesReturnItem i where i.saleItemId = :saleItemId")
    BigDecimal returnedValue(Long saleItemId);
}
