package af.storemis.catalog.repository;

import af.storemis.catalog.entity.Category;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("select count(c) > 0 from Category c where lower(c.name) = lower(:name) and (:excludeId is null or c.id <> :excludeId)")
    boolean existsByNameIgnoreCase(String name, Long excludeId);

    @Query("""
            select c.id, count(p.id) from Category c left join Product p on p.category.id = c.id
            group by c.id""")
    List<Object[]> countProductsPerCategory();

    List<Category> findAllByOrderByNameAsc();
}
