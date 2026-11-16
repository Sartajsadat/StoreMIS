package af.storemis.supplier.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** A party statement in one currency: opening balance, lines, closing balance. Reused for customers. */
public record StatementResponse(String currency, LocalDate from, LocalDate to, BigDecimal openingBalance, BigDecimal totalDebit, BigDecimal totalCredit,
        BigDecimal closingBalance, List<Line> lines) {

    public record Line(Long id, LocalDate date, String type, String referenceNo, String sourceType, Long sourceId, BigDecimal debit, BigDecimal credit,
            BigDecimal balance, String notes) {}
}
