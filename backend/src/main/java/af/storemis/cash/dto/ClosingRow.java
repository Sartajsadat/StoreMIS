package af.storemis.cash.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** One account's closing in the history list. */
public record ClosingRow(Long id, LocalDate date, Long accountId, String accountName, String currency, BigDecimal openingBalance, BigDecimal totalIn,
        BigDecimal totalOut, BigDecimal expectedBalance, BigDecimal countedBalance, BigDecimal difference, String status, String notes,
        String closedByName, OffsetDateTime closedAt, String reopenReason) {}
