package af.storemis.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReverseRequest(@NotBlank @Size(min = 3, max = 300) String reason) {}
