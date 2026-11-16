package af.storemis.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Pay a supplier. With purchaseId the money goes to that purchase first; otherwise it is applied to the oldest open
 * purchases. Anything left over is kept as an advance. exchangeRate defaults to the current rate.
 */
public record SupplierPaymentRequest(
        @NotNull Long supplierId,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 2) BigDecimal amount,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 6) BigDecimal exchangeRate,
        @NotNull Long cashAccountId,
        Long purchaseId,
        @Size(max = 300) String notes,
        UUID clientRequestId) {}
