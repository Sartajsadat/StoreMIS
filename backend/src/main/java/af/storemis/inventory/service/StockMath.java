package af.storemis.inventory.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Weighted average cost arithmetic (pure functions, unit-tested).
 * Costs and values are in the base currency with 4 decimals; quantities have 3 decimals.
 */
public final class StockMath {

    public static final int COST_SCALE = 4;
    public static final int QTY_SCALE = 3;

    private StockMath() {}

    /** Balance after a movement. */
    public record Balance(BigDecimal quantity, BigDecimal avgCost, BigDecimal value) {}

    public static BigDecimal qty(BigDecimal q) {
        return q.setScale(QTY_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal money(BigDecimal v) {
        return v.setScale(COST_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Goods in at a known cost: the new average is (old value + qty × cost) / (old qty + qty).
     * If stock was zero or negative (only possible when negative stock is allowed), the incoming cost becomes the new basis.
     */
    public static Balance receive(Balance before, BigDecimal quantity, BigDecimal unitCost) {
        BigDecimal newQty = qty(before.quantity().add(quantity));
        if (newQty.signum() <= 0) {
            BigDecimal avg = money(unitCost);
            return new Balance(newQty, avg, money(newQty.multiply(avg)));
        }
        if (before.quantity().signum() <= 0) {
            BigDecimal avg = money(unitCost);
            return new Balance(newQty, avg, money(newQty.multiply(avg)));
        }
        BigDecimal value = before.value().add(quantity.multiply(unitCost));
        BigDecimal avg = value.divide(newQty, COST_SCALE, RoundingMode.HALF_UP);
        return new Balance(newQty, avg, money(value));
    }

    /** Goods out at the current average cost; the average does not change. */
    public static Balance issue(Balance before, BigDecimal quantity) {
        BigDecimal newQty = qty(before.quantity().subtract(quantity));
        BigDecimal value = newQty.signum() == 0 ? BigDecimal.ZERO : before.value().subtract(quantity.multiply(before.avgCost()));
        if (newQty.signum() > 0 && value.signum() < 0) value = BigDecimal.ZERO;
        return new Balance(newQty, before.avgCost(), money(value));
    }

    /**
     * Goods out at a specific historical cost (purchase return / purchase cancellation): the value drops by exactly what
     * was added, and the average of what remains is recalculated. The value never goes below zero.
     */
    public static Balance removeAtCost(Balance before, BigDecimal quantity, BigDecimal unitCost) {
        BigDecimal newQty = qty(before.quantity().subtract(quantity));
        if (newQty.signum() <= 0) {
            return new Balance(newQty, before.avgCost(), BigDecimal.ZERO.setScale(COST_SCALE));
        }
        BigDecimal value = before.value().subtract(quantity.multiply(unitCost));
        if (value.signum() < 0) value = BigDecimal.ZERO;
        BigDecimal avg = value.divide(newQty, COST_SCALE, RoundingMode.HALF_UP);
        return new Balance(newQty, avg, money(value));
    }
}
