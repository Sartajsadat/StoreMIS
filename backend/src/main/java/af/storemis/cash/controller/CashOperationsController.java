package af.storemis.cash.controller;

import af.storemis.cash.dto.CashTransactionResponse;
import af.storemis.cash.dto.CashTransferRequest;
import af.storemis.cash.dto.CashTransferResponse;
import af.storemis.cash.dto.CloseDayRequest;
import af.storemis.cash.dto.ClosingPreview;
import af.storemis.cash.dto.ClosingRow;
import af.storemis.cash.dto.OwnerCashRequest;
import af.storemis.cash.dto.ReasonRequest;
import af.storemis.cash.service.CashClosingService;
import af.storemis.cash.service.CashOperationsService;
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

/** Admin cash operations: owner deposits/withdrawals, transfers, currency exchange and the daily closing. */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('ADMIN')")
public class CashOperationsController {

    private final CashOperationsService operations;
    private final CashClosingService closing;

    public CashOperationsController(CashOperationsService operations, CashClosingService closing) {
        this.operations = operations;
        this.closing = closing;
    }

    @PostMapping("/cash-transactions/deposit")
    public ApiResponse<CashTransactionResponse> deposit(@Valid @RequestBody OwnerCashRequest r) {
        return ApiResponse.ok(operations.ownerCash(r, true));
    }

    @PostMapping("/cash-transactions/withdrawal")
    public ApiResponse<CashTransactionResponse> withdrawal(@Valid @RequestBody OwnerCashRequest r) {
        return ApiResponse.ok(operations.ownerCash(r, false));
    }

    @PostMapping("/cash-transfers")
    public ApiResponse<CashTransferResponse> transfer(@Valid @RequestBody CashTransferRequest r) {
        return ApiResponse.ok(operations.transfer(r));
    }

    @GetMapping("/cash-transfers")
    public ApiResponse<PageResponse<CashTransferResponse>> transfers(@RequestParam(required = false) String type,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequests.of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "id"));
        return ApiResponse.ok(PageResponse.of(operations.transfers(type, accountId, from, to, pageable), x -> x));
    }

    @GetMapping("/cash-closings/preview")
    public ApiResponse<ClosingPreview> preview(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(closing.preview(date));
    }

    @PostMapping("/cash-closings")
    public ApiResponse<ClosingPreview> close(@Valid @RequestBody CloseDayRequest r) {
        return ApiResponse.ok(closing.close(r));
    }

    @PostMapping("/cash-closings/{date}/reopen")
    public ApiResponse<ClosingPreview> reopen(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody ReasonRequest r) {
        return ApiResponse.ok(closing.reopen(date, r.reason()));
    }

    @GetMapping("/cash-closings")
    public ApiResponse<List<ClosingRow>> history(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(closing.history(from, to));
    }
}
