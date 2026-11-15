package af.storemis.user.repository;

import af.storemis.common.security.Permission;
import af.storemis.user.entity.PermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<PermissionEntity, Permission> {}
