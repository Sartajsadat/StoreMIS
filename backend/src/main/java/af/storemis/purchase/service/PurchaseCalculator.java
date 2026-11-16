package af.storemis.purchase.service;

import af.storemis.common.exception.BusinessException;
import af.storemis.common.exception.ErrorCode;
import af.storemis.common.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Purchase arithmetic (pure, unit-tested).
 * <pre>
 * line gross   = qty × unit cost                       (2 decimals)
 * line total   = line gross − line discount
 * overall share(line) = overall discount × line total / Σ line totals  (remainder to the last line)
 * landed unit cost (base) = (line total − share) / qty × exchange rate  (4 decimals) → enters the weighted average
 * grand total  = Σ line totals − overall discount
 * </pre>
 */
public final class PurchaseCalculator {

    private PurchaseCalculator() {}

    public record LineInput(BigDecimal quantity, BigDecimal unitCost, BigDecimal discount) {}

    public record LineResult(BigDecimal gross, BigDecimal discount, BigDecimal lineTotal, BigDecimal overallShare, BigDecimal landedUnitCostBase) {}

    public record Result(BigDecimal subtotal, BigDecimal itemDiscountTotal, BigDecimal overallDiscount, BigDecimal grandTotal, List<LineResult> lines) {}

    public static Result calculate(List<LineInput> inputs, BigDecimal overallDiscount, BigDecimal exchangeRate) {
        BigDecimal overall = Money.amount(overallDiscount);
        if (overall.signum() < 0) throw new BusinessException(ErrorCode.DISCOUNT_TOO_LARGE, Map.of("line", 0));
        List<BigDecimal> gross = new ArrayList<>();
        List<BigDecimal> disc = new ArrayList<>();
        List<BigDecimal> totals = new ArrayList<>();
        BigDecimal subtotal = Money.ZERO;
        BigDecimal itemDiscounts = Money.ZERO;
        BigDecimal net = Money.ZERO;
        for (int i = 0; i < inputs.size(); i++) {
            LineInput in = inputs.get(i);
            BigDecimal g = Money.amount(in.quantity().multiply(in.unitCost()));
            BigDecimal d = Money.amount(in.discount());
            if (d.signum() < 0 || d.compareTo(g) > 0) throw new BusinessException(ErrorCode.DISCOUNT_TOO_LARGE, Map.of("line", i + 1));
            BigDecimal t = g.subtract(d);
            gross.add(g);
            disc.add(d);
            totals.add(t);
            subtotal = subtotal.add(g);
            itemDiscounts = itemDiscounts.add(d);
            net = net.add(t);
        }
        if (overall.compareTo(net) > 0) throw new BusinessException(ErrorCode.DISCOUNT_TOO_LARGE, Map.of("line", 0));

        List<LineResult> lines = new ArrayList<>();
        BigDecimal allocated = Money.ZERO;
        for (int i = 0; i < inputs.size(); i++) {
            BigDecimal share;
            if (net.signum() == 0 || overall.signum() == 0) {
                share = Money.ZERO;
            } else if (i == inputs.size() - 1) {
                share = overall.subtract(allocated);
            } else {
                share = overall.multiply(totals.get(i)).divide(net, Money.AMOUNT_SCALE, RoundingMode.HALF_UP);
            }
            allocated = allocated.add(share);
            BigDecimal qty = inputs.get(i).quantity();
            BigDecimal landed = totals.get(i).subtract(share).multiply(exchangeRate).divide(qty, 4, RoundingMode.HALF_UP);
            lines.add(new LineResult(gross.get(i), disc.get(i), totals.get(i), share, landed));
        }
        return new Result(subtotal, itemDiscounts, overall, net.subtract(overall), lines);
    }

    /** UNPAID / PARTIALLY_PAID / PAID from what is still owed. */
    public static String paymentStatus(BigDecimal grandTotal, BigDecimal returned, BigDecimal paid) {
        BigDecimal due = grandTotal.subtract(returned).subtract(paid);
        if (due.signum() <= 0) return "PAID";
        return paid.signum() == 0 ? "UNPAID" : "PARTIALLY_PAID";
    }
}
