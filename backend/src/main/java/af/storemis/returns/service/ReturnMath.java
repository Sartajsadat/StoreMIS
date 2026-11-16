package af.storemis.returns.service;

import af.storemis.common.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Value of returned goods at the net price actually charged (after the line discount and its share of the overall discount). */
final class ReturnMath {

    private ReturnMath() {}

    static BigDecimal netLine(BigDecimal lineTotal, BigDecimal overallShare) {
        return Money.amount(lineTotal.subtract(overallShare == null ? BigDecimal.ZERO : overallShare));
    }

    static BigDecimal unitNet(BigDecimal netLine, BigDecimal quantity) {
        return netLine.divide(quantity, 4, RoundingMode.HALF_UP);
    }

    /**
     * Value of {@code qty} out of a line. Returning everything that is left gives exactly what remains of the line's value,
     * so a line returned in several parts always adds up to its net total.
     */
    static BigDecimal value(BigDecimal netLine, BigDecimal lineQty, BigDecimal alreadyReturnedValue, BigDecimal returnableQty, BigDecimal qty) {
        if (qty.compareTo(returnableQty) == 0) return Money.amount(netLine.subtract(alreadyReturnedValue).max(BigDecimal.ZERO));
        return Money.amount(netLine.multiply(qty).divide(lineQty, 10, RoundingMode.HALF_UP));
    }
}
