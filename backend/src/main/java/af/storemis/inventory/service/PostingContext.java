package af.storemis.inventory.service;

import af.storemis.inventory.entity.StockSourceType;
import java.time.LocalDate;

/** The document a stock movement belongs to. */
public record PostingContext(StockSourceType sourceType, Long sourceId, String referenceNo, LocalDate businessDate, String notes) {}
