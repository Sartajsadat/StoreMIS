package af.storemis.product.service;

import af.storemis.audit.entity.AuditAction;
import af.storemis.audit.entity.AuditModule;
import af.storemis.audit.service.AuditEvent;
import af.storemis.audit.service.AuditService;
import af.storemis.catalog.entity.Category;
import af.storemis.catalog.entity.Unit;
import af.storemis.catalog.service.CatalogService;
import af.storemis.common.exception.BusinessException;
import af.storemis.common.exception.ErrorCode;
import af.storemis.common.exception.NotFoundException;
import af.storemis.common.security.SecurityUtils;
import af.storemis.files.service.FileStorageService;
import af.storemis.files.service.FileStorageService.Area;
import af.storemis.inventory.entity.AdjustmentType;
import af.storemis.inventory.service.AdjustmentService;
import af.storemis.inventory.service.InventoryPostingService;
import af.storemis.inventory.service.InventoryQueryService;
import af.storemis.product.dto.ProductRequest;
import af.storemis.product.dto.ProductResponse;
import af.storemis.product.entity.Product;
import af.storemis.product.repository.ProductRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductService {

    private final ProductRepository products;
    private final CatalogService catalog;
    private final InventoryPostingService posting;
    private final InventoryQueryService queries;
    private final AdjustmentService adjustments;
    private final FileStorageService files;
    private final AuditService audit;

    public ProductService(ProductRepository products, CatalogService catalog, InventoryPostingService posting, InventoryQueryService queries,
            AdjustmentService adjustments, FileStorageService files, AuditService audit) {
        this.products = products;
        this.catalog = catalog;
        this.posting = posting;
        this.queries = queries;
        this.adjustments = adjustments;
        this.files = files;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Product get(Long id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Product", id));
    }

    @Transactional(readOnly = true)
    public ProductResponse describe(Long id) {
        return queries.describe(get(id));
    }

    /** POS / picker search: active products only, best matches first. */
    @Transactional(readOnly = true)
    public List<ProductResponse> quickSearch(String q, int limit) {
        if (q == null || q.isBlank()) return List.of();
        String text = q.trim();
        String escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return queries.describeAll(products.quickSearch(text, "%" + escaped + "%", escaped + "%", Math.min(Math.max(limit, 1), 50)));
    }

    @Transactional
    public ProductResponse create(ProductRequest r) {
        Product p = new Product();
        apply(p, r, null);
        products.save(p);
        posting.initialize(p.getId());
        audit.log(AuditEvent.of(AuditAction.CREATE, AuditModule.PRODUCTS, "Product", p.getId(), refOf(p), "Product created"));

        boolean hasOpening = r.openingQuantity() != null && r.openingQuantity().signum() > 0;
        if (hasOpening) {
            if (!SecurityUtils.requirePrincipal().isAdmin()) throw new AccessDeniedException("Opening stock is admin-only");
            if (r.openingUnitCost() == null) throw new BusinessException(ErrorCode.UNIT_COST_REQUIRED);
            adjustments.post(p, AdjustmentType.OPENING, r.openingQuantity(), r.openingUnitCost(), "Opening stock", null);
        }
        return queries.describe(p);
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest r) {
        Product p = get(id);
        p.checkVersion(r.version());
        Map<String, Object> before = snapshot(p);
        apply(p, r, id);
        products.saveAndFlush(p);
        audit.log(AuditEvent.of(AuditAction.UPDATE, AuditModule.PRODUCTS, "Product", p.getId(), refOf(p), "Product updated")
                .withDetails(diff(before, snapshot(p))));
        return queries.describe(p);
    }

    @Transactional
    public ProductResponse setActive(Long id, boolean active) {
        Product p = get(id);
        if (p.isActive() != active) {
            p.setActive(active);
            audit.log(AuditEvent.of(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE, AuditModule.PRODUCTS, "Product", p.getId(), refOf(p),
                    active ? "Product activated" : "Product deactivated"));
        }
        return queries.describe(p);
    }

    @Transactional
    public ProductResponse uploadImage(Long id, MultipartFile file) {
        Product p = get(id);
        String stored = files.store(Area.PRODUCTS, file, FileStorageService.IMAGE_TYPES);
        String old = p.getImagePath();
        p.setImagePath(stored);
        products.saveAndFlush(p);
        if (old != null) files.delete(Area.PRODUCTS, old);
        audit.log(AuditEvent.of(AuditAction.UPLOAD, AuditModule.PRODUCTS, "Product", p.getId(), refOf(p), "Product image uploaded"));
        return queries.describe(p);
    }

    @Transactional
    public ProductResponse removeImage(Long id) {
        Product p = get(id);
        if (p.getImagePath() != null) {
            files.delete(Area.PRODUCTS, p.getImagePath());
            p.setImagePath(null);
            products.saveAndFlush(p);
        }
        return queries.describe(p);
    }

    private void apply(Product p, ProductRequest r, Long excludeId) {
        String code = blankToNull(r.code());
        if (code != null && products.existsByCodeIgnoreCase(code, excludeId)) {
            throw new BusinessException(ErrorCode.DUPLICATE, Map.of("field", "code"));
        }
        Category category = catalog.getCategory(r.categoryId());
        Unit unit = catalog.getUnit(r.unitId());
        // Inactive categories/units cannot be newly chosen, but a product may keep the one it already has.
        if (!category.isActive() && (p.getCategory() == null || !p.getCategory().getId().equals(category.getId()))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("field", "categoryId"));
        }
        if (!unit.isActive() && (p.getUnit() == null || !p.getUnit().getId().equals(unit.getId()))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("field", "unitId"));
        }
        p.setName(r.name().trim());
        p.setCode(code);
        p.setCategory(category);
        p.setUnit(unit);
        p.setBrand(blankToNull(r.brand()));
        p.setDescription(blankToNull(r.description()));
        p.setMinStockLevel(r.minStockLevel() == null || r.minStockLevel().signum() == 0 ? null : r.minStockLevel());
    }

    private static String refOf(Product p) {
        String ref = p.getCode() != null ? p.getCode() : p.getName();
        return ref.length() > 50 ? ref.substring(0, 50) : ref;
    }

    private static Map<String, Object> snapshot(Product p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.getName());
        m.put("code", p.getCode());
        m.put("category", p.getCategory().getName());
        m.put("unit", p.getUnit().getName());
        m.put("brand", p.getBrand());
        m.put("minStockLevel", p.getMinStockLevel() == null ? null : p.getMinStockLevel().stripTrailingZeros().toPlainString());
        return m;
    }

    private static Map<String, Object> diff(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> changes = new LinkedHashMap<>();
        after.forEach((k, v) -> {
            if (!Objects.equals(before.get(k), v)) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("from", before.get(k));
                c.put("to", v);
                changes.put(k, c);
            }
        });
        return changes;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
