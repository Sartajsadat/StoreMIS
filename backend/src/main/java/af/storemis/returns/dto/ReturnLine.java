package af.storemis.returns.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** One returned line. {@code condition} is only used for sales returns (SELLABLE goes back to stock, DAMAGED does not). */
public record ReturnLine(@NotNull Long itemId, @NotNull @DecimalMin(value = "0.001") @Digits(integer = 11, fraction = 3) BigDecimal quantity,
        @Pattern(regexp = "SELLABLE|DAMAGED") String condition, @Size(max = 300) String reason) {}
