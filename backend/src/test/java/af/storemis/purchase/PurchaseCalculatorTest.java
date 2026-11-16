package af.storemis.purchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import af.storemis.common.exception.BusinessException;
import af.storemis.purchase.service.PurchaseCalculator;
import af.storemis.purchase.service.PurchaseCalculator.LineInput;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PurchaseCalculatorTest {

    static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void overallDiscountIsSpreadIntoLandedCost() {
        var r = PurchaseCalculator.calculate(List.of(new LineInput(d("10"), d("400"), d("0"))), d("100"), BigDecimal.ONE);
        assertThat(r.grandTotal()).isEqualByComparingTo("3900");
        assertThat(r.lines().get(0).landedUnitCostBase()).isEqualByComparingTo("390");
    }

    @Test
    void sharesAddUpExactlyAndUsdIsConvertedAtTheDocumentRate() {
        var r = PurchaseCalculator.calculate(List.of(
                new LineInput(d("3"), d("10"), d("0")),      // 30
                new LineInput(d("3"), d("10"), d("0")),      // 30
                new LineInput(d("3"), d("10"), d("0"))),     // 30
                d("10"), d("70.5"));
        BigDecimal shares = r.lines().stream().map(PurchaseCalculator.LineResult::overallShare).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(shares).isEqualByComparingTo("10");
        assertThat(r.lines().get(0).overallShare()).isEqualByComparingTo("3.33");
        assertThat(r.lines().get(2).overallShare()).isEqualByComparingTo("3.34");
        assertThat(r.grandTotal()).isEqualByComparingTo("80");
        // (30 - 3.33) / 3 × 70.5 = 626.7450
        assertThat(r.lines().get(0).landedUnitCostBase()).isEqualByComparingTo("626.745");
    }

    @Test
    void lineAndOverallDiscountsCannotExceedTheAmounts() {
        assertThatThrownBy(() -> PurchaseCalculator.calculate(List.of(new LineInput(d("1"), d("100"), d("101"))), d("0"), BigDecimal.ONE))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PurchaseCalculator.calculate(List.of(new LineInput(d("1"), d("100"), d("0"))), d("100.01"), BigDecimal.ONE))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void paymentStatus() {
        assertThat(PurchaseCalculator.paymentStatus(d("100"), d("0"), d("0"))).isEqualTo("UNPAID");
        assertThat(PurchaseCalculator.paymentStatus(d("100"), d("0"), d("40"))).isEqualTo("PARTIALLY_PAID");
        assertThat(PurchaseCalculator.paymentStatus(d("100"), d("0"), d("100"))).isEqualTo("PAID");
    }
}
