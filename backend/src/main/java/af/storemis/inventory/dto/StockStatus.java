package af.storemis.inventory.dto;

import java.math.BigDecimal;

/** Matches the frontend status keys (status.IN_STOCK …). */
public enum StockStatus {
    IN_STOCK,
    LOW_STOCK,
    OUT_OF_STOCK;

    public static StockStatus of(BigDecimal quantity, BigDecimal minStockLevel) {
        if (quantity.signum() <= 0) return OUT_OF_STOCK;
        if (minStockLevel != null && minStockLevel.signum() > 0 && quantity.compareTo(minStockLevel) <= 0) return LOW_STOCK;
        return IN_STOCK;
    }
}
