package af.storemis.customer.repository;

import af.storemis.customer.entity.Customer;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    Optional<Customer> findFirstByWalkInTrue();

    /** Serialises ledger postings per customer so running balances can never interleave. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Customer s where s.id = :id")
    Optional<Customer> lock(Long id);
}
