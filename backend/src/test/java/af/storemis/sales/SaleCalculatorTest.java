package af.storemis.sales;

import static org.assertj.core.api.Assertions.assertThat;

import af.storemis.sales.service.SaleCalculator;
import af.storemis.sales.service.SaleCalculator.LineInput;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SaleCalculatorTest {

    static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void totalsWithLineAndOverallDiscounts() {
        var r = SaleCalculator.calculate(List.of(new LineInput(d("2"), d("5000"), d("200")), new LineInput(d("1"), d("1500"), d("0"))), d("300"));
        assertThat(r.subtotal()).isEqualByComparingTo("11500");
        assertThat(r.itemDiscountTotal()).isEqualByComparingTo("200");
        assertThat(r.grandTotal()).isEqualByComparingTo("11000");
        assertThat(r.lines().get(0).overallShare().add(r.lines().get(1).overallShare())).isEqualByComparingTo("300");
    }

    @Test
    void crossRateConvertsPaymentIntoInvoiceCurrency() {
        // USD paid against an AFN invoice: 1 USD covers 70 AFN.
        assertThat(SaleCalculator.crossRate("USD", d("70"), "AFN", BigDecimal.ONE)).isEqualByComparingTo("70");
        // AFN paid against a USD invoice recorded at 70: 1 AFN covers 1/70 USD.
        assertThat(SaleCalculator.crossRate("AFN", BigDecimal.ONE, "USD", d("70"))).isEqualByComparingTo("0.014286");
        assertThat(SaleCalculator.crossRate("AFN", BigDecimal.ONE, "AFN", BigDecimal.ONE)).isEqualByComparingTo("1");
    }
}
