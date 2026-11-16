package af.storemis.inventory.dto;

import java.math.BigDecimal;
import java.util.List;

public record IntegrityReport(boolean consistent, List<Mismatch> mismatches) {

    public record Mismatch(Long productId, BigDecimal cachedQuantity, BigDecimal ledgerQuantity) {}
}
