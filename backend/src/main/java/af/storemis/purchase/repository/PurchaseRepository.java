package af.storemis.purchase.repository;

import af.storemis.purchase.entity.Purchase;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PurchaseRepository extends JpaRepository<Purchase, Long>, JpaSpecificationExecutor<Purchase> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Purchase p where p.id = :id")
    Optional<Purchase> lock(Long id);

    /** Open (unpaid or partly paid) confirmed purchases of a supplier, oldest first, locked for allocation. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from Purchase p where p.supplierId = :supplierId and p.status = af.storemis.purchase.entity.PurchaseStatus.CONFIRMED
              and p.balanceDue > 0 order by p.businessDate, p.id""")
    List<Purchase> lockOpenForSupplier(Long supplierId);

    @Query("""
            select p from Purchase p where p.supplierId = :supplierId and p.status = af.storemis.purchase.entity.PurchaseStatus.CONFIRMED
              and p.balanceDue > 0 order by p.businessDate, p.id""")
    List<Purchase> findOpenForSupplier(Long supplierId);
}
