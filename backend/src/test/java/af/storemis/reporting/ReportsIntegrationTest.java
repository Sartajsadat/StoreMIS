package af.storemis.reporting;

import static af.storemis.support.ApiClient.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import af.storemis.reporting.service.ReportService;
import af.storemis.support.ApiClient;
import af.storemis.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Phase 8: every report runs; profit and loss follows the posting rules; dashboard and My Day respond. */
class ReportsIntegrationTest extends IntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    String admin;
    long categoryId;
    long pieceUnit;
    long afnCash;

    @BeforeEach
    void setUp() throws Exception {
        admin = new ApiClient(mvc, json).adminToken();
        categoryId = data(perform(post("/api/v1/categories"), Map.of("name", "Report cat " + UUID.randomUUID()))).get("id").asLong();
        pieceUnit = jdbc.queryForObject("select id from units where code = 'PIECE'", Long.class);
        afnCash = jdbc.queryForObject("select id from cash_accounts where name = 'AFN Cash'", Long.class);
        if (jdbc.queryForObject("select count(*) from cash_transactions where cash_account_id = ?", Integer.class, afnCash) == 0) {
            perform(post("/api/v1/cash-accounts/" + afnCash + "/opening-balance"), Map.of("amount", "1000000"));
        }
        jdbc.update("update business_settings set block_sale_below_cost = false");
    }

    @Test
    void everyReportRuns() throws Exception {
        for (String key : ReportService.KEYS) {
            JsonNode r = data(perform(get("/api/v1/reports/" + key + "?q=a&size=5"), null).andExpect(status().isOk()));
            assertThat(r.get("key").asText()).isEqualTo(key);
            assertThat(r.get("columns").size()).as(key).isPositive();
            assertThat(r.get("rows").isArray()).as(key).isTrue();
        }
        perform(get("/api/v1/reports/nope"), null).andExpect(status().isNotFound());
    }

    @Test
    void profitAndLossFollowsThePostingRules() throws Exception {
        Map<String, BigDecimal> before = pl();
        String name = "P&L TV " + UUID.randomUUID().toString().substring(0, 8);
        long product = product(name, "10", "1000");
        long customer = data(perform(post("/api/v1/customers"), Map.of("name", "P&L customer " + SEQ.incrementAndGet()))).get("id").asLong();
        Map<String, Object> sale = new HashMap<>();
        sale.put("customerId", customer);
        sale.put("currency", "AFN");
        sale.put("overallDiscount", "100");
        sale.put("items", List.of(Map.of("productId", product, "quantity", "2", "unitPrice", "1500")));
        sale.put("payments", List.of());
        JsonNode s = data(perform(post("/api/v1/sales/confirm"), sale).andExpect(status().isCreated()));
        long itemId = s.get("items").get(0).get("id").asLong();
        perform(post("/api/v1/sales-returns"), Map.of("documentId", s.get("id").asLong(), "reason", "Broken", "settlement", "CREDIT",
                "items", List.of(Map.of("itemId", itemId, "quantity", "1", "condition", "DAMAGED")))).andExpect(status().isCreated());
        long rent = jdbc.queryForObject("select id from expense_categories where code = 'RENT'", Long.class);
        perform(post("/api/v1/expenses"), Map.of("categoryId", rent, "description", "P&L rent", "amount", "300", "cashAccountId", afnCash))
                .andExpect(status().isCreated());
        perform(post("/api/v1/inventory/adjustments"), Map.of("productId", product, "type", "DAMAGE", "quantity", "1", "reason", "Dropped"))
                .andExpect(status().isCreated());
        Map<String, BigDecimal> after = pl();

        assertThat(delta(before, after, "GROSS_SALES")).isEqualByComparingTo("3000");
        assertThat(delta(before, after, "DISCOUNTS")).isEqualByComparingTo("-100");
        assertThat(delta(before, after, "SALES_RETURNS")).as("1 of 2 at the net price 1,450").isEqualByComparingTo("-1450");
        assertThat(delta(before, after, "NET_SALES")).isEqualByComparingTo("1450");
        assertThat(delta(before, after, "COGS")).as("2 sold − 1 returned, at 1,000").isEqualByComparingTo("-1000");
        assertThat(delta(before, after, "GROSS_PROFIT")).isEqualByComparingTo("450");
        assertThat(delta(before, after, "OPERATING_EXPENSES")).isEqualByComparingTo("-300");
        assertThat(delta(before, after, "INVENTORY_LOSSES")).as("damaged return + damage write-off").isEqualByComparingTo("-2000");
        assertThat(delta(before, after, "NET_PROFIT")).isEqualByComparingTo("-1850");

        JsonNode best = data(perform(get("/api/v1/reports/best-selling").param("q", name), null));
        assertThat(new BigDecimal(best.get("rows").get(0).get("quantity").asText())).as("2 sold − 1 returned").isEqualByComparingTo("1");
        JsonNode damaged = data(perform(get("/api/v1/reports/damaged-stock").param("q", name), null));
        assertThat(damaged.get("totalRows").asLong()).isEqualTo(2);
        assertThat(new BigDecimal(damaged.get("totals").get("totalCost").asText())).isEqualByComparingTo("2000");
    }

    @Test
    void dashboardAndMyDay() throws Exception {
        perform(get("/api/v1/dashboard/summary"), null).andExpect(status().isOk()).andExpect(jsonPath("$.data.kpi.netSales.value").exists())
                .andExpect(jsonPath("$.data.cash").isArray());
        JsonNode charts = data(perform(get("/api/v1/dashboard/charts?from=2026-01-01&to=2026-12-31"), null).andExpect(status().isOk()));
        assertThat(charts.get("unit").asText()).isEqualTo("month");
        assertThat(charts.get("series").size()).isEqualTo(12);
        perform(get("/api/v1/dashboard/recent"), null).andExpect(status().isOk()).andExpect(jsonPath("$.data.activity").isArray());
        perform(get("/api/v1/dashboard/my-day"), null).andExpect(status().isOk()).andExpect(jsonPath("$.data.heldCarts").exists());
        perform(get("/api/v1/dashboard/summary?from=2026-02-01&to=2026-01-01"), null).andExpect(status().isBadRequest());
    }

    private Map<String, BigDecimal> pl() throws Exception {
        JsonNode r = data(perform(get("/api/v1/reports/profit-loss"), null).andExpect(status().isOk()));
        Map<String, BigDecimal> out = new HashMap<>();
        for (JsonNode row : r.get("rows")) {
            if (!row.get("line").asText().equals("EXPENSE_CATEGORY")) out.put(row.get("line").asText(), new BigDecimal(row.get("amount").asText()));
        }
        return out;
    }

    private static BigDecimal delta(Map<String, BigDecimal> before, Map<String, BigDecimal> after, String line) {
        return after.get(line).subtract(before.get(line));
    }

    private long product(String name, String qty, String cost) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("categoryId", categoryId);
        m.put("unitId", pieceUnit);
        m.put("openingQuantity", qty);
        m.put("openingUnitCost", cost);
        return data(perform(post("/api/v1/products"), m).andExpect(status().isCreated())).get("id").asLong();
    }

    private ResultActions perform(MockHttpServletRequestBuilder req, Object body) throws Exception {
        req.header("Authorization", bearer(admin));
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(req);
    }

    private JsonNode data(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString()).get("data");
    }
}
