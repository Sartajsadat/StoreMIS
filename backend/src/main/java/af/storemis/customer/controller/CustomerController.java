package af.storemis.customer.controller;

import af.storemis.common.api.ApiResponse;
import af.storemis.common.api.PageRequests;
import af.storemis.common.api.PageResponse;
import af.storemis.common.time.BusinessClock;
import af.storemis.supplier.dto.StatementResponse;
import af.storemis.customer.dto.CustomerOpeningBalanceRequest;
import af.storemis.customer.dto.CustomerRequest;
import af.storemis.customer.dto.CustomerResponse;
import af.storemis.customer.service.CustomerService;
import af.storemis.user.dto.StatusRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
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

@RestController
@RequestMapping("/api/v1/customers")
@PreAuthorize("@authz.hasAny('RECORD_PURCHASES', 'PAY_CUSTOMERS')")
public class CustomerController {

    private final CustomerService customers;
    private final BusinessClock clock;

    public CustomerController(CustomerService customers, BusinessClock clock) {
        this.customers = customers;
        this.clock = clock;
    }

    @GetMapping
    public ApiResponse<PageResponse<CustomerResponse>> list(@RequestParam(required = false) String q, @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String type, @RequestParam(defaultValue = "false") boolean withBalance, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String sort) {
        var pageable = PageRequests.of(page, size, sort, Set.of("name", "createdAt"), Sort.by("name"));
        return ApiResponse.ok(PageResponse.of(customers.search(q, active, type, withBalance, pageable), x -> x));
    }

    @GetMapping("/receivables")
    public ApiResponse<Map<String, BigDecimal>> receivables() {
        return ApiResponse.ok(customers.totalReceivables());
    }

    @GetMapping("/walk-in")
    public ApiResponse<CustomerResponse> walkIn() {
        return ApiResponse.ok(customers.describe(customers.walkIn().getId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<CustomerResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(customers.describe(id));
    }

    /** Entry Users may register customers (baseline permission). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        return ApiResponse.ok(customers.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<CustomerResponse> update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return ApiResponse.ok(customers.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CustomerResponse> status(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return ApiResponse.ok(customers.setActive(id, request.active()));
    }

    @PostMapping("/{id}/opening-balance")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CustomerResponse> openingBalance(@PathVariable Long id, @Valid @RequestBody CustomerOpeningBalanceRequest request) {
        return ApiResponse.ok(customers.openingBalance(id, request));
    }

    @GetMapping("/{id}/statement")
    public ApiResponse<StatementResponse> statement(@PathVariable Long id, @RequestParam(defaultValue = "AFN") String currency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? clock.today() : to;
        LocalDate start = from == null ? end.withDayOfYear(1) : from;
        return ApiResponse.ok(customers.statement(id, currency, start, end));
    }
}
