package af.storemis.catalog.controller;

import af.storemis.catalog.dto.CategoryRequest;
import af.storemis.catalog.dto.CategoryResponse;
import af.storemis.catalog.dto.UnitRequest;
import af.storemis.catalog.dto.UnitResponse;
import af.storemis.catalog.service.CatalogService;
import af.storemis.common.api.ApiResponse;
import af.storemis.user.dto.StatusRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Everyone signed in can read categories and units (pickers); only Admins change them. */
@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/categories")
    public ApiResponse<List<CategoryResponse>> categories(@RequestParam(required = false) Boolean active) {
        return ApiResponse.ok(catalog.listCategories(active));
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok(catalog.createCategory(request));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CategoryResponse> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok(catalog.updateCategory(id, request));
    }

    @PatchMapping("/categories/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CategoryResponse> categoryStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return ApiResponse.ok(catalog.setCategoryActive(id, request.active()));
    }

    @GetMapping("/units")
    public ApiResponse<List<UnitResponse>> units(@RequestParam(required = false) Boolean active) {
        return ApiResponse.ok(catalog.listUnits(active));
    }

    @PostMapping("/units")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UnitResponse> createUnit(@Valid @RequestBody UnitRequest request) {
        return ApiResponse.ok(catalog.createUnit(request));
    }

    @PutMapping("/units/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UnitResponse> updateUnit(@PathVariable Long id, @Valid @RequestBody UnitRequest request) {
        return ApiResponse.ok(catalog.updateUnit(id, request));
    }

    @PatchMapping("/units/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UnitResponse> unitStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return ApiResponse.ok(catalog.setUnitActive(id, request.active()));
    }
}
