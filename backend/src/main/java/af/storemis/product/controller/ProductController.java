package af.storemis.product.controller;

import af.storemis.common.api.ApiResponse;
import af.storemis.common.api.PageResponse;
import af.storemis.inventory.dto.StockStatus;
import af.storemis.inventory.service.InventoryQueryService;
import af.storemis.product.dto.ProductRequest;
import af.storemis.product.dto.ProductResponse;
import af.storemis.product.service.ProductService;
import af.storemis.user.dto.StatusRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import org.springframework.web.multipart.MultipartFile;

/** Everyone signed in can browse and search products (cost prices only with VIEW_COST_PRICES); Admins maintain them. */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService products;
    private final InventoryQueryService queries;

    public ProductController(ProductService products, InventoryQueryService queries) {
        this.products = products;
        this.queries = queries;
    }

    @GetMapping
    public ApiResponse<PageResponse<ProductResponse>> list(@RequestParam(required = false) String q, @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean active, @RequestParam(required = false) StockStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String sort) {
        return ApiResponse.ok(PageResponse.of(queries.searchProducts(q, categoryId, active, status, StockSorts.pageable(page, size, sort)), x -> x));
    }

    @GetMapping("/search")
    public ApiResponse<List<ProductResponse>> search(@RequestParam String q, @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(products.quickSearch(q, limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(products.describe(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ApiResponse.ok(products.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ProductResponse> update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return ApiResponse.ok(products.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ProductResponse> status(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return ApiResponse.ok(products.setActive(id, request.active()));
    }

    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ProductResponse> uploadImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(products.uploadImage(id, file));
    }

    @DeleteMapping("/{id}/image")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ProductResponse> removeImage(@PathVariable Long id) {
        return ApiResponse.ok(products.removeImage(id));
    }
}
