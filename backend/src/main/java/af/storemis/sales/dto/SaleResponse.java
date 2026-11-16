package af.storemis.sales.dto;

import af.storemis.sales.entity.SaleStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** A sale. Cost fields are null unless the user may see cost prices. */
public record SaleResponse(Long id, String invoiceNo, Long customerId, String customerName, String customerPhone, boolean walkIn, String saleType,
        OffsetDateTime saleDate, LocalDate businessDate, String currency, BigDecimal exchangeRate, BigDecimal subtotal, BigDecimal itemDiscountTotal,
        BigDecimal overallDiscount, BigDecimal grandTotal, BigDecimal amountPaid, BigDecimal returnedAmount, BigDecimal balanceDue, BigDecimal totalCostBase,
        SaleStatus status, String paymentStatus, String notes, String cancelReason, Long createdBy, String createdByName, int version, List<Item> items,
        List<Payment> payments) {

    public record Item(Long id, int lineNo, Long productId, String productName, String productCode, String unitName, BigDecimal quantity,
            BigDecimal unitPrice, BigDecimal discount, BigDecimal lineTotal, BigDecimal unitCostBase, BigDecimal returnedQuantity) {}

    public record Payment(Long paymentId, String paymentNo, String kind, OffsetDateTime paymentDate, String currency, BigDecimal paidAmount,
            BigDecimal appliedAmount, BigDecimal exchangeRate, String cashAccountName, String status, boolean released) {}
}
