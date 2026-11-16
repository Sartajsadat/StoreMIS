package af.storemis.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Create or update a product. {@code openingQuantity}/{@code openingUnitCost} are only used when creating (Admin):
 * they post the product's opening stock in the same transaction.
 */
public record ProductRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 60) String code,
        @NotNull Long categoryId,
        @NotNull Long unitId,
        @Size(max = 100) String brand,
        @Size(max = 2000) String description,
        @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal minStockLevel,
        Integer version,
        @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal openingQuantity,
        @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal openingUnitCost) {}
