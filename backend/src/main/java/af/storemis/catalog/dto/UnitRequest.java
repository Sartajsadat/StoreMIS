package af.storemis.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnitRequest(@NotBlank @Size(max = 50) String name, @Size(max = 10) String symbol, boolean allowDecimal, Integer version) {}
