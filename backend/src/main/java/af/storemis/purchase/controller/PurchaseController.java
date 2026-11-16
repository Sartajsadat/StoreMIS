package af.storemis.purchase.controller;

import af.storemis.common.api.ApiResponse;
import af.storemis.common.api.PageRequests;
import af.storemis.common.api.PageResponse;
import af.storemis.purchase.dto.CancelPurchaseRequest;
import af.storemis.purchase.dto.ConfirmPurchaseRequest;
import af.storemis.purchase.dto.PurchaseRequest;
import af.storemis.purchase.dto.PurchaseResponse;
import af.storemis.purchase.entity.PurchaseStatus;
import af.storemis.purchase.service.PurchaseService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/purchases")
@PreAuthorize("@authz.has('RECORD_PURCHASES')")
public class PurchaseController {

    private final PurchaseService purchases;

    public PurchaseController(PurchaseService purchases) {
        this.purchases = purchases;
    }

    @GetMapping
    public ApiResponse<PageResponse<PurchaseResponse>> list(@RequestParam(required = false) String q, @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) PurchaseStatus status, @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String sort) {
        var pageable = PageRequests.of(page, size, sort, Set.of("purchaseDate", "grandTotal", "balanceDue", "createdAt"),
                Sort.by(Sort.Direction.DESC, "purchaseDate").and(Sort.by(Sort.Direction.DESC, "id")));
        return ApiResponse.ok(PageResponse.of(purchases.search(q, supplierId, status, paymentStatus, currency, from, to, pageable), x -> x));
    }

    @GetMapping("/open")
    @PreAuthorize("@authz.hasAny('RECORD_PURCHASES', 'PAY_SUPPLIERS')")
    public ApiResponse<List<PurchaseResponse>> open(@RequestParam Long supplierId) {
        return ApiResponse.ok(purchases.openForSupplier(supplierId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.hasAny('RECORD_PURCHASES', 'PAY_SUPPLIERS')")
    public ApiResponse<PurchaseResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(purchases.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PurchaseResponse> create(@Valid @RequestBody PurchaseRequest request) {
        return ApiResponse.ok(purchases.createDraft(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<PurchaseResponse> update(@PathVariable Long id, @Valid @RequestBody PurchaseRequest request) {
        return ApiResponse.ok(purchases.updateDraft(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        purchases.deleteDraft(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<PurchaseResponse> confirm(@PathVariable Long id, @Valid @RequestBody(required = false) ConfirmPurchaseRequest request) {
        return ApiResponse.ok(purchases.confirm(id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PurchaseResponse> cancel(@PathVariable Long id, @Valid @RequestBody CancelPurchaseRequest request) {
        return ApiResponse.ok(purchases.cancel(id, request));
    }
}
