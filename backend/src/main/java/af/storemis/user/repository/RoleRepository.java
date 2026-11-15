package af.storemis.user.repository;

import af.storemis.common.security.Role;
import af.storemis.user.entity.RoleEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<RoleEntity, Long> {

    Optional<RoleEntity> findByCode(Role code);
}
