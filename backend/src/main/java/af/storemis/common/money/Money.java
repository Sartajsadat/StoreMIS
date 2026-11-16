package af.storemis.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money helpers. Document amounts have 2 decimals, rates 6, unit costs 4. Never float/double. */
public final class Money {

    public static final int AMOUNT_SCALE = 2;
    public static final int RATE_SCALE = 6;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(AMOUNT_SCALE);

    private Money() {}

    public static BigDecimal amount(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal rate(BigDecimal v) {
        return v.setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public static boolean positive(BigDecimal v) {
        return v != null && v.signum() > 0;
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
