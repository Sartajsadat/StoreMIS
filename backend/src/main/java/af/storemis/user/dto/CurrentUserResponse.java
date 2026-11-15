package af.storemis.user.dto;

import af.storemis.common.security.Permission;
import af.storemis.common.security.Role;
import java.util.Set;

/** The signed-in user as the frontend needs it: identity, role, effective permissions and UI preferences. */
public record CurrentUserResponse(Long id, String username, String fullName, String phone, Role role, Set<Permission> permissions,
        boolean mustChangePassword, PreferencesDto preferences) {}
