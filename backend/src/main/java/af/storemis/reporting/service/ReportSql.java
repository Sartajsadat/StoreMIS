package af.storemis.reporting.service;

import af.storemis.common.money.Money;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Read-only SQL helpers for reports: paging, totals and JSON-friendly values (dates, money). */
@Component
public class ReportSql {

    private final NamedParameterJdbcTemplate jdbc;

    public ReportSql(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> list(String sql, MapSqlParameterSource params) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) out.add(normalize(r));
        return out;
    }

    public Map<String, Object> one(String sql, MapSqlParameterSource params) {
        List<Map<String, Object>> rows = list(sql, params);
        return rows.isEmpty() ? new LinkedHashMap<>() : rows.get(0);
    }

    public BigDecimal decimal(String sql, MapSqlParameterSource params) {
        BigDecimal v = jdbc.queryForObject(sql, params, BigDecimal.class);
        return v == null ? Money.ZERO : Money.amount(v);
    }

    public long count(String sql, MapSqlParameterSource params) {
        Long v = jdbc.queryForObject("select count(*) from (" + sql + ") x", params, Long.class);
        return v == null ? 0 : v;
    }

    /** One page of {@code sql} in the given order. */
    public List<Map<String, Object>> page(String sql, String orderBy, MapSqlParameterSource params, int page, int size) {
        params.addValue("_limit", size).addValue("_offset", (long) page * size);
        // Wrapped so the order can use the quoted column aliases, also inside expressions.
        return list("select * from (" + sql + ") page_x order by " + orderBy + " limit :_limit offset :_offset", params);
    }

    /** Dates as ISO dates, timestamps as offset date-times, and money rounded to 2 decimals stays a BigDecimal. */
    static Map<String, Object> normalize(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        row.forEach((k, v) -> {
            if (v instanceof Date d) out.put(k, d.toLocalDate());
            else if (v instanceof Timestamp ts) out.put(k, ts.toInstant().atOffset(ZoneOffset.UTC));
            else out.put(k, v);
        });
        return out;
    }

    public static String like(String q) {
        return q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
    }
}
