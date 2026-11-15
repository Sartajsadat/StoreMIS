package af.storemis.user.dto;

import af.storemis.common.security.Permission;
import af.storemis.common.security.Role;
import af.storemis.user.entity.AppUser;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.TreeSet;

public record UserResponse(Long id, String username, String fullName, String phone, Role role, Set<Permission> permissions, boolean active,
        boolean mustChangePassword, boolean locked, OffsetDateTime lastLoginAt, OffsetDateTime createdAt, int version) {

    public static UserResponse from(AppUser u, OffsetDateTime now) {
        boolean locked = u.getLockedUntil() != null && u.getLockedUntil().isAfter(now);
        return new UserResponse(u.getId(), u.getUsername(), u.getFullName(), u.getPhone(), u.getRole(), new TreeSet<>(u.getPermissions()),
                u.isActive(), u.isMustChangePassword(), locked, u.getLastLoginAt(), u.getCreatedAt(), u.getVersion());
    }
}
