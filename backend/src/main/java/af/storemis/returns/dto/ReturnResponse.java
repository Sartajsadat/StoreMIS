package af.storemis.returns.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * A sales or purchase return. {@code documentNo} is the invoice / purchase number, {@code party} the customer or
 * supplier, {@code reduction} the part that reduced what was still owed. Cost is null unless the user may see it.
 */
public record ReturnResponse(Long id, String returnNo, String kind, Long documentId, String documentNo, Long partyId, String partyName,
        OffsetDateTime returnDate, LocalDate businessDate, String currency, BigDecimal exchangeRate, BigDecimal totalAmount, BigDecimal totalCostBase,
        BigDecimal reduction, BigDecimal cashRefundAmount, Long refundCashAccountId, String refundCashAccountName, BigDecimal creditAmount,
        String reason, String createdByName, List<Item> items) {

    public record Item(Long id, Long originalItemId, Long productId, String productName, String unitName, BigDecimal quantity, BigDecimal unitNetPrice,
            BigDecimal lineTotal, String condition, String reason) {}
}
