package af.storemis.auth.service;

import af.storemis.config.AppProperties;
import af.storemis.common.time.BusinessClock;
import af.storemis.user.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Counts failed logins in its own transaction (the login itself fails and rolls back) and locks the account when needed. */
@Service
public class LoginAttemptService {

    private final AppUserRepository users;
    private final BusinessClock clock;
    private final AppProperties.Security props;

    public LoginAttemptService(AppUserRepository users, BusinessClock clock, AppProperties properties) {
        this.users = users;
        this.clock = clock;
        this.props = properties.security();
    }

    /** @return true if this failure locked the account */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordFailure(Long userId) {
        var user = users.findById(userId).orElseThrow();
        int attempts = user.getFailedLoginAttempts() + 1;
        if (attempts >= props.maxFailedAttempts()) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(clock.now().plusMinutes(props.lockMinutes()));
            return true;
        }
        user.setFailedLoginAttempts(attempts);
        return false;
    }

    public int lockMinutes() {
        return props.lockMinutes();
    }
}
