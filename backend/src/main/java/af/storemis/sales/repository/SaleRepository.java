package af.storemis.sales.repository;

import af.storemis.sales.entity.Sale;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SaleRepository extends JpaRepository<Sale, Long>, JpaSpecificationExecutor<Sale> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id")
    Optional<Sale> lock(Long id);

    Optional<Sale> findByClientRequestId(UUID clientRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from Sale s where s.customerId = :customerId and s.status = af.storemis.sales.entity.SaleStatus.CONFIRMED
              and s.balanceDue > 0 order by s.businessDate, s.id""")
    List<Sale> lockOpenForCustomer(Long customerId);

    @Query("""
            select s from Sale s where s.customerId = :customerId and s.status = af.storemis.sales.entity.SaleStatus.CONFIRMED
              and s.balanceDue > 0 order by s.businessDate, s.id""")
    List<Sale> findOpenForCustomer(Long customerId);

    @Query("select s from Sale s where s.status = af.storemis.sales.entity.SaleStatus.DRAFT and s.createdBy = :userId order by s.id desc")
    List<Sale> findDraftsOf(Long userId);

    /** Total confirmed sales per currency for a customer: rows of [currency, sum]. */
    @Query("""
            select s.currencyCode, sum(s.grandTotal - s.returnedAmount) from Sale s
            where s.customerId = :customerId and s.status = af.storemis.sales.entity.SaleStatus.CONFIRMED group by s.currencyCode""")
    List<Object[]> totalsForCustomer(Long customerId);

    /** Latest prices for a product (optionally for one customer): rows of [unitPrice, currency, saleDate]. */
    @Query("""
            select i.unitPrice, s.currencyCode, s.saleDate from SaleItem i join i.sale s
            where i.productId = :productId and s.status = af.storemis.sales.entity.SaleStatus.CONFIRMED
              and (:customerId is null or s.customerId = :customerId)
            order by s.id desc""")
    List<Object[]> lastPrices(Long productId, Long customerId, Pageable pageable);

    default Optional<Object[]> lastPrice(Long productId, Long customerId) {
        return lastPrices(productId, customerId, org.springframework.data.domain.PageRequest.of(0, 1)).stream().findFirst();
    }
}
