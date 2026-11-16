package af.storemis.inventory.dto;

import java.math.BigDecimal;

/** inventoryValue is null unless the user may see cost prices. */
public record InventorySummary(long activeProducts, long lowStock, long outOfStock, BigDecimal inventoryValue, String baseCurrency) {}
