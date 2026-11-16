package af.storemis.inventory.dto;

import java.math.BigDecimal;
import java.util.List;

/** For the notification bell: counts plus the most urgent products. */
public record StockAlerts(long lowStock, long outOfStock, List<Item> items) {

    public record Item(Long productId, String name, String code, BigDecimal quantity, BigDecimal minStockLevel, String unitCode, String unitName,
            StockStatus status) {}
}
