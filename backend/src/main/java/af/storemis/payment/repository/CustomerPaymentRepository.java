package af.storemis.payment.repository;

import af.storemis.payment.entity.CustomerPayment;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CustomerPaymentRepository extends JpaRepository<CustomerPayment, Long>, JpaSpecificationExecutor<CustomerPayment> {

    Optional<CustomerPayment> findByClientRequestId(UUID clientRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CustomerPayment p where p.id = :id")
    Optional<CustomerPayment> lock(Long id);
}
