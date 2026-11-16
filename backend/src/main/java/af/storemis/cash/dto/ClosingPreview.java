package af.storemis.cash.dto;

import af.storemis.cash.entity.CashDirection;
import af.storemis.cash.entity.CashTxnType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Everything the Admin sees on the Daily Cash Closing screen for one business date. */
public record ClosingPreview(LocalDate date, LocalDate lastClosedDate, boolean closed, boolean canClose, boolean canReopen, List<AccountDay> accounts) {

    public record AccountDay(Long accountId, String accountName, String currency, BigDecimal openingBalance, BigDecimal totalIn, BigDecimal totalOut,
            BigDecimal expectedBalance, List<TypeTotal> byType, List<UserTotal> byUser, Closing closing) {}

    public record TypeTotal(CashTxnType type, CashDirection direction, BigDecimal amount, long count) {}

    public record UserTotal(Long userId, String userName, BigDecimal totalIn, BigDecimal totalOut) {}

    public record Closing(Long id, String status, BigDecimal countedBalance, BigDecimal difference, String notes, String closedByName,
            OffsetDateTime closedAt, String reopenedByName, OffsetDateTime reopenedAt, String reopenReason) {}
}
