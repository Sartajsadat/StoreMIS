package af.storemis.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** version is required when updating, ignored when creating. */
public record CategoryRequest(@NotBlank @Size(max = 100) String name, @Size(max = 300) String description, Integer version) {}
