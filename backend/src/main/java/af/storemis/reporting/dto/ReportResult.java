package af.storemis.reporting.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * One report: column definitions, a page of rows, totals over all matching rows and report-specific extras
 * ({@code meta}, e.g. a breakdown for a chart). Money without a currency field is in {@code baseCurrency}.
 */
public record ReportResult(String key, LocalDate from, LocalDate to, String baseCurrency, List<ReportColumn> columns, List<Map<String, Object>> rows,
        Map<String, Object> totals, long totalRows, int page, int size, Map<String, Object> meta) {}
