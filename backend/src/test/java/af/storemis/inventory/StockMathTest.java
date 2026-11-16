package af.storemis.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import af.storemis.inventory.service.StockMath;
import af.storemis.inventory.service.StockMath.Balance;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class StockMathTest {

    static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    static final Balance EMPTY = new Balance(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    @Test
    void specExampleWeightedAverageIs350() {
        Balance b = StockMath.receive(EMPTY, d("10"), d("300"));
        b = StockMath.receive(b, d("10"), d("400"));
        assertThat(b.quantity()).isEqualByComparingTo("20");
        assertThat(b.avgCost()).isEqualByComparingTo("350");
        assertThat(b.value()).isEqualByComparingTo("7000");
        // Selling one unit costs 350 → gross profit at a price of 500 is 150.
        Balance after = StockMath.issue(b, d("1"));
        assertThat(after.avgCost()).isEqualByComparingTo("350");
        assertThat(after.value()).isEqualByComparingTo("6650");
        assertThat(d("500").subtract(b.avgCost())).isEqualByComparingTo("150");
    }

    @Test
    void issuingEverythingLeavesZeroValue() {
        Balance b = StockMath.receive(EMPTY, d("3"), d("100.3333"));
        Balance after = StockMath.issue(b, d("3"));
        assertThat(after.quantity()).isEqualByComparingTo("0");
        assertThat(after.value()).isEqualByComparingTo("0");
    }

    @Test
    void averageIsRecomputedWithFourDecimals() {
        Balance b = StockMath.receive(EMPTY, d("3"), d("10"));
        b = StockMath.receive(b, d("1"), d("11"));
        assertThat(b.avgCost()).isEqualByComparingTo("10.25");
        b = StockMath.receive(b, d("2"), d("10.1"));
        // (41 + 20.2) / 6 = 10.2
        assertThat(b.avgCost()).isEqualByComparingTo("10.2");
    }

    @Test
    void removingAtHistoricalCostRecalculatesTheRemainder() {
        Balance b = StockMath.receive(EMPTY, d("10"), d("300"));
        b = StockMath.receive(b, d("10"), d("400"));
        // Return 5 of the 400-cost units to the supplier.
        Balance after = StockMath.removeAtCost(b, d("5"), d("400"));
        assertThat(after.quantity()).isEqualByComparingTo("15");
        assertThat(after.value()).isEqualByComparingTo("5000");
        assertThat(after.avgCost()).isEqualByComparingTo("333.3333");
    }

    @Test
    void receivingIntoNegativeStockResetsTheCostBasis() {
        Balance negative = new Balance(d("-2"), d("50"), d("-100"));
        Balance after = StockMath.receive(negative, d("5"), d("60"));
        assertThat(after.quantity()).isEqualByComparingTo("3");
        assertThat(after.avgCost()).isEqualByComparingTo("60");
        assertThat(after.value()).isEqualByComparingTo("180");
    }

    @Test
    void decimalQuantitiesForMetres() {
        Balance b = StockMath.receive(EMPTY, d("100.5"), d("20"));
        Balance after = StockMath.issue(b, d("2.25"));
        assertThat(after.quantity()).isEqualByComparingTo("98.25");
        assertThat(after.value()).isEqualByComparingTo("1965");
    }
}
