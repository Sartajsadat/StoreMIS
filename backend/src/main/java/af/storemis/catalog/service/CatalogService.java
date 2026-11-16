package af.storemis.catalog.service;

import af.storemis.audit.entity.AuditAction;
import af.storemis.audit.entity.AuditModule;
import af.storemis.audit.service.AuditEvent;
import af.storemis.audit.service.AuditService;
import af.storemis.catalog.dto.CategoryRequest;
import af.storemis.catalog.dto.CategoryResponse;
import af.storemis.catalog.dto.UnitRequest;
import af.storemis.catalog.dto.UnitResponse;
import af.storemis.catalog.entity.Category;
import af.storemis.catalog.entity.Unit;
import af.storemis.catalog.repository.CategoryRepository;
import af.storemis.catalog.repository.UnitRepository;
import af.storemis.common.exception.BusinessException;
import af.storemis.common.exception.ErrorCode;
import af.storemis.common.exception.NotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Categories and units. Neither is ever deleted — deactivated items simply stop appearing in pickers. */
@Service
public class CatalogService {

    private final CategoryRepository categories;
    private final UnitRepository units;
    private final AuditService audit;

    public CatalogService(CategoryRepository categories, UnitRepository units, AuditService audit) {
        this.categories = categories;
        this.units = units;
        this.audit = audit;
    }

    // ---- Categories ----

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories(Boolean active) {
        Map<Long, Long> counts = new HashMap<>();
        categories.countProductsPerCategory().forEach(r -> counts.put((Long) r[0], (Long) r[1]));
        return categories.findAllByOrderByNameAsc().stream().filter(c -> active == null || c.isActive() == active)
                .map(c -> CategoryResponse.from(c, counts.getOrDefault(c.getId(), 0L))).toList();
    }

    @Transactional(readOnly = true)
    public Category getCategory(Long id) {
        return categories.findById(id).orElseThrow(() -> new NotFoundException("Category", id));
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest r) {
        String name = r.name().trim();
        if (categories.existsByNameIgnoreCase(name, null)) throw new BusinessException(ErrorCode.DUPLICATE, Map.of("field", "name"));
        Category c = new Category();
        c.setName(name);
        c.setDescription(blankToNull(r.description()));
        categories.save(c);
        audit.log(AuditEvent.of(AuditAction.CREATE, AuditModule.CATEGORIES, "Category", c.getId(), c.getName(), "Category created"));
        return CategoryResponse.from(c, 0);
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest r) {
        Category c = getCategory(id);
        c.checkVersion(r.version());
        String name = r.name().trim();
        if (categories.existsByNameIgnoreCase(name, id)) throw new BusinessException(ErrorCode.DUPLICATE, Map.of("field", "name"));
        Map<String, Object> details = Map.of("name", Map.of("from", c.getName(), "to", name));
        c.setName(name);
        c.setDescription(blankToNull(r.description()));
        categories.saveAndFlush(c);
        audit.log(AuditEvent.of(AuditAction.UPDATE, AuditModule.CATEGORIES, "Category", c.getId(), c.getName(), "Category updated").withDetails(details));
        return CategoryResponse.from(c, 0);
    }

    @Transactional
    public CategoryResponse setCategoryActive(Long id, boolean active) {
        Category c = getCategory(id);
        if (c.isActive() != active) {
            c.setActive(active);
            audit.log(AuditEvent.of(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE, AuditModule.CATEGORIES, "Category", c.getId(), c.getName(),
                    active ? "Category activated" : "Category deactivated"));
        }
        return CategoryResponse.from(c, 0);
    }

    // ---- Units ----

    @Transactional(readOnly = true)
    public List<UnitResponse> listUnits(Boolean active) {
        return units.findAllByOrderByIdAsc().stream().filter(u -> active == null || u.isActive() == active).map(UnitResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Unit getUnit(Long id) {
        return units.findById(id).orElseThrow(() -> new NotFoundException("Unit", id));
    }

    @Transactional
    public UnitResponse createUnit(UnitRequest r) {
        String name = r.name().trim();
        if (units.existsByNameIgnoreCase(name, null)) throw new BusinessException(ErrorCode.DUPLICATE, Map.of("field", "name"));
        Unit u = new Unit();
        u.setName(name);
        u.setSymbol(blankToNull(r.symbol()));
        u.setAllowDecimal(r.allowDecimal());
        units.save(u);
        audit.log(AuditEvent.of(AuditAction.CREATE, AuditModule.UNITS, "Unit", u.getId(), u.getName(), "Unit created"));
        return UnitResponse.from(u);
    }

    /**
     * Updates a unit. Switching a unit from decimal to whole numbers is allowed; existing fractional stock stays as recorded,
     * and new movements must use whole quantities.
     */
    @Transactional
    public UnitResponse updateUnit(Long id, UnitRequest r) {
        Unit u = getUnit(id);
        u.checkVersion(r.version());
        String name = r.name().trim();
        if (units.existsByNameIgnoreCase(name, id)) throw new BusinessException(ErrorCode.DUPLICATE, Map.of("field", "name"));
        u.setName(name);
        u.setSymbol(blankToNull(r.symbol()));
        u.setAllowDecimal(r.allowDecimal());
        units.saveAndFlush(u);
        audit.log(AuditEvent.of(AuditAction.UPDATE, AuditModule.UNITS, "Unit", u.getId(), u.getName(), "Unit updated"));
        return UnitResponse.from(u);
    }

    @Transactional
    public UnitResponse setUnitActive(Long id, boolean active) {
        Unit u = getUnit(id);
        if (u.isActive() != active) {
            u.setActive(active);
            audit.log(AuditEvent.of(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE, AuditModule.UNITS, "Unit", u.getId(), u.getName(),
                    active ? "Unit activated" : "Unit deactivated"));
        }
        return UnitResponse.from(u);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
