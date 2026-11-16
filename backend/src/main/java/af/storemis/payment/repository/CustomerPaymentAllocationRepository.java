package af.storemis.payment.repository;

import af.storemis.payment.entity.CustomerPaymentAllocation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CustomerPaymentAllocationRepository extends JpaRepository<CustomerPaymentAllocation, Long> {

    @Query("select a from CustomerPaymentAllocation a join fetch a.payment p where a.saleId = :saleId order by a.id")
    List<CustomerPaymentAllocation> findBySaleId(Long saleId);
}
