package af.storemis.inventory.controller;

import af.storemis.common.api.ApiResponse;
import af.storemis.common.api.PageRequests;
import af.storemis.common.api.PageResponse;
import af.storemis.inventory.dto.AdjustmentRequest;
import af.storemis.inventory.dto.AdjustmentResponse;
import af.storemis.inventory.dto.IntegrityReport;
import af.storemis.inventory.dto.InventorySummary;
import af.storemis.inventory.dto.MovementResponse;
import af.storemis.inventory.dto.StockAlerts;
import af.storemis.inventory.dto.StockStatus;
import af.storemis.inventory.entity.AdjustmentType;
import af.storemis.inventory.entity.MovementType;
import af.storemis.inventory.service.AdjustmentService;
import af.storemis.inventory.service.InventoryQueryService;
import af.storemis.product.controller.StockSorts;
import af.storemis.product.dto.ProductResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryQueryService queries;
    private final AdjustmentService adjustments;

    public InventoryController(InventoryQueryService queries, AdjustmentService adjustments) {
        this.queries = queries;
        this.adjustments = adjustments;
    }

    /** Stock overview — quantities for everyone; average cost and value only with VIEW_COST_PRICES. */
    @GetMapping("/stock")
    public ApiResponse<PageResponse<ProductResponse>> stock(@RequestParam(required = false) String q, @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) StockStatus status, @RequestParam(defaultValue = "true") Boolean active,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String sort) {
        return ApiResponse.ok(PageResponse.of(queries.searchProducts(q, categoryId, active, status, StockSorts.pageable(page, size, sort)), x -> x));
    }

    @GetMapping("/summary")
    public ApiResponse<InventorySummary> summary() {
        return ApiResponse.ok(queries.summary());
    }

    /** Low and out-of-stock alerts for the notification bell and the Low Stock page. */
    @GetMapping("/alerts")
    public ApiResponse<StockAlerts> alerts(@RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(queries.alerts(limit));
    }

    @GetMapping("/movements")
    @PreAuthorize("@authz.has('ADJUST_STOCK')")
    public ApiResponse<PageResponse<MovementResponse>> movements(@RequestParam(required = false) Long productId,
            @RequestParam(required = false) MovementType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequests.of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "id"));
        return ApiResponse.ok(PageResponse.of(queries.movements(productId, type, from, to, q, pageable), x -> x));
    }

    @GetMapping("/adjustments")
    @PreAuthorize("@authz.has('ADJUST_STOCK')")
    public ApiResponse<PageResponse<AdjustmentResponse>> adjustments(@RequestParam(required = false) Long productId,
            @RequestParam(required = false) AdjustmentType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequests.of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "id"));
        return ApiResponse.ok(PageResponse.of(adjustments.search(productId, type, from, to, q, pageable), x -> x));
    }

    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authz.has('ADJUST_STOCK')")
    public ApiResponse<AdjustmentResponse> createAdjustment(@Valid @RequestBody AdjustmentRequest request) {
        return ApiResponse.ok(adjustments.create(request));
    }

    @GetMapping("/integrity-check")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<IntegrityReport> integrityCheck() {
        return ApiResponse.ok(queries.integrityCheck());
    }
}
