package af.storemis.purchase;

import static af.storemis.support.ApiClient.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import af.storemis.support.ApiClient;
import af.storemis.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
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

class PurchaseIntegrationTest extends IntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    ApiClient api;
    String admin;
    long categoryId;
    long pieceUnit;
    long afnCash;
    long usdCash;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, json);
        admin = api.adminToken();
        categoryId = data(perform(post("/api/v1/categories"), Map.of("name", "Purch cat " + UUID.randomUUID()))).get("id").asLong();
        pieceUnit = jdbc.queryForObject("select id from units where code = 'PIECE'", Long.class);
        afnCash = jdbc.queryForObject("select id from cash_accounts where name = 'AFN Cash'", Long.class);
        usdCash = jdbc.queryForObject("select id from cash_accounts where name = 'USD Cash'", Long.class);
        // Opening cash can be set once per account; the first test to run sets it.
        Integer txns = jdbc.queryForObject("select count(*) from cash_transactions where cash_account_id = ?", Integer.class, afnCash);
        if (txns == 0) {
            perform(post("/api/v1/cash-accounts/" + afnCash + "/opening-balance"), Map.of("amount", "1000000")).andExpect(status().isOk());
            perform(post("/api/v1/cash-accounts/" + usdCash + "/opening-balance"), Map.of("amount", "10000")).andExpect(status().isOk());
        }
    }

    @Test
    void confirmedPurchaseUpdatesStockAverageCostPayableAndCash() throws Exception {
        long product = product("TV " + SEQ.incrementAndGet(), "10", "300");
        long supplier = supplier("Kabul Traders");
        BigDecimal cashBefore = cash(afnCash);

        JsonNode draft = data(perform(post("/api/v1/purchases"), purchase(supplier, "AFN", null, "100", List.of(line(product, "10", "400", null))))
                .andExpect(status().isCreated()));
        assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
        assertThat(dec(draft.get("grandTotal"))).isEqualByComparingTo("3900");
        assertThat(dec(draft.get("items").get(0).get("landedUnitCostBase"))).isEqualByComparingTo("390");
        assertThat(stockQty(product)).as("drafts do not touch stock").isEqualByComparingTo("10");

        long id = draft.get("id").asLong();
        JsonNode confirmed = data(perform(post("/api/v1/purchases/" + id + "/confirm"), Map.of("amountPaid", "1000", "cashAccountId", afnCash))
                .andExpect(status().isOk()));
        assertThat(confirmed.get("purchaseNo").asText()).matches("PUR-\\d{4}-\\d{6}");
        assertThat(confirmed.get("paymentStatus").asText()).isEqualTo("PARTIALLY_PAID");
        assertThat(dec(confirmed.get("balanceDue"))).isEqualByComparingTo("2900");

        assertThat(stockQty(product)).isEqualByComparingTo("20");
        // (10 × 300 + 10 × 390) / 20 = 345
        assertThat(jdbc.queryForObject("select avg_unit_cost from product_stock where product_id = ?", BigDecimal.class, product)).isEqualByComparingTo("345");
        assertThat(balance(supplier, "AFN")).isEqualByComparingTo("2900");
        assertThat(cashBefore.subtract(cash(afnCash))).isEqualByComparingTo("1000");

        // Confirming again (e.g. a retried request) changes nothing.
        perform(post("/api/v1/purchases/" + id + "/confirm"), Map.of("amountPaid", "1000", "cashAccountId", afnCash)).andExpect(status().isOk());
        assertThat(stockQty(product)).isEqualByComparingTo("20");
        assertThat(cashBefore.subtract(cash(afnCash))).isEqualByComparingTo("1000");

        // Pay the rest later: allocated to the open purchase.
        JsonNode pay = data(perform(post("/api/v1/supplier-payments"), Map.of("supplierId", supplier, "currency", "AFN", "amount", "2900",
                "cashAccountId", afnCash)).andExpect(status().isCreated()));
        assertThat(pay.get("allocations").get(0).get("purchaseId").asLong()).isEqualTo(id);
        assertThat(balance(supplier, "AFN")).isEqualByComparingTo("0");
        assertThat(data(perform(get("/api/v1/purchases/" + id), null)).get("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(cashBefore.subtract(cash(afnCash))).isEqualByComparingTo("3900");
    }

    @Test
    void usdPurchaseUsesItsOwnRateAndCanBePaidInAfghani() throws Exception {
        long product = product("Fridge " + SEQ.incrementAndGet(), null, null);
        long supplier = supplier("Dubai Supplier");
        JsonNode p = data(perform(post("/api/v1/purchases"), purchase(supplier, "USD", "70", null, List.of(line(product, "5", "10", null)))));
        assertThat(dec(p.get("exchangeRate"))).isEqualByComparingTo("70");
        assertThat(dec(p.get("items").get(0).get("landedUnitCostBase"))).as("10 USD × 70").isEqualByComparingTo("700");
        perform(post("/api/v1/purchases/" + p.get("id").asLong() + "/confirm"), Map.of()).andExpect(status().isOk());
        assertThat(balance(supplier, "USD")).isEqualByComparingTo("50");
        assertThat(balance(supplier, "AFN")).isEqualByComparingTo("0");

        // Today's USD rate changes; the purchase keeps its historical rate.
        perform(post("/api/v1/exchange-rates"), Map.of("currency", "USD", "rate", "70")).andExpect(status().isCreated());
        BigDecimal afnBefore = cash(afnCash);
        JsonNode pay = data(perform(post("/api/v1/supplier-payments"), Map.of("supplierId", supplier, "currency", "AFN", "amount", "3500",
                "cashAccountId", afnCash)).andExpect(status().isCreated()));
        assertThat(dec(pay.get("allocations").get(0).get("appliedAmount"))).as("3500 AFN ÷ 70 = 50 USD").isEqualByComparingTo("50");
        assertThat(balance(supplier, "USD")).isEqualByComparingTo("0");
        assertThat(afnBefore.subtract(cash(afnCash))).isEqualByComparingTo("3500");
        assertThat(jdbc.queryForObject("select exchange_rate from purchases where id = ?", BigDecimal.class, p.get("id").asLong()))
                .isEqualByComparingTo("70");
    }

    @Test
    void cancellingRefundsCashAndRemovesTheGoods() throws Exception {
        long product = product("Iron " + SEQ.incrementAndGet(), null, null);
        long supplier = supplier("Herat Electric");
        BigDecimal cashBefore = cash(afnCash);
        long id = confirmAfn(supplier, product, "4", "500", "2000");
        assertThat(stockQty(product)).isEqualByComparingTo("4");

        perform(post("/api/v1/purchases/" + id + "/cancel"), Map.of("reason", "Wrong supplier invoice", "paymentHandling", "REFUND"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertThat(stockQty(product)).isEqualByComparingTo("0");
        assertThat(balance(supplier, "AFN")).isEqualByComparingTo("0");
        assertThat(cash(afnCash)).isEqualByComparingTo(cashBefore);
        mvc.perform(get("/api/v1/inventory/integrity-check").header("Authorization", bearer(admin))).andExpect(jsonPath("$.data.consistent").value(true));
    }

    @Test
    void cannotCancelWhenTheGoodsAreAlreadyGone() throws Exception {
        long product = product("Kettle " + SEQ.incrementAndGet(), null, null);
        long supplier = supplier("Mazar Wholesale");
        long id = confirmAfn(supplier, product, "3", "800", "0");
        perform(post("/api/v1/inventory/adjustments"), Map.of("productId", product, "type", "DAMAGE", "quantity", "2", "reason", "Broken"));
        perform(post("/api/v1/purchases/" + id + "/cancel"), Map.of("reason", "Test", "paymentHandling", "CREDIT"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("STOCK_INSUFFICIENT"));
        assertThat(data(perform(get("/api/v1/purchases/" + id), null)).get("status").asText()).isEqualTo("CONFIRMED");
    }

    @Test
    void notEnoughCashRollsEverythingBack() throws Exception {
        long product = product("Generator " + SEQ.incrementAndGet(), null, null);
        long supplier = supplier("Big Supplier");
        JsonNode p = data(perform(post("/api/v1/purchases"), purchase(supplier, "USD", "70", null, List.of(line(product, "1", "50000", null)))));
        perform(post("/api/v1/purchases/" + p.get("id").asLong() + "/confirm"), Map.of("amountPaid", "50000", "cashAccountId", usdCash))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("CASH_INSUFFICIENT"));
        assertThat(stockQty(product)).isEqualByComparingTo("0");
        assertThat(balance(supplier, "USD")).isEqualByComparingTo("0");
        assertThat(data(perform(get("/api/v1/purchases/" + p.get("id").asLong()), null)).get("status").asText()).isEqualTo("DRAFT");
    }

    @Test
    void cashAccountMustMatchThePurchaseCurrency() throws Exception {
        long product = product("Lamp " + SEQ.incrementAndGet(), null, null);
        long supplier = supplier("Lamp Supplier");
        JsonNode p = data(perform(post("/api/v1/purchases"), purchase(supplier, "AFN", null, null, List.of(line(product, "2", "100", null)))));
        perform(post("/api/v1/purchases/" + p.get("id").asLong() + "/confirm"), Map.of("amountPaid", "100", "cashAccountId", usdCash))
                .andExpect(jsonPath("$.error.code").value("CURRENCY_MISMATCH"));
    }

    @Test
    void reversingAPaymentRestoresTheDebtAndTheCash() throws Exception {
        long product = product("Fan " + SEQ.incrementAndGet(), null, null);
        long supplier = supplier("Fan Supplier");
        confirmAfn(supplier, product, "2", "1000", "0");
        BigDecimal cashBefore = cash(afnCash);
        JsonNode pay = data(perform(post("/api/v1/supplier-payments"), Map.of("supplierId", supplier, "currency", "AFN", "amount", "2500",
                "cashAccountId", afnCash, "clientRequestId", UUID.randomUUID().toString())));
        assertThat(dec(pay.get("unallocatedAmount"))).as("500 more than owed becomes an advance").isEqualByComparingTo("500");
        assertThat(balance(supplier, "AFN")).isEqualByComparingTo("-500");

        perform(post("/api/v1/supplier-payments/" + pay.get("id").asLong() + "/reverse"), Map.of("reason", "Paid by mistake"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REVERSED"));
        assertThat(balance(supplier, "AFN")).isEqualByComparingTo("2000");
        assertThat(cash(afnCash)).isEqualByComparingTo(cashBefore);
    }

    @Test
    void draftsCanBeEditedAndDiscardedButConfirmedPurchasesCannot() throws Exception {
        long product = product("Mixer " + SEQ.incrementAndGet(), null, null);
        long supplier = supplier("Mixer Supplier");
        JsonNode d = data(perform(post("/api/v1/purchases"), purchase(supplier, "AFN", null, null, List.of(line(product, "1", "100", null)))));
        Map<String, Object> body = purchase(supplier, "AFN", null, null, List.of(line(product, "2", "150", "10"), line(product, "1", "90", null)));
        body.put("version", d.get("version").asInt());
        JsonNode edited = data(perform(put("/api/v1/purchases/" + d.get("id").asLong()), body).andExpect(status().isOk()));
        assertThat(edited.get("items")).hasSize(2);
        assertThat(dec(edited.get("grandTotal"))).isEqualByComparingTo("380");
        perform(delete("/api/v1/purchases/" + d.get("id").asLong()), null).andExpect(status().isOk());

        long confirmed = confirmAfn(supplier, product, "1", "100", "0");
        perform(delete("/api/v1/purchases/" + confirmed), null).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
    }

    @Test
    void supplierStatementAndOpeningBalance() throws Exception {
        long product = product("Heater " + SEQ.incrementAndGet(), null, null);
        long supplier = supplier("Old Supplier");
        perform(post("/api/v1/suppliers/" + supplier + "/opening-balance"), Map.of("currency", "AFN", "amount", "15000", "notes", "From paper ledger"))
                .andExpect(status().isOk());
        perform(post("/api/v1/suppliers/" + supplier + "/opening-balance"), Map.of("currency", "AFN", "amount", "1"))
                .andExpect(jsonPath("$.error.code").value("OPENING_BALANCE_EXISTS"));
        confirmAfn(supplier, product, "1", "5000", "2000");
        JsonNode st = data(perform(get("/api/v1/suppliers/" + supplier + "/statement?currency=AFN&from=" + LocalDate.now().minusDays(1)), null));
        assertThat(st.get("lines")).hasSize(3);
        assertThat(dec(st.get("closingBalance"))).isEqualByComparingTo("18000");
        assertThat(dec(st.get("totalCredit"))).isEqualByComparingTo("20000");
        assertThat(dec(st.get("totalDebit"))).isEqualByComparingTo("2000");
    }

    @Test
    void permissions() throws Exception {
        String cashier = api.createReadyUser(admin, "pur.cashier" + SEQ.incrementAndGet(), "ENTRY_USER", List.of());
        mvc.perform(get("/api/v1/purchases").header("Authorization", bearer(cashier))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/suppliers").header("Authorization", bearer(cashier))).andExpect(status().isForbidden());

        String buyer = api.createReadyUser(admin, "pur.buyer" + SEQ.incrementAndGet(), "ENTRY_USER", List.of("RECORD_PURCHASES"));
        long product = product("Toaster " + SEQ.incrementAndGet(), null, null);
        long supplier = supplier("Toaster Supplier");
        long id = confirmAfn(supplier, product, "1", "100", "0");
        mvc.perform(post("/api/v1/purchases/" + id + "/cancel").header("Authorization", bearer(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Not allowed\",\"paymentHandling\":\"CREDIT\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/supplier-payments").header("Authorization", bearer(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("supplierId", supplier, "currency", "AFN", "amount", "1", "cashAccountId", afnCash))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/cash-accounts").header("Authorization", bearer(buyer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].balance").isEmpty());
    }

    // ---- helpers ----

    private long confirmAfn(long supplier, long product, String qty, String cost, String paid) throws Exception {
        JsonNode d = data(perform(post("/api/v1/purchases"), purchase(supplier, "AFN", null, null, List.of(line(product, qty, cost, null)))));
        Map<String, Object> body = new HashMap<>(Map.of("amountPaid", paid));
        body.put("cashAccountId", afnCash);
        perform(post("/api/v1/purchases/" + d.get("id").asLong() + "/confirm"), body).andExpect(status().isOk());
        return d.get("id").asLong();
    }

    private long product(String name, String openingQty, String openingCost) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("categoryId", categoryId);
        m.put("unitId", pieceUnit);
        m.put("openingQuantity", openingQty);
        m.put("openingUnitCost", openingCost);
        return data(perform(post("/api/v1/products"), m).andExpect(status().isCreated())).get("id").asLong();
    }

    private long supplier(String name) throws Exception {
        return data(perform(post("/api/v1/suppliers"), Map.of("name", name + " " + SEQ.incrementAndGet())).andExpect(status().isCreated())).get("id").asLong();
    }

    private Map<String, Object> purchase(long supplier, String currency, String rate, String overall, List<Map<String, Object>> items) {
        Map<String, Object> m = new HashMap<>();
        m.put("supplierId", supplier);
        m.put("purchaseDate", LocalDate.now().toString());
        m.put("currency", currency);
        m.put("exchangeRate", rate);
        m.put("overallDiscount", overall);
        m.put("supplierInvoiceNo", "INV-" + SEQ.incrementAndGet());
        m.put("items", items);
        return m;
    }

    private Map<String, Object> line(long product, String qty, String cost, String discount) {
        Map<String, Object> m = new HashMap<>();
        m.put("productId", product);
        m.put("quantity", qty);
        m.put("unitCost", cost);
        m.put("discount", discount);
        return m;
    }

    private BigDecimal stockQty(long product) {
        return jdbc.queryForObject("select quantity_on_hand from product_stock where product_id = ?", BigDecimal.class, product);
    }

    private BigDecimal cash(long account) {
        return jdbc.queryForObject("select current_balance from cash_accounts where id = ?", BigDecimal.class, account);
    }

    private BigDecimal balance(long supplier, String currency) throws Exception {
        JsonNode b = data(perform(get("/api/v1/suppliers/" + supplier), null)).get("balances").get(currency);
        return b == null ? BigDecimal.ZERO : new BigDecimal(b.asText());
    }

    private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, Object body) throws Exception {
        req.header("Authorization", bearer(admin));
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(req);
    }

    private JsonNode data(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString()).get("data");
    }

    private static BigDecimal dec(JsonNode n) {
        return new BigDecimal(n.asText());
    }
}
