package af.storemis.sales.service;

import af.storemis.common.exception.BusinessException;
import af.storemis.common.exception.ErrorCode;
import af.storemis.common.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sale arithmetic (pure, unit-tested).
 * <pre>
 * line gross  = qty × unit price          line total = gross − line discount
 * grand total = Σ line totals − overall discount
 * overall share(line) = overall × line total / Σ line totals  (remainder to the last line; used by returns)
 * </pre>
 */
public final class SaleCalculator {

    private SaleCalculator() {}

    public record LineInput(BigDecimal quantity, BigDecimal unitPrice, BigDecimal discount) {}

    public record LineResult(BigDecimal gross, BigDecimal discount, BigDecimal lineTotal, BigDecimal overallShare) {}

    public record Result(BigDecimal subtotal, BigDecimal itemDiscountTotal, BigDecimal overallDiscount, BigDecimal grandTotal, List<LineResult> lines) {}

    public static Result calculate(List<LineInput> inputs, BigDecimal overallDiscount) {
        BigDecimal overall = Money.amount(overallDiscount);
        List<BigDecimal> gross = new ArrayList<>();
        List<BigDecimal> disc = new ArrayList<>();
        List<BigDecimal> totals = new ArrayList<>();
        BigDecimal subtotal = Money.ZERO;
        BigDecimal itemDiscounts = Money.ZERO;
        BigDecimal net = Money.ZERO;
        for (int i = 0; i < inputs.size(); i++) {
            LineInput in = inputs.get(i);
            BigDecimal g = Money.amount(in.quantity().multiply(in.unitPrice()));
            BigDecimal d = Money.amount(in.discount());
            if (d.signum() < 0 || d.compareTo(g) > 0) throw new BusinessException(ErrorCode.DISCOUNT_TOO_LARGE, Map.of("line", i + 1));
            gross.add(g);
            disc.add(d);
            totals.add(g.subtract(d));
            subtotal = subtotal.add(g);
            itemDiscounts = itemDiscounts.add(d);
            net = net.add(g.subtract(d));
        }
        if (overall.signum() < 0 || overall.compareTo(net) > 0) throw new BusinessException(ErrorCode.DISCOUNT_TOO_LARGE, Map.of("line", 0));
        List<LineResult> lines = new ArrayList<>();
        BigDecimal allocated = Money.ZERO;
        for (int i = 0; i < inputs.size(); i++) {
            BigDecimal share;
            if (net.signum() == 0 || overall.signum() == 0) share = Money.ZERO;
            else if (i == inputs.size() - 1) share = overall.subtract(allocated);
            else share = overall.multiply(totals.get(i)).divide(net, Money.AMOUNT_SCALE, RoundingMode.HALF_UP);
            allocated = allocated.add(share);
            lines.add(new LineResult(gross.get(i), disc.get(i), totals.get(i), share));
        }
        return new Result(subtotal, itemDiscounts, overall, net.subtract(overall), lines);
    }

    /**
     * How much of the invoice a payment covers: paid × (payment rate ÷ invoice rate), both in base units per currency unit.
     * Same currency → 1:1.
     */
    public static BigDecimal crossRate(String paymentCurrency, BigDecimal paymentRate, String invoiceCurrency, BigDecimal invoiceRate) {
        if (paymentCurrency.equals(invoiceCurrency)) return BigDecimal.ONE;
        return paymentRate.divide(invoiceRate, Money.RATE_SCALE, RoundingMode.HALF_UP);
    }
}
