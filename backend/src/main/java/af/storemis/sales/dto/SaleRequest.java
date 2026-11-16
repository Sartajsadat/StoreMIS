package af.storemis.sales.dto;

import af.storemis.payment.dto.PaymentLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A sale from the POS. customerId null = Walk-in Customer. saleType defaults to the customer's type.
 * payments may be empty (all on credit, named customers only) or mix currencies.
 * For drafts (held carts) payments and clientRequestId are ignored.
 */
public record SaleRequest(
        Long customerId,
        @Pattern(regexp = "RETAIL|WHOLESALE") String saleType,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 6) BigDecimal exchangeRate,
        @DecimalMin("0") @Digits(integer = 15, fraction = 2) BigDecimal overallDiscount,
        @Size(max = 500) String notes,
        @NotEmpty @Size(max = 300) List<@Valid Item> items,
        @Size(max = 5) List<@Valid PaymentLine> payments,
        UUID clientRequestId,
        Integer version) {

    public record Item(
            @NotNull Long productId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 3) BigDecimal quantity,
            @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 2) BigDecimal unitPrice,
            @DecimalMin("0") @Digits(integer = 15, fraction = 2) BigDecimal discount) {}
}
