package af.storemis.payment.controller;

import af.storemis.common.api.ApiResponse;
import af.storemis.common.api.PageRequests;
import af.storemis.common.api.PageResponse;
import af.storemis.common.security.AppPrincipal;
import af.storemis.common.security.Permission;
import af.storemis.common.security.SecurityUtils;
import af.storemis.payment.dto.CustomerPaymentRequest;
import af.storemis.payment.dto.CustomerPaymentResponse;
import af.storemis.payment.dto.ReverseRequest;
import af.storemis.payment.entity.PaymentStatus;
import af.storemis.payment.service.CustomerPaymentService;
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
@RequestMapping("/api/v1/customer-payments")
@PreAuthorize("@authz.has('RECEIVE_CUSTOMER_PAYMENTS')")
public class CustomerPaymentController {

    private final CustomerPaymentService payments;

    public CustomerPaymentController(CustomerPaymentService payments) {
        this.payments = payments;
    }

    /** Entry Users see the payments they received unless they may view all sales. */
    @GetMapping
    public ApiResponse<PageResponse<CustomerPaymentResponse>> list(@RequestParam(required = false) Long customerId,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        AppPrincipal me = SecurityUtils.requirePrincipal();
        Long onlyUser = me.has(Permission.VIEW_ALL_SALES) ? null : me.id();
        var pageable = PageRequests.of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "id"));
        return ApiResponse.ok(PageResponse.of(payments.search(customerId, status, from, to, q, onlyUser, pageable), x -> x));
    }

    @GetMapping("/{id}")
    public ApiResponse<CustomerPaymentResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(payments.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CustomerPaymentResponse> create(@Valid @RequestBody CustomerPaymentRequest request) {
        return ApiResponse.ok(payments.collect(request));
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CustomerPaymentResponse> reverse(@PathVariable Long id, @Valid @RequestBody ReverseRequest request) {
        return ApiResponse.ok(payments.reverse(id, request.reason()));
    }
}
