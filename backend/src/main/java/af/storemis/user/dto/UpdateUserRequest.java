package af.storemis.user.dto;

import af.storemis.common.security.Permission;
import af.storemis.common.security.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record UpdateUserRequest(
        @NotBlank @Size(min = 2, max = 150) String fullName,
        @Size(max = 30) String phone,
        @NotNull Role role,
        Set<Permission> permissions,
        @NotNull Integer version) {}
