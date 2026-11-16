package af.storemis.supplier.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** What the store already owed this supplier on go-live day (from the paper ledger). */
public record SupplierOpeningBalanceRequest(@NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 2) BigDecimal amount, @Size(max = 300) String notes) {}
