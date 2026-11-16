package af.storemis.purchase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** A purchase draft. exchangeRate is ignored for the base currency and defaults to the current rate otherwise. */
public record PurchaseRequest(
        @NotNull Long supplierId,
        @Size(max = 50) String supplierInvoiceNo,
        @NotNull LocalDate purchaseDate,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 6) BigDecimal exchangeRate,
        @DecimalMin("0") @Digits(integer = 15, fraction = 2) BigDecimal overallDiscount,
        @Size(max = 500) String notes,
        @NotEmpty @Size(max = 200) List<@Valid Item> items,
        Integer version) {

    public record Item(
            @NotNull Long productId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 3) BigDecimal quantity,
            @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal unitCost,
            @DecimalMin("0") @Digits(integer = 15, fraction = 2) BigDecimal discount) {}
}
