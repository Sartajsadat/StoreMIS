package af.storemis.purchase.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;

/** amountPaid (purchase currency) is taken from cashAccountId now; the rest becomes a payable to the supplier. */
public record ConfirmPurchaseRequest(@DecimalMin("0") @Digits(integer = 15, fraction = 2) BigDecimal amountPaid, Long cashAccountId) {}
