package af.storemis.user.dto;

import af.storemis.common.security.Permission;
import af.storemis.common.security.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String username,
        @NotBlank @Size(min = 2, max = 150) String fullName,
        @Size(max = 30) String phone,
        @NotNull Role role,
        @NotBlank @Size(min = 8, max = 100) String password,
        Set<Permission> permissions) {}
