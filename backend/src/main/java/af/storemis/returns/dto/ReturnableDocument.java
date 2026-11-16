package af.storemis.returns.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** An original sale or purchase with what can still be returned per line (net prices, after all discounts). */
public record ReturnableDocument(Long documentId, String documentNo, Long partyId, String partyName, boolean walkIn, LocalDate businessDate,
        String currency, BigDecimal grandTotal, BigDecimal amountPaid, BigDecimal returnedAmount, BigDecimal balanceDue, String status,
        List<Line> items) {

    public record Line(Long itemId, Long productId, String productName, String unitName, boolean allowDecimal, BigDecimal quantity,
            BigDecimal returnedQuantity, BigDecimal returnableQuantity, BigDecimal unitNetPrice, BigDecimal stockOnHand) {}
}
