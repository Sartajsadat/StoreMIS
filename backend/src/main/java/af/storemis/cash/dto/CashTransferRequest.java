package af.storemis.cash.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * TRANSFER: same currency, {@code toAmount} is ignored. EXCHANGE: different currencies, {@code toAmount} is what the
 * receiving account gets (the rate is derived from the two amounts).
 */
public record CashTransferRequest(UUID clientRequestId, @NotNull @Pattern(regexp = "TRANSFER|EXCHANGE") String type, @NotNull Long fromAccountId,
        @NotNull Long toAccountId, @NotNull @DecimalMin(value = "0.01") @Digits(integer = 15, fraction = 2) BigDecimal fromAmount,
        @DecimalMin(value = "0.01") @Digits(integer = 15, fraction = 2) BigDecimal toAmount, @Size(max = 300) String notes) {}
