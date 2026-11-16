package af.storemis.product.controller;

import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;

/** Sorting for the product + stock listing; client names map to query aliases (p = product, s = stock). */
public final class StockSorts {

    private static final Map<String, String> ALLOWED = Map.of(
            "name", "p.name", "code", "p.code", "category", "p.category.name", "quantity", "s.quantityOnHand",
            "value", "s.inventoryValue", "createdAt", "p.createdAt", "lastMovementAt", "s.lastMovementAt");

    private StockSorts() {}

    public static Pageable pageable(int page, int size, String sort) {
        Sort.Direction dir = Sort.Direction.ASC;
        String property = "p.name";
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String mapped = ALLOWED.get(parts[0].trim());
            if (mapped != null) {
                property = mapped;
                dir = parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
            }
        }
        Sort s = JpaSort.unsafe(dir, property).and(JpaSort.unsafe(Sort.Direction.ASC, "p.id"));
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), s);
    }
}
