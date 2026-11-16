package af.storemis.cash.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record CashTransferResponse(Long id, String transferNo, String type, Long fromAccountId, String fromAccountName, String fromCurrency,
        Long toAccountId, String toAccountName, String toCurrency, BigDecimal fromAmount, BigDecimal toAmount, BigDecimal exchangeRate,
        LocalDate businessDate, String notes, OffsetDateTime createdAt, String createdByName) {}
