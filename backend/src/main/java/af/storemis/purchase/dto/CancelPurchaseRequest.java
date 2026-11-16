package af.storemis.purchase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** paymentHandling: REFUND = the supplier returns the money now; CREDIT = the supplier keeps it as credit for later purchases. */
public record CancelPurchaseRequest(@NotBlank @Size(min = 3, max = 300) String reason, @NotNull @Pattern(regexp = "REFUND|CREDIT") String paymentHandling) {}
