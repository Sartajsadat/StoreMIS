package af.storemis.payment.repository;

import af.storemis.payment.entity.SupplierPaymentAllocation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SupplierPaymentAllocationRepository extends JpaRepository<SupplierPaymentAllocation, Long> {

    @Query("select a from SupplierPaymentAllocation a join fetch a.payment p where a.purchaseId = :purchaseId order by a.id")
    List<SupplierPaymentAllocation> findByPurchaseId(Long purchaseId);
}
