package af.storemis.cash.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Owner puts money into, or takes money out of, a cash account (capital, not income or expense). */
public record OwnerCashRequest(@NotNull Long accountId, @NotNull @DecimalMin(value = "0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
        @NotBlank @Size(max = 300) String notes) {}
