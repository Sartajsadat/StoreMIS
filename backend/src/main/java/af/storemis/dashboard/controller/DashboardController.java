package af.storemis.dashboard.controller;

import af.storemis.common.api.ApiResponse;
import af.storemis.common.exception.BusinessException;
import af.storemis.common.exception.ErrorCode;
import af.storemis.common.time.BusinessClock;
import af.storemis.dashboard.service.DashboardService;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboard;
    private final BusinessClock clock;

    public DashboardController(DashboardService dashboard, BusinessClock clock) {
        this.dashboard = dashboard;
        this.clock = clock;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate[] r = range(from, to);
        return ApiResponse.ok(dashboard.summary(r[0], r[1]));
    }

    @GetMapping("/charts")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> charts(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate[] r = range(from, to);
        return ApiResponse.ok(dashboard.charts(r[0], r[1]));
    }

    @GetMapping("/recent")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> recent() {
        return ApiResponse.ok(dashboard.recent());
    }

    /** Any signed-in user: their own day. */
    @GetMapping("/my-day")
    public ApiResponse<Map<String, Object>> myDay() {
        return ApiResponse.ok(dashboard.myDay());
    }

    /** Defaults to today; at most about ten years. */
    private LocalDate[] range(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? clock.today() : to;
        LocalDate start = from == null ? end : from;
        if (start.isAfter(end) || end.toEpochDay() - start.toEpochDay() > 3_700) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of("field", "from"));
        }
        return new LocalDate[] {start, end};
    }
}
