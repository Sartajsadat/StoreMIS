package af.storemis.currency.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** rate = base-currency units per 1 unit of currency (e.g. 1 USD = 70.5 AFN). */
public record RateResponse(Long id, String currency, String baseCurrency, BigDecimal rate, OffsetDateTime effectiveAt, String notes,
        String createdByName) {}
