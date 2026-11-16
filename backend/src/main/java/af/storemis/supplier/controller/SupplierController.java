package af.storemis.supplier.controller;

import af.storemis.common.api.ApiResponse;
import af.storemis.common.api.PageRequests;
import af.storemis.common.api.PageResponse;
import af.storemis.common.time.BusinessClock;
import af.storemis.supplier.dto.StatementResponse;
import af.storemis.supplier.dto.SupplierOpeningBalanceRequest;
import af.storemis.supplier.dto.SupplierRequest;
import af.storemis.supplier.dto.SupplierResponse;
import af.storemis.supplier.service.SupplierService;
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
@RequestMapping("/api/v1/suppliers")
@PreAuthorize("@authz.hasAny('RECORD_PURCHASES', 'PAY_SUPPLIERS')")
public class SupplierController {

    private final SupplierService suppliers;
    private final BusinessClock clock;

    public SupplierController(SupplierService suppliers, BusinessClock clock) {
        this.suppliers = suppliers;
        this.clock = clock;
    }

    @GetMapping
    public ApiResponse<PageResponse<SupplierResponse>> list(@RequestParam(required = false) String q, @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "false") boolean withBalance, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String sort) {
        var pageable = PageRequests.of(page, size, sort, Set.of("name", "createdAt"), Sort.by("name"));
        return ApiResponse.ok(PageResponse.of(suppliers.search(q, active, withBalance, pageable), x -> x));
    }

    @GetMapping("/payables")
    public ApiResponse<Map<String, BigDecimal>> payables() {
        return ApiResponse.ok(suppliers.totalPayables());
    }

    @GetMapping("/{id}")
    public ApiResponse<SupplierResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(suppliers.describe(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authz.has('RECORD_PURCHASES')")
    public ApiResponse<SupplierResponse> create(@Valid @RequestBody SupplierRequest request) {
        return ApiResponse.ok(suppliers.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.has('RECORD_PURCHASES')")
    public ApiResponse<SupplierResponse> update(@PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        return ApiResponse.ok(suppliers.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SupplierResponse> status(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return ApiResponse.ok(suppliers.setActive(id, request.active()));
    }

    @PostMapping("/{id}/opening-balance")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SupplierResponse> openingBalance(@PathVariable Long id, @Valid @RequestBody SupplierOpeningBalanceRequest request) {
        return ApiResponse.ok(suppliers.openingBalance(id, request));
    }

    @GetMapping("/{id}/statement")
    public ApiResponse<StatementResponse> statement(@PathVariable Long id, @RequestParam(defaultValue = "AFN") String currency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? clock.today() : to;
        LocalDate start = from == null ? end.withDayOfYear(1) : from;
        return ApiResponse.ok(suppliers.statement(id, currency, start, end));
    }
}
