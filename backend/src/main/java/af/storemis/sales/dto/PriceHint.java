package af.storemis.sales.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Shown next to the price field in POS. avgCost is null unless the user may see cost prices. */
public record PriceHint(BigDecimal lastPrice, String lastCurrency, OffsetDateTime lastDate, BigDecimal lastPriceForCustomer, String lastCurrencyForCustomer,
        OffsetDateTime lastDateForCustomer, BigDecimal avgCost, String baseCurrency) {}
