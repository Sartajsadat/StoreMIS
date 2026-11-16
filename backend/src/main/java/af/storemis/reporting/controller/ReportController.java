package af.storemis.reporting.controller;

import af.storemis.common.api.ApiResponse;
import af.storemis.common.exception.BusinessException;
import af.storemis.common.exception.ErrorCode;
import af.storemis.common.time.BusinessClock;
import af.storemis.reporting.dto.ReportQuery;
import af.storemis.reporting.dto.ReportResult;
import af.storemis.reporting.service.ReportService;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /reports/{key}}. Without dates a report covers the current month. {@code size} may go up to 10,000
 * rows so the browser can build a complete Excel or print version.
 */
@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasRole('ADMIN')")
public class ReportController {

    private static final int MAX_SIZE = 10_000;
    private static final int MAX_DAYS = 3_700;

    private final ReportService reports;
    private final BusinessClock clock;

    public ReportController(ReportService reports, BusinessClock clock) {
        this.reports = reports;
        this.clock = clock;
    }

    @GetMapping("/{key}")
    public ApiResponse<ReportResult> run(@PathVariable String key,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q, @RequestParam(required = false) String type, @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long accountId, @RequestParam(required = false) String status,
            @RequestParam(required = false) String currency, @RequestParam(defaultValue = "false") boolean nonZero,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        LocalDate end = to == null ? clock.today() : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        if (start.isAfter(end) || end.toEpochDay() - start.toEpochDay() > MAX_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("field", "from"));
        }
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        return ApiResponse.ok(reports.run(key, new ReportQuery(start, end, q, type, categoryId, accountId, status, currency, nonZero, Math.max(page, 0),
                safeSize)));
    }
}
