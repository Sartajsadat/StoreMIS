package af.storemis.payment.dto;

import af.storemis.payment.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record SupplierPaymentResponse(Long id, String paymentNo, Long supplierId, String supplierName, String kind, OffsetDateTime paymentDate,
        LocalDate businessDate, String currency, BigDecimal amount, BigDecimal exchangeRate, Long cashAccountId, String cashAccountName,
        BigDecimal unallocatedAmount, PaymentStatus status, String notes, String reversalReason, String createdByName, List<Allocation> allocations) {

    public record Allocation(Long purchaseId, String purchaseNo, String purchaseCurrency, BigDecimal appliedAmount, BigDecimal paidAmount,
            BigDecimal crossRate, boolean released) {}
}
