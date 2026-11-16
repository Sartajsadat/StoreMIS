package af.storemis.inventory.repository;

import af.storemis.inventory.entity.ProductStock;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ProductStockRepository extends JpaRepository<ProductStock, Long> {

    /** Locks the rows in ascending id order, so two documents touching the same products can never deadlock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ProductStock s where s.productId in :ids order by s.productId")
    List<ProductStock> lockAll(Collection<Long> ids);

    List<ProductStock> findByProductIdIn(Collection<Long> ids);

    /** Products whose cached quantity differs from the sum of their ledger. Should always be empty. */
    @Query(value = """
            select s.product_id, s.quantity_on_hand, coalesce(m.total, 0)
            from product_stock s
            left join (select product_id, sum(quantity) as total from stock_movements group by product_id) m on m.product_id = s.product_id
            where s.quantity_on_hand <> coalesce(m.total, 0)""", nativeQuery = true)
    List<Object[]> findLedgerMismatches();
}
