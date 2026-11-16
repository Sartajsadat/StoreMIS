package af.storemis.cash.controller;

import af.storemis.cash.dto.CashAccountResponse;
import af.storemis.cash.dto.CashTransactionResponse;
import af.storemis.cash.dto.OpeningBalanceRequest;
import af.storemis.cash.entity.CashTxnType;
import af.storemis.cash.service.CashAccountService;
import af.storemis.common.api.ApiResponse;
import af.storemis.common.api.PageRequests;
import af.storemis.common.api.PageResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CashController {

    private final CashAccountService cash;

    public CashController(CashAccountService cash) {
        this.cash = cash;
    }

    /** Admins get balances; others get the list of accounts for payment pickers. */
    @GetMapping("/cash-accounts")
    public ApiResponse<List<CashAccountResponse>> accounts() {
        return ApiResponse.ok(cash.list());
    }

    @PostMapping("/cash-accounts/{id}/opening-balance")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CashAccountResponse> openingBalance(@PathVariable Long id, @Valid @RequestBody OpeningBalanceRequest request) {
        return ApiResponse.ok(cash.setOpeningBalance(id, request.amount()));
    }

    @GetMapping("/cash-transactions")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<CashTransactionResponse>> transactions(@RequestParam(required = false) Long accountId,
            @RequestParam(required = false) CashTxnType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequests.of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "id"));
        return ApiResponse.ok(PageResponse.of(cash.transactions(accountId, type, from, to, q, pageable), x -> x));
    }
}
