package af.storemis.config;

import af.storemis.common.security.Role;
import af.storemis.user.entity.AppUser;
import af.storemis.user.repository.AppUserRepository;
import af.storemis.user.repository.RoleRepository;
import af.storemis.user.service.PasswordPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator when the database has no users at all, from STOREMIS_ADMIN_USERNAME /
 * STOREMIS_ADMIN_PASSWORD. The password is temporary: it must be changed at first sign-in.
 */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final AppProperties.BootstrapAdmin props;

    public BootstrapAdminRunner(AppUserRepository users, RoleRepository roles, PasswordEncoder encoder, AppProperties properties) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.props = properties.bootstrapAdmin();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) return;
        if (props.password() == null || props.password().isBlank()) {
            log.warn("No users exist and STOREMIS_ADMIN_PASSWORD is not set — no administrator was created.");
            return;
        }
        PasswordPolicy.check(props.password());
        AppUser admin = new AppUser();
        admin.setUsername(props.username());
        admin.setFullName(props.fullName());
        admin.setRole(roles.findByCode(Role.ADMIN).orElseThrow());
        admin.setPasswordHash(encoder.encode(props.password()));
        admin.setMustChangePassword(true);
        users.save(admin);
        log.info("Created initial administrator '{}' (password change required at first sign-in).", props.username());
    }
}
