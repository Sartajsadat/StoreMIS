package af.storemis.purchase.dto;

import af.storemis.purchase.entity.PurchaseStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record PurchaseResponse(Long id, String purchaseNo, Long supplierId, String supplierName, String supplierInvoiceNo, LocalDate purchaseDate,
        LocalDate businessDate, String currency, BigDecimal exchangeRate, BigDecimal subtotal, BigDecimal itemDiscountTotal, BigDecimal overallDiscount,
        BigDecimal grandTotal, BigDecimal amountPaid, BigDecimal returnedAmount, BigDecimal balanceDue, PurchaseStatus status, String paymentStatus,
        String notes, String cancelReason, OffsetDateTime createdAt, String createdByName, OffsetDateTime confirmedAt, int version, List<Item> items,
        List<Payment> payments) {

    public record Item(Long id, int lineNo, Long productId, String productName, String unitName, BigDecimal quantity, BigDecimal unitCost,
            BigDecimal discount, BigDecimal lineTotal, BigDecimal overallDiscountShare, BigDecimal landedUnitCostBase, BigDecimal returnedQuantity) {}

    public record Payment(Long paymentId, String paymentNo, OffsetDateTime paymentDate, String currency, BigDecimal paidAmount, BigDecimal appliedAmount,
            String status, boolean released) {}
}
