package af.storemis.inventory;

import static af.storemis.support.ApiClient.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import af.storemis.support.ApiClient;
import af.storemis.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class InventoryIntegrationTest extends IntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    ApiClient api;
    String admin;
    long categoryId;
    long pieceUnit;
    long meterUnit;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, json);
        admin = api.adminToken();
        categoryId = data(mvc.perform(post("/api/v1/categories").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "Category " + UUID.randomUUID()))))).get("id").asLong();
        pieceUnit = jdbc.queryForObject("select id from units where code = 'PIECE'", Long.class);
        meterUnit = jdbc.queryForObject("select id from units where code = 'METER'", Long.class);
    }

    @Test
    void weightedAverageCostFollowsTheSpecExample() throws Exception {
        long id = createProduct("LED TV 43", "TV" + SEQ.incrementAndGet(), pieceUnit, "10", "300", null);
        adjust(admin, id, "INCREASE", "10", "400", null).andExpect(status().isCreated());

        JsonNode stock = data(mvc.perform(get("/api/v1/products/" + id).header("Authorization", bearer(admin)))).get("stock");
        assertThat(dec(stock.get("quantity"))).isEqualByComparingTo("20");
        assertThat(dec(stock.get("avgCost"))).isEqualByComparingTo("350");
        assertThat(dec(stock.get("value"))).isEqualByComparingTo("7000");
        assertThat(stock.get("quantity").isTextual()).as("money and quantities are JSON strings").isTrue();

        JsonNode dec = data(adjust(admin, id, "DECREASE", "1", null, null).andExpect(status().isCreated()));
        assertThat(dec(dec.get("unitCost"))).isEqualByComparingTo("350");

        JsonNode movements = data(mvc.perform(get("/api/v1/inventory/movements?productId=" + id).header("Authorization", bearer(admin)))).get("content");
        assertThat(movements).hasSize(3);
        assertThat(movements.get(0).get("type").asText()).isEqualTo("ADJUSTMENT_OUT");
        assertThat(dec(movements.get(0).get("quantity"))).isEqualByComparingTo("-1");
        assertThat(dec(movements.get(0).get("quantityAfter"))).isEqualByComparingTo("19");
        assertThat(dec(movements.get(0).get("valueAfter"))).isEqualByComparingTo("6650");
        assertThat(movements.get(2).get("type").asText()).isEqualTo("OPENING");

        mvc.perform(get("/api/v1/inventory/integrity-check").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.data.consistent").value(true));
    }

    @Test
    void cannotRemoveMoreThanIsInStock() throws Exception {
        long id = createProduct("Fan", "FAN" + SEQ.incrementAndGet(), pieceUnit, "2", "1500", null);
        adjust(admin, id, "DAMAGE", "3", null, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STOCK_INSUFFICIENT"))
                .andExpect(jsonPath("$.error.params.available").value("2"));
        Integer moves = jdbc.queryForObject("select count(*) from stock_movements where product_id = ?", Integer.class, id);
        assertThat(moves).as("the failed adjustment left nothing behind").isEqualTo(1);
    }

    @Test
    void openingStockOnlyOnceAndWholeUnitsForPieces() throws Exception {
        long id = createProduct("Kettle", null, pieceUnit, "5", "800", null);
        adjust(admin, id, "OPENING", "1", "800", null).andExpect(jsonPath("$.error.code").value("OPENING_STOCK_EXISTS"));
        adjust(admin, id, "INCREASE", "1.5", "800", null).andExpect(jsonPath("$.error.code").value("QUANTITY_NOT_INTEGER"));

        long cable = createProduct("Cable 2.5mm", null, meterUnit, "100.5", "45.25", null);
        adjust(admin, cable, "DECREASE", "2.25", null, null).andExpect(status().isCreated());
        JsonNode stock = data(mvc.perform(get("/api/v1/products/" + cable).header("Authorization", bearer(admin)))).get("stock");
        assertThat(dec(stock.get("quantity"))).isEqualByComparingTo("98.25");
    }

    @Test
    void adjustmentNumbersAreSequentialAndRetriesAreIdempotent() throws Exception {
        long id = createProduct("Iron", null, pieceUnit, "10", "1000", null);
        UUID key = UUID.randomUUID();
        JsonNode first = data(adjust(admin, id, "DAMAGE", "1", null, key));
        JsonNode retry = data(adjust(admin, id, "DAMAGE", "1", null, key));
        assertThat(retry.get("id").asLong()).isEqualTo(first.get("id").asLong());
        assertThat(first.get("adjustmentNo").asText()).matches("ADJ-\\d{4}-\\d{6}");
        JsonNode next = data(adjust(admin, id, "DAMAGE", "1", null, null));
        long a = Long.parseLong(first.get("adjustmentNo").asText().substring(9));
        long b = Long.parseLong(next.get("adjustmentNo").asText().substring(9));
        assertThat(b).isEqualTo(a + 1);
        JsonNode stock = data(mvc.perform(get("/api/v1/products/" + id).header("Authorization", bearer(admin)))).get("stock");
        assertThat(dec(stock.get("quantity"))).isEqualByComparingTo("8");
    }

    @Test
    void twoCashiersCannotTakeTheLastUnitTwice() throws Exception {
        long id = createProduct("Last One", null, pieceUnit, "1", "500", null);
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Integer> task = () -> {
                start.await();
                return adjust(admin, id, "DECREASE", "1", null, null).andReturn().getResponse().getStatus();
            };
            results.add(pool.submit(task));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) statuses.add(f.get());
        pool.shutdown();
        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(threads - 1);
        assertThat(jdbc.queryForObject("select quantity_on_hand from product_stock where product_id = ?", java.math.BigDecimal.class, id))
                .isEqualByComparingTo("0");
    }

    @Test
    void entryUsersSeeQuantitiesButNotCostsAndCannotMaintainProducts() throws Exception {
        long id = createProduct("Blender", "BL" + SEQ.incrementAndGet(), pieceUnit, "4", "2000", null);
        String cashier = api.createReadyUser(admin, "inv.cashier" + SEQ.incrementAndGet(), "ENTRY_USER", List.of());
        JsonNode p = data(mvc.perform(get("/api/v1/products/" + id).header("Authorization", bearer(cashier))).andExpect(status().isOk()));
        assertThat(dec(p.get("stock").get("quantity"))).isEqualByComparingTo("4");
        assertThat(p.get("stock").get("avgCost").isNull()).isTrue();
        assertThat(p.get("stock").get("value").isNull()).isTrue();

        mvc.perform(post("/api/v1/products").header("Authorization", bearer(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(productBody("Nope", null, pieceUnit, null, null, null)))).andExpect(status().isForbidden());
        adjust(cashier, id, "DECREASE", "1", null, null).andExpect(status().isForbidden());

        String stockKeeper = api.createReadyUser(admin, "inv.keeper" + SEQ.incrementAndGet(), "ENTRY_USER", List.of("ADJUST_STOCK"));
        adjust(stockKeeper, id, "DAMAGE", "1", null, null).andExpect(status().isCreated()).andExpect(jsonPath("$.data.unitCost").isEmpty());
        adjust(stockKeeper, id, "OPENING", "1", "100", null).andExpect(status().isForbidden());
    }

    @Test
    void productCodesAreUniqueAndSearchPrefersExactCode() throws Exception {
        String code = "SKU-" + UUID.randomUUID().toString().substring(0, 8);
        createProduct("Washing machine", code, pieceUnit, null, null, null);
        mvc.perform(post("/api/v1/products").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(productBody("Other", code.toLowerCase(), pieceUnit, null, null, null))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.params.field").value("code"));
        mvc.perform(get("/api/v1/products/search").param("q", code).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.data[0].code").value(code));
    }

    @Test
    void lowStockAlertsAndStatusFilter() throws Exception {
        String tag = "Alert" + SEQ.incrementAndGet();
        long low = createProduct(tag + " low", null, pieceUnit, "2", "10", "5");
        long out = createProduct(tag + " out", null, pieceUnit, null, null, "1");
        createProduct(tag + " fine", null, pieceUnit, "50", "10", "5");
        JsonNode lowList = data(mvc.perform(get("/api/v1/inventory/stock").param("q", tag).param("status", "LOW_STOCK").header("Authorization", bearer(admin))));
        assertThat(lowList.get("content")).hasSize(1);
        assertThat(lowList.get("content").get(0).get("id").asLong()).isEqualTo(low);
        assertThat(lowList.get("content").get(0).get("stock").get("status").asText()).isEqualTo("LOW_STOCK");
        JsonNode outList = data(mvc.perform(get("/api/v1/inventory/stock").param("q", tag).param("status", "OUT_OF_STOCK").header("Authorization", bearer(admin))));
        assertThat(outList.get("content").get(0).get("id").asLong()).isEqualTo(out);
        JsonNode alerts = data(mvc.perform(get("/api/v1/inventory/alerts?limit=50").header("Authorization", bearer(admin))));
        assertThat(alerts.get("lowStock").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(alerts.get("outOfStock").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void everySortOptionWorks() throws Exception {
        createProduct("Sortable", null, pieceUnit, "3", "10", "5");
        for (String sort : List.of("name,asc", "code,desc", "category,asc", "quantity,asc", "value,desc", "createdAt,desc", "lastMovementAt,asc", "bogus,asc")) {
            mvc.perform(get("/api/v1/inventory/stock").param("sort", sort).param("status", "LOW_STOCK").header("Authorization", bearer(admin)))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/v1/products").param("sort", sort).header("Authorization", bearer(admin))).andExpect(status().isOk());
        }
    }

    @Test
    void productUpdateUsesVersionAndIsAudited() throws Exception {
        long id = createProduct("Heater", null, pieceUnit, null, null, null);
        JsonNode p = data(mvc.perform(get("/api/v1/products/" + id).header("Authorization", bearer(admin))));
        Map<String, Object> body = productBody("Heater 2000W", "HT" + SEQ.incrementAndGet(), pieceUnit, null, null, "3");
        body.put("version", p.get("version").asInt());
        mvc.perform(put("/api/v1/products/" + id).header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Heater 2000W"));
        mvc.perform(put("/api/v1/products/" + id).header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().isConflict());
        String details = jdbc.queryForObject("select details::text from audit_logs where module = 'PRODUCTS' and action = 'UPDATE' and entity_id = ? order by id desc limit 1",
                String.class, id);
        assertThat(details).contains("Heater 2000W");
    }

    // ---- helpers ----

    /** Money and quantities are JSON strings. */
    private static java.math.BigDecimal dec(JsonNode n) {
        return new java.math.BigDecimal(n.asText());
    }

    private long createProduct(String name, String code, long unitId, String openingQty, String openingCost, String minStock) throws Exception {
        return data(mvc.perform(post("/api/v1/products").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(productBody(name, code, unitId, openingQty, openingCost, minStock))))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private Map<String, Object> productBody(String name, String code, long unitId, String openingQty, String openingCost, String minStock) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("code", code);
        m.put("categoryId", categoryId);
        m.put("unitId", unitId);
        m.put("openingQuantity", openingQty);
        m.put("openingUnitCost", openingCost);
        m.put("minStockLevel", minStock);
        return m;
    }

    private org.springframework.test.web.servlet.ResultActions adjust(String token, long productId, String type, String qty, String cost, UUID key)
            throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("productId", productId);
        m.put("type", type);
        m.put("quantity", qty);
        m.put("unitCost", cost);
        m.put("reason", "Test " + type);
        m.put("clientRequestId", key);
        return mvc.perform(post("/api/v1/inventory/adjustments").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(m)));
    }

    private JsonNode data(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString()).get("data");
    }
}
