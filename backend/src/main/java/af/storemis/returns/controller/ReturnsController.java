package af.storemis.returns.controller;

import af.storemis.common.api.ApiResponse;
import af.storemis.common.api.PageRequests;
import af.storemis.common.api.PageResponse;
import af.storemis.returns.dto.ReturnRequest;
import af.storemis.returns.dto.ReturnResponse;
import af.storemis.returns.dto.ReturnableDocument;
import af.storemis.returns.service.PurchaseReturnService;
import af.storemis.returns.service.SalesReturnService;
import af.storemis.sales.service.SalesService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
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

/** Sales returns (PROCESS_SALES_RETURNS) and purchase returns (Admin). */
@RestController
@RequestMapping("/api/v1")
public class ReturnsController {

    private static final String SALES = "@authz.has('PROCESS_SALES_RETURNS')";
    private static final String PURCHASES = "hasRole('ADMIN')";

    private final SalesReturnService salesReturns;
    private final PurchaseReturnService purchaseReturns;
    private final SalesService sales;

    public ReturnsController(SalesReturnService salesReturns, PurchaseReturnService purchaseReturns, SalesService sales) {
        this.sales = sales;
        this.salesReturns = salesReturns;
        this.purchaseReturns = purchaseReturns;
    }

    // ---------------------------------------------------------------- sales returns

    @GetMapping("/sales/{id}/returnable-items")
    @PreAuthorize(SALES)
    public ApiResponse<ReturnableDocument> saleReturnable(@PathVariable Long id) {
        return ApiResponse.ok(salesReturns.returnable(id));
    }

    @GetMapping("/sales-returns/lookup")
    @PreAuthorize(SALES)
    public ApiResponse<ReturnableDocument> saleLookup(@RequestParam String documentNo) {
        return ApiResponse.ok(salesReturns.lookup(documentNo));
    }

    @PostMapping("/sales-returns")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SALES)
    public ApiResponse<ReturnResponse> createSalesReturn(@Valid @RequestBody ReturnRequest r) {
        return ApiResponse.ok(salesReturns.create(r));
    }

    @GetMapping("/sales-returns")
    @PreAuthorize(SALES)
    public ApiResponse<PageResponse<ReturnResponse>> salesReturns(@RequestParam(required = false) String q,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequests.of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "id"));
        return ApiResponse.ok(PageResponse.of(salesReturns.search(q, customerId, from, to, pageable), x -> x));
    }

    @GetMapping("/sales-returns/{id}")
    @PreAuthorize(SALES)
    public ApiResponse<ReturnResponse> salesReturn(@PathVariable Long id) {
        return ApiResponse.ok(salesReturns.get(id));
    }

    /** Returns of one sale — shown on the sale page to anyone who may open the sale. */
    @GetMapping("/sales/{id}/returns")
    public ApiResponse<List<ReturnResponse>> returnsOfSale(@PathVariable Long id) {
        sales.get(id); // same visibility as the sale itself
        return ApiResponse.ok(salesReturns.forSale(id));
    }

    // ---------------------------------------------------------------- purchase returns

    @GetMapping("/purchases/{id}/returnable-items")
    @PreAuthorize(PURCHASES)
    public ApiResponse<ReturnableDocument> purchaseReturnable(@PathVariable Long id) {
        return ApiResponse.ok(purchaseReturns.returnable(id));
    }

    @GetMapping("/purchase-returns/lookup")
    @PreAuthorize(PURCHASES)
    public ApiResponse<ReturnableDocument> purchaseLookup(@RequestParam String documentNo) {
        return ApiResponse.ok(purchaseReturns.lookup(documentNo));
    }

    @PostMapping("/purchase-returns")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(PURCHASES)
    public ApiResponse<ReturnResponse> createPurchaseReturn(@Valid @RequestBody ReturnRequest r) {
        return ApiResponse.ok(purchaseReturns.create(r));
    }

    @GetMapping("/purchase-returns")
    @PreAuthorize(PURCHASES)
    public ApiResponse<PageResponse<ReturnResponse>> purchaseReturns(@RequestParam(required = false) String q,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequests.of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "id"));
        return ApiResponse.ok(PageResponse.of(purchaseReturns.search(q, supplierId, from, to, pageable), x -> x));
    }

    @GetMapping("/purchase-returns/{id}")
    @PreAuthorize(PURCHASES)
    public ApiResponse<ReturnResponse> purchaseReturn(@PathVariable Long id) {
        return ApiResponse.ok(purchaseReturns.get(id));
    }

    @GetMapping("/purchases/{id}/returns")
    @PreAuthorize("@authz.has('RECORD_PURCHASES')")
    public ApiResponse<List<ReturnResponse>> returnsOfPurchase(@PathVariable Long id) {
        return ApiResponse.ok(purchaseReturns.forPurchase(id));
    }
}
