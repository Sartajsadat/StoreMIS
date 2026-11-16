package af.storemis.reporting.dto;

import java.time.LocalDate;

/** Filters shared by all reports; each report uses the ones that make sense for it. */
public record ReportQuery(LocalDate from, LocalDate to, String q, String type, Long categoryId, Long accountId, String status, String currency,
        boolean nonZero, int page, int size) {}
