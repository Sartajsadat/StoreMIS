package af.storemis.common.security;

import java.util.Set;

/** The authenticated user for the current request (loaded fresh from the database on every request). */
public record AppPrincipal(Long id, String username, Role role, Set<Permission> permissions) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean has(Permission permission) {
        return isAdmin() || permissions.contains(permission);
    }
}
