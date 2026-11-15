package af.storemis.common.security;

import org.springframework.stereotype.Component;

/**
 * Permission checks for {@code @PreAuthorize}, e.g. {@code @PreAuthorize("@authz.has('RECORD_PURCHASES')")}.
 * Admins pass every check; Entry Users need the toggle.
 */
@Component("authz")
public class Authz {

    public boolean has(String permission) {
        Permission p = Permission.valueOf(permission);
        return SecurityUtils.currentPrincipal().map(u -> u.has(p)).orElse(false);
    }

    public boolean hasAny(String... permissions) {
        for (String p : permissions) {
            if (has(p)) return true;
        }
        return false;
    }
}
