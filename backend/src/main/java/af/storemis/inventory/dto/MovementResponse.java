package af.storemis.inventory.dto;

import af.storemis.inventory.entity.MovementType;
import af.storemis.inventory.entity.StockSourceType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** A ledger line. Cost fields are null unless the user may see cost prices. */
public record MovementResponse(Long id, Long productId, String productName, String productCode, String unitCode, String unitName, MovementType type,
        BigDecimal quantity, BigDecimal unitCost, BigDecimal totalCost, BigDecimal quantityAfter, BigDecimal avgCostAfter, BigDecimal valueAfter,
        StockSourceType sourceType, Long sourceId, String referenceNo, LocalDate businessDate, String notes, OffsetDateTime createdAt,
        String createdByName) {}
