package af.storemis.inventory.repository;

import af.storemis.inventory.entity.ProductStock;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Read-only queries joining products with their stock balance. */
public interface StockQueryRepository extends Repository<ProductStock, Long> {

    String FILTER = """
            from Product p join ProductStock s on s.productId = p.id
            where (:q is null or lower(p.name) like :q or lower(coalesce(p.code, '')) like :q or lower(coalesce(p.brand, '')) like :q)
              and (:categoryId is null or p.category.id = :categoryId)
              and (:active is null or p.active = :active)
              and (:status is null
                   or (:status = 'OUT_OF_STOCK' and s.quantityOnHand <= 0)
                   or (:status = 'LOW_STOCK' and s.quantityOnHand > 0 and p.minStockLevel > 0 and s.quantityOnHand <= p.minStockLevel)
                   or (:status = 'IN_STOCK' and s.quantityOnHand > 0
                       and (p.minStockLevel is null or p.minStockLevel = 0 or s.quantityOnHand > p.minStockLevel)))
            """;

    @Query(value = "select p, s " + FILTER, countQuery = "select count(p) " + FILTER)
    Page<Object[]> search(String q, Long categoryId, Boolean active, String status, Pageable pageable);

    @Query("select count(p) from Product p where p.active = true")
    long countActive();

    @Query("""
            select count(p) from Product p join ProductStock s on s.productId = p.id where p.active = true
              and s.quantityOnHand > 0 and p.minStockLevel > 0 and s.quantityOnHand <= p.minStockLevel""")
    long countLow();

    @Query("select count(p) from Product p join ProductStock s on s.productId = p.id where p.active = true and s.quantityOnHand <= 0")
    long countOut();

    @Query("select coalesce(sum(s.inventoryValue), 0) from ProductStock s")
    BigDecimal totalValue();

    /** Active products at or below their minimum (or out of stock), most urgent first. */
    @Query("""
            select p, s from Product p join ProductStock s on s.productId = p.id where p.active = true
              and (s.quantityOnHand <= 0 or (p.minStockLevel > 0 and s.quantityOnHand <= p.minStockLevel))
            order by case when s.quantityOnHand <= 0 then 0 else 1 end, s.quantityOnHand, p.name""")
    List<Object[]> alerts(Pageable pageable);
}
