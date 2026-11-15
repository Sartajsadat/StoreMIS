package af.storemis.user.repository;

import af.storemis.common.security.Role;
import af.storemis.user.entity.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {

    @Query("select u from AppUser u where lower(u.username) = lower(:username)")
    Optional<AppUser> findByUsernameIgnoreCase(String username);

    @Query("select count(u) > 0 from AppUser u where lower(u.username) = lower(:username)")
    boolean existsByUsernameIgnoreCase(String username);

    @Query("select count(u) from AppUser u where u.role.code = :role and u.active = true")
    long countActiveByRole(Role role);
}
