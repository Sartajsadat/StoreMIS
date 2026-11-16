package af.storemis.product.repository;

import af.storemis.product.entity.Product;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @Query("select count(p) > 0 from Product p where p.code is not null and lower(p.code) = lower(:code) and (:excludeId is null or p.id <> :excludeId)")
    boolean existsByCodeIgnoreCase(String code, Long excludeId);

    /**
     * Fast POS search on name, code or brand (uses the trigram indexes). An exact code / barcode match comes first,
     * then names that start with the text, then everything else.
     */
    @Query(value = """
            select p.* from products p
            where p.active = true and (p.name ilike :pattern or p.code ilike :pattern or p.brand ilike :pattern)
            order by case when lower(p.code) = lower(:q) then 0 when p.name ilike :prefix then 1 else 2 end, p.name
            limit :limit""", nativeQuery = true)
    List<Product> quickSearch(String q, String pattern, String prefix, int limit);
}
