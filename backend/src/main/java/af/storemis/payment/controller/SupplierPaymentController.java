package af.storemis.payment.controller;

import af.storemis.common.api.ApiResponse;
import af.storemis.common.api.PageRequests;
import af.storemis.common.api.PageResponse;
import af.storemis.payment.dto.ReverseRequest;
import af.storemis.payment.dto.SupplierPaymentRequest;
import af.storemis.payment.dto.SupplierPaymentResponse;
import af.storemis.payment.entity.PaymentStatus;
import af.storemis.payment.service.SupplierPaymentService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/supplier-payments")
@PreAuthorize("@authz.has('PAY_SUPPLIERS')")
public class SupplierPaymentController {

    private final SupplierPaymentService payments;

    public SupplierPaymentController(SupplierPaymentService payments) {
        this.payments = payments;
    }

    @GetMapping
    public ApiResponse<PageResponse<SupplierPaymentResponse>> list(@RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequests.of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "id"));
        return ApiResponse.ok(PageResponse.of(payments.search(supplierId, status, from, to, q, pageable), x -> x));
    }

    @GetMapping("/{id}")
    public ApiResponse<SupplierPaymentResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(payments.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SupplierPaymentResponse> create(@Valid @RequestBody SupplierPaymentRequest request) {
        return ApiResponse.ok(payments.create(request));
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SupplierPaymentResponse> reverse(@PathVariable Long id, @Valid @RequestBody ReverseRequest request) {
        return ApiResponse.ok(payments.reverse(id, request.reason()));
    }
}
