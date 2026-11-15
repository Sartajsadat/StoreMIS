package af.storemis.common.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<AppPrincipal> currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppPrincipal p) {
            return Optional.of(p);
        }
        return Optional.empty();
    }

    public static Optional<Long> currentUserId() {
        return currentPrincipal().map(AppPrincipal::id);
    }

    public static AppPrincipal requirePrincipal() {
        return currentPrincipal().orElseThrow(() -> new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("No user"));
    }
}
