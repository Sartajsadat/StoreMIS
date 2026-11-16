package af.storemis.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** Collect money from a customer: applied to saleId first if given, otherwise oldest unpaid invoices; any rest is customer credit. */
public record CustomerPaymentRequest(
        @NotNull Long customerId,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 2) BigDecimal amount,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 6) BigDecimal exchangeRate,
        @NotNull Long cashAccountId,
        Long saleId,
        @Size(max = 300) String notes,
        UUID clientRequestId) {}
