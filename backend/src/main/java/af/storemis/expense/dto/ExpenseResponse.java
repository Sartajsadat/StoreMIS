package af.storemis.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ExpenseResponse(Long id, String expenseNo, Long categoryId, String categoryCode, String categoryName, LocalDate expenseDate,
        LocalDate businessDate, String description, BigDecimal amount, String currency, BigDecimal exchangeRate, Long cashAccountId,
        String cashAccountName, boolean hasAttachment, String status, String voidReason, OffsetDateTime voidedAt, String voidedByName,
        OffsetDateTime createdAt, Long createdBy, String createdByName) {}
