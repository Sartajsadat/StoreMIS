package af.storemis.inventory.dto;

import af.storemis.inventory.entity.ProductStock;
import java.math.BigDecimal;

/** Stock of one product. Cost fields are null unless the user may see cost prices. */
public record StockInfo(BigDecimal quantity, BigDecimal avgCost, BigDecimal value, StockStatus status) {

    public static StockInfo of(ProductStock s, BigDecimal minStockLevel, boolean showCost) {
        BigDecimal qty = s == null ? BigDecimal.ZERO : s.getQuantityOnHand();
        return new StockInfo(qty, showCost && s != null ? s.getAvgUnitCost() : null, showCost && s != null ? s.getInventoryValue() : null,
                StockStatus.of(qty, minStockLevel));
    }
}
