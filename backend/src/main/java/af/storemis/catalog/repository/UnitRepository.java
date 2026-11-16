package af.storemis.catalog.repository;

import af.storemis.catalog.entity.Unit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UnitRepository extends JpaRepository<Unit, Long> {

    @Query("select count(u) > 0 from Unit u where lower(u.name) = lower(:name) and (:excludeId is null or u.id <> :excludeId)")
    boolean existsByNameIgnoreCase(String name, Long excludeId);

    List<Unit> findAllByOrderByIdAsc();
}
