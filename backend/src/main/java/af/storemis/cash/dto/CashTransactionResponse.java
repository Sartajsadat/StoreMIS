package af.storemis.cash.dto;

import af.storemis.cash.entity.CashDirection;
import af.storemis.cash.entity.CashTxnType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record CashTransactionResponse(Long id, String txnNo, Long accountId, String accountName, String currency, CashTxnType type,
        CashDirection direction, BigDecimal amount, BigDecimal balanceAfter, String sourceType, Long sourceId, String referenceNo,
        LocalDate businessDate, String description, OffsetDateTime createdAt, String createdByName) {}
