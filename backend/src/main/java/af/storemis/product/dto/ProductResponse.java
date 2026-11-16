package af.storemis.product.dto;

import af.storemis.catalog.dto.UnitResponse;
import af.storemis.inventory.dto.StockInfo;
import af.storemis.product.entity.Product;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ProductResponse(Long id, String name, String code, CategoryRef category, UnitResponse unit, String brand, String description,
        BigDecimal minStockLevel, String imageUrl, boolean active, StockInfo stock, OffsetDateTime createdAt, int version) {

    public record CategoryRef(Long id, String name) {}

    public static ProductResponse from(Product p, StockInfo stock) {
        return new ProductResponse(p.getId(), p.getName(), p.getCode(), new CategoryRef(p.getCategory().getId(), p.getCategory().getName()),
                UnitResponse.from(p.getUnit()), p.getBrand(), p.getDescription(), p.getMinStockLevel(), imageUrl(p), p.isActive(), stock,
                p.getCreatedAt(), p.getVersion());
    }

    public static String imageUrl(Product p) {
        return p.getImagePath() == null ? null : "/api/v1/files/products/" + p.getImagePath();
    }
}
