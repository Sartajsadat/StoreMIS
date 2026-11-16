package af.storemis.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Money handed over at the till, in one currency. exchangeRate = base units per 1 unit (defaults to the current rate). */
public record PaymentLine(
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 2) BigDecimal amount,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 6) BigDecimal exchangeRate,
        @NotNull Long cashAccountId) {}
