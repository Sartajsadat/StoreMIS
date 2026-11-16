package af.storemis.sales.controller;

import af.storemis.common.api.ApiResponse;
import af.storemis.common.api.PageRequests;
import af.storemis.common.api.PageResponse;
import af.storemis.sales.dto.CancelSaleRequest;
import af.storemis.sales.dto.ConfirmDraftRequest;
import af.storemis.sales.dto.PriceHint;
import af.storemis.sales.dto.ReceiptResponse;
import af.storemis.sales.dto.SaleRequest;
import af.storemis.sales.dto.SaleResponse;
import af.storemis.sales.entity.SaleStatus;
import af.storemis.sales.service.SalesService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

/** Selling is part of every user's baseline; cancelling is Admin-only. */
@RestController
@RequestMapping("/api/v1/sales")
public class SalesController {

    private final SalesService sales;

    public SalesController(SalesService sales) {
        this.sales = sales;
    }

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SaleResponse> confirm(@Valid @RequestBody SaleRequest request) {
        return ApiResponse.ok(sales.confirmNew(request));
    }

    @GetMapping("/drafts")
    public ApiResponse<List<SaleResponse>> drafts() {
        return ApiResponse.ok(sales.myDrafts());
    }

    @PostMapping("/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SaleResponse> createDraft(@Valid @RequestBody SaleRequest request) {
        return ApiResponse.ok(sales.createDraft(request));
    }

    @PutMapping("/drafts/{id}")
    public ApiResponse<SaleResponse> updateDraft(@PathVariable Long id, @Valid @RequestBody SaleRequest request) {
        return ApiResponse.ok(sales.updateDraft(id, request));
    }

    @DeleteMapping("/drafts/{id}")
    public ApiResponse<Void> deleteDraft(@PathVariable Long id) {
        sales.deleteDraft(id);
        return ApiResponse.ok();
    }

    @PostMapping("/drafts/{id}/confirm")
    public ApiResponse<SaleResponse> confirmDraft(@PathVariable Long id, @Valid @RequestBody ConfirmDraftRequest request) {
        return ApiResponse.ok(sales.confirmDraft(id, request));
    }

    @GetMapping
    public ApiResponse<PageResponse<SaleResponse>> list(@RequestParam(required = false) String q, @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) SaleStatus status, @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String saleType, @RequestParam(required = false) String currency, @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String sort) {
        var pageable = PageRequests.of(page, size, sort, Set.of("saleDate", "grandTotal", "balanceDue"), Sort.by(Sort.Direction.DESC, "id"));
        return ApiResponse.ok(PageResponse.of(sales.search(q, customerId, status, paymentStatus, saleType, currency, userId, from, to, pageable), x -> x));
    }

    @GetMapping("/open")
    public ApiResponse<List<SaleResponse>> open(@RequestParam Long customerId) {
        return ApiResponse.ok(sales.openForCustomer(customerId));
    }

    @GetMapping("/customer-totals")
    public ApiResponse<Map<String, BigDecimal>> customerTotals(@RequestParam Long customerId) {
        return ApiResponse.ok(sales.totalsForCustomer(customerId));
    }

    @GetMapping("/price-hint")
    public ApiResponse<PriceHint> priceHint(@RequestParam Long productId, @RequestParam(required = false) Long customerId) {
        return ApiResponse.ok(sales.priceHint(productId, customerId));
    }

    @GetMapping("/{id}")
    public ApiResponse<SaleResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(sales.get(id));
    }

    @GetMapping("/{id}/receipt")
    public ApiResponse<ReceiptResponse> receipt(@PathVariable Long id) {
        return ApiResponse.ok(sales.receipt(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SaleResponse> cancel(@PathVariable Long id, @Valid @RequestBody CancelSaleRequest request) {
        return ApiResponse.ok(sales.cancel(id, request));
    }
}
