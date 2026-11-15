package af.storemis.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PreferencesDto(
        @NotNull @Pattern(regexp = "en|fa|ps") String language,
        @NotNull @Pattern(regexp = "light|dark") String theme,
        boolean sidebarCollapsed) {}
