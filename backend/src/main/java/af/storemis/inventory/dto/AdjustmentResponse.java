package af.storemis.inventory.dto;

import af.storemis.inventory.entity.AdjustmentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record AdjustmentResponse(Long id, String adjustmentNo, Long productId, String productName, String productCode, String unitCode, String unitName,
        AdjustmentType type, BigDecimal quantity, BigDecimal unitCost, BigDecimal totalCost, String reason, LocalDate businessDate,
        OffsetDateTime createdAt, String createdByName) {}
