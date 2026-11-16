package af.storemis.expense.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** The currency is the cash account's currency. */
public record ExpenseRequest(UUID clientRequestId, @NotNull Long categoryId, LocalDate expenseDate, @NotBlank @Size(max = 300) String description,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount, @NotNull Long cashAccountId) {}
