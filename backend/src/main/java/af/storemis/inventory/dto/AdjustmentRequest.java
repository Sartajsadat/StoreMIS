package af.storemis.inventory.dto;

import af.storemis.inventory.entity.AdjustmentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * unitCost (base currency) is required for OPENING; for INCREASE it defaults to the current average cost;
 * it is ignored for DECREASE and DAMAGE, which always leave at the average cost.
 */
public record AdjustmentRequest(
        @NotNull Long productId,
        @NotNull AdjustmentType type,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 3) BigDecimal quantity,
        @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal unitCost,
        @NotBlank @Size(min = 3, max = 300) String reason,
        UUID clientRequestId) {}
