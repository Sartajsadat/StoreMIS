package af.storemis.user.dto;

import jakarta.validation.constraints.NotNull;

public record StatusRequest(@NotNull Boolean active) {}
