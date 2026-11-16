package af.storemis.currency.controller;

import af.storemis.common.api.ApiResponse;
import af.storemis.common.api.PageRequests;
import af.storemis.common.api.PageResponse;
import af.storemis.currency.dto.RateRequest;
import af.storemis.currency.dto.RateResponse;
import af.storemis.currency.service.CurrencyService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Sort;
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
@RequestMapping("/api/v1/exchange-rates")
public class CurrencyController {

    private final CurrencyService currency;

    public CurrencyController(CurrencyService currency) {
        this.currency = currency;
    }

    /** Current rates for document defaults — every signed-in user. */
    @GetMapping("/current")
    public ApiResponse<Map<String, Object>> current() {
        return ApiResponse.ok(currency.currentRates());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<RateResponse>> history(@RequestParam(defaultValue = "USD") String currency,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.of(this.currency.history(currency, PageRequests.of(page, size, null, Set.of(), Sort.unsorted())), x -> x));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<RateResponse> setRate(@Valid @RequestBody RateRequest request) {
        return ApiResponse.ok(currency.setRate(request));
    }
}
