package af.storemis.cash.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The physically counted cash of every active account for a business date. Notes are required when any count differs. */
public record CloseDayRequest(@NotNull LocalDate date, @NotEmpty List<@Valid Counted> accounts, @Size(max = 300) String notes) {

    public record Counted(@NotNull Long accountId, @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 2) BigDecimal countedBalance) {}
}
