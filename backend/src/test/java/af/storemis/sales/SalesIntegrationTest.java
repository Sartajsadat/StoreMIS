package af.storemis.sales;

import static af.storemis.support.ApiClient.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import af.storemis.support.ApiClient;
import af.storemis.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
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

class SalesIntegrationTest extends IntegrationTest {

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
        categoryId = data(perform(admin, post("/api/v1/categories"), Map.of("name", "Sales cat " + UUID.randomUUID()))).get("id").asLong();
        pieceUnit = jdbc.queryForObject("select id from units where code = 'PIECE'", Long.class);
        afnCash = jdbc.queryForObject("select id from cash_accounts where name = 'AFN Cash'", Long.class);
        usdCash = jdbc.queryForObject("select id from cash_accounts where name = 'USD Cash'", Long.class);
        if (jdbc.queryForObject("select count(*) from cash_transactions where cash_account_id = ?", Integer.class, afnCash) == 0) {
            perform(admin, post("/api/v1/cash-accounts/" + afnCash + "/opening-balance"), Map.of("amount", "1000000"));
            perform(admin, post("/api/v1/cash-accounts/" + usdCash + "/opening-balance"), Map.of("amount", "10000"));
        }
        jdbc.update("update business_settings set block_sale_below_cost = false");
    }

    @Test
    void specExamplePartialPaymentThenCollection() throws Exception {
        long product = product("TV " + SEQ.incrementAndGet(), "10", "300");
        long customer = customer("Ahmad Electric");
        BigDecimal cashBefore = cash(afnCash);

        JsonNode sale = data(perform(admin, post("/api/v1/sales/confirm"),
                sale(customer, "AFN", null, List.of(line(product, "2", "5000", null)), List.of(pay("AFN", "6000", afnCash)), null))
                .andExpect(status().isCreated()));
        assertThat(sale.get("invoiceNo").asText()).matches("INV-\\d{4}-\\d{6}");
        assertThat(dec(sale.get("grandTotal"))).isEqualByComparingTo("10000");
        assertThat(dec(sale.get("amountPaid"))).isEqualByComparingTo("6000");
        assertThat(dec(sale.get("balanceDue"))).isEqualByComparingTo("4000");
        assertThat(sale.get("paymentStatus").asText()).isEqualTo("PARTIALLY_PAID");
        assertThat(dec(sale.get("totalCostBase"))).as("COGS = 2 × 300").isEqualByComparingTo("600");
        assertThat(stockQty(product)).isEqualByComparingTo("8");
        assertThat(cash(afnCash).subtract(cashBefore)).as("cash grows only by what was received").isEqualByComparingTo("6000");
        assertThat(balance(customer, "AFN")).isEqualByComparingTo("4000");

        perform(admin, post("/api/v1/customer-payments"), Map.of("customerId", customer, "currency", "AFN", "amount", "4000", "cashAccountId", afnCash))
                .andExpect(status().isCreated());
        JsonNode after = data(perform(admin, get("/api/v1/sales/" + sale.get("id").asLong()), null));
        assertThat(after.get("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(after.get("payments")).hasSize(2);
        assertThat(balance(customer, "AFN")).isEqualByComparingTo("0");
        assertThat(cash(afnCash).subtract(cashBefore)).isEqualByComparingTo("10000");
    }

    @Test
    void walkInCustomerMustPayInFull() throws Exception {
        long product = product("Fan " + SEQ.incrementAndGet(), "5", "1000");
        perform(admin, post("/api/v1/sales/confirm"), sale(null, "AFN", null, List.of(line(product, "1", "1500", null)), List.of(pay("AFN", "1000", afnCash)), null))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("WALK_IN_MUST_PAY"));
        assertThat(stockQty(product)).as("nothing was posted").isEqualByComparingTo("5");
        JsonNode ok = data(perform(admin, post("/api/v1/sales/confirm"),
                sale(null, "AFN", null, List.of(line(product, "1", "1500", null)), List.of(pay("AFN", "1500", afnCash)), null)).andExpect(status().isCreated()));
        assertThat(ok.get("walkIn").asBoolean()).isTrue();
        assertThat(ok.get("paymentStatus").asText()).isEqualTo("PAID");
    }

    @Test
    void oneSalePaidInAfghaniAndDollars() throws Exception {
        long product = product("Fridge " + SEQ.incrementAndGet(), "3", "9000");
        BigDecimal afn = cash(afnCash);
        BigDecimal usd = cash(usdCash);
        JsonNode sale = data(perform(admin, post("/api/v1/sales/confirm"), sale(null, "AFN", null, List.of(line(product, "1", "14000", null)),
                List.of(payRate("USD", "100", "70", usdCash), pay("AFN", "7000", afnCash)), null)).andExpect(status().isCreated()));
        assertThat(sale.get("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(cash(usdCash).subtract(usd)).isEqualByComparingTo("100");
        assertThat(cash(afnCash).subtract(afn)).isEqualByComparingTo("7000");
        JsonNode usdPay = sale.get("payments").get(0);
        assertThat(dec(usdPay.get("appliedAmount"))).as("100 USD × 70 covers 7,000 AFN").isEqualByComparingTo("7000");
    }

    @Test
    void overpaymentIsRefusedForWalkInAndBecomesCreditForCustomers() throws Exception {
        long product = product("Iron " + SEQ.incrementAndGet(), "5", "500");
        perform(admin, post("/api/v1/sales/confirm"), sale(null, "AFN", null, List.of(line(product, "1", "1000", null)), List.of(pay("AFN", "1500", afnCash)), null))
                .andExpect(jsonPath("$.error.code").value("OVERPAYMENT"));
        long customer = customer("Overpaying Customer");
        perform(admin, post("/api/v1/sales/confirm"), sale(customer, "AFN", null, List.of(line(product, "1", "1000", null)), List.of(pay("AFN", "1500", afnCash)), null))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.paymentStatus").value("PAID"));
        assertThat(balance(customer, "AFN")).as("500 AFN customer credit").isEqualByComparingTo("-500");
    }

    @Test
    void retriedRequestDoesNotSellTwice() throws Exception {
        long product = product("Kettle " + SEQ.incrementAndGet(), "5", "400");
        Map<String, Object> body = sale(null, "AFN", null, List.of(line(product, "1", "800", null)), List.of(pay("AFN", "800", afnCash)), UUID.randomUUID());
        JsonNode a = data(perform(admin, post("/api/v1/sales/confirm"), body).andExpect(status().isCreated()));
        JsonNode b = data(perform(admin, post("/api/v1/sales/confirm"), body));
        assertThat(b.get("invoiceNo").asText()).isEqualTo(a.get("invoiceNo").asText());
        assertThat(stockQty(product)).isEqualByComparingTo("4");
    }

    @Test
    void notEnoughStockRollsBackAndKeepsNumbersGapFree() throws Exception {
        long product = product("Heater " + SEQ.incrementAndGet(), "1", "2000");
        perform(admin, post("/api/v1/sales/confirm"), sale(null, "AFN", null, List.of(line(product, "2", "3000", null)), List.of(pay("AFN", "6000", afnCash)), null))
                .andExpect(jsonPath("$.error.code").value("STOCK_INSUFFICIENT"));
        String last = jdbc.queryForObject("select max(invoice_no) from sales", String.class);
        JsonNode ok = data(perform(admin, post("/api/v1/sales/confirm"),
                sale(null, "AFN", null, List.of(line(product, "1", "3000", null)), List.of(pay("AFN", "3000", afnCash)), null)));
        long lastNo = last == null ? 0 : Long.parseLong(last.substring(9));
        assertThat(Long.parseLong(ok.get("invoiceNo").asText().substring(9))).isEqualTo(lastNo + 1);
    }

    @Test
    void entryUsersSellButDiscountsNeedPermissionAndTheySeeOnlyTheirSales() throws Exception {
        long product = product("Blender " + SEQ.incrementAndGet(), "10", "700");
        String noDiscount = api.createReadyUser(admin, "pos.a" + SEQ.incrementAndGet(), "ENTRY_USER", List.of());
        perform(noDiscount, post("/api/v1/sales/confirm"), sale(null, "AFN", null, List.of(line(product, "1", "1000", "50")), List.of(pay("AFN", "950", afnCash)), null))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("DISCOUNT_NOT_ALLOWED"));
        JsonNode mine = data(perform(noDiscount, post("/api/v1/sales/confirm"),
                sale(null, "AFN", null, List.of(line(product, "1", "1000", null)), List.of(pay("AFN", "1000", afnCash)), null)).andExpect(status().isCreated()));
        assertThat(mine.get("totalCostBase").isNull()).as("cost hidden from entry users").isTrue();

        JsonNode list = data(perform(noDiscount, get("/api/v1/sales?size=200"), null)).get("content");
        for (JsonNode s : list) assertThat(s.get("createdBy").asLong()).isEqualTo(mine.get("createdBy").asLong());
        perform(noDiscount, post("/api/v1/sales/" + mine.get("id").asLong() + "/cancel"), Map.of("reason", "Not allowed", "paymentHandling", "REFUND"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancellingRestoresStockCashAndReceivable() throws Exception {
        long product = product("Mixer " + SEQ.incrementAndGet(), "6", "1000");
        long customer = customer("Cancel Customer");
        BigDecimal cashBefore = cash(afnCash);
        JsonNode sale = data(perform(admin, post("/api/v1/sales/confirm"),
                sale(customer, "AFN", null, List.of(line(product, "3", "2000", null)), List.of(pay("AFN", "2500", afnCash)), null)));
        perform(admin, post("/api/v1/sales/" + sale.get("id").asLong() + "/cancel"), Map.of("reason", "Customer changed mind", "paymentHandling", "REFUND"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertThat(stockQty(product)).isEqualByComparingTo("6");
        assertThat(cash(afnCash)).isEqualByComparingTo(cashBefore);
        assertThat(balance(customer, "AFN")).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("select avg_unit_cost from product_stock where product_id = ?", BigDecimal.class, product)).isEqualByComparingTo("1000");
        perform(admin, get("/api/v1/inventory/integrity-check"), null).andExpect(jsonPath("$.data.consistent").value(true));
    }

    @Test
    void heldCartsAreConfirmedLater() throws Exception {
        long product = product("Lamp " + SEQ.incrementAndGet(), "5", "100");
        JsonNode draft = data(perform(admin, post("/api/v1/sales/drafts"), sale(null, "AFN", null, List.of(line(product, "2", "250", null)), null, null))
                .andExpect(status().isCreated()));
        assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
        assertThat(stockQty(product)).isEqualByComparingTo("5");
        JsonNode drafts = data(perform(admin, get("/api/v1/sales/drafts"), null));
        assertThat(drafts.findValuesAsText("id")).contains(draft.get("id").asText());
        perform(admin, post("/api/v1/sales/drafts/" + draft.get("id").asLong() + "/confirm"), Map.of("payments", List.of(pay("AFN", "500", afnCash))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        assertThat(stockQty(product)).isEqualByComparingTo("3");
    }

    @Test
    void sellingBelowCostCanBeBlocked() throws Exception {
        long product = product("Cheap " + SEQ.incrementAndGet(), "5", "300");
        jdbc.update("update business_settings set block_sale_below_cost = true");
        perform(admin, post("/api/v1/sales/confirm"), sale(null, "AFN", null, List.of(line(product, "1", "250", null)), List.of(pay("AFN", "250", afnCash)), null))
                .andExpect(jsonPath("$.error.code").value("SALE_BELOW_COST"));
        jdbc.update("update business_settings set block_sale_below_cost = false");
        perform(admin, post("/api/v1/sales/confirm"), sale(null, "AFN", null, List.of(line(product, "1", "250", null)), List.of(pay("AFN", "250", afnCash)), null))
                .andExpect(status().isCreated());
    }

    @Test
    void receiptAndPriceHints() throws Exception {
        long product = product("Toaster " + SEQ.incrementAndGet(), "5", "300");
        long customer = customer("Hint Customer");
        JsonNode sale = data(perform(admin, post("/api/v1/sales/confirm"),
                sale(customer, "AFN", null, List.of(line(product, "1", "650", null)), List.of(pay("AFN", "650", afnCash)), null)));
        JsonNode receipt = data(perform(admin, get("/api/v1/sales/" + sale.get("id").asLong() + "/receipt"), null));
        assertThat(receipt.get("business").get("name").asText()).isNotBlank();
        assertThat(receipt.get("sale").get("items")).hasSize(1);
        JsonNode hint = data(perform(admin, get("/api/v1/sales/price-hint?productId=" + product + "&customerId=" + customer), null));
        assertThat(dec(hint.get("lastPrice"))).isEqualByComparingTo("650");
        assertThat(dec(hint.get("lastPriceForCustomer"))).isEqualByComparingTo("650");
        assertThat(dec(hint.get("avgCost"))).isEqualByComparingTo("300");
    }

    // ---- helpers ----

    private Map<String, Object> sale(Long customer, String currency, String rate, List<Map<String, Object>> items, List<Map<String, Object>> payments, UUID key) {
        Map<String, Object> m = new HashMap<>();
        m.put("customerId", customer);
        m.put("currency", currency);
        m.put("exchangeRate", rate);
        m.put("items", items);
        m.put("payments", payments == null ? new ArrayList<>() : payments);
        m.put("clientRequestId", key);
        return m;
    }

    private Map<String, Object> line(long product, String qty, String price, String discount) {
        Map<String, Object> m = new HashMap<>();
        m.put("productId", product);
        m.put("quantity", qty);
        m.put("unitPrice", price);
        m.put("discount", discount);
        return m;
    }

    private Map<String, Object> pay(String currency, String amount, long account) {
        return payRate(currency, amount, null, account);
    }

    private Map<String, Object> payRate(String currency, String amount, String rate, long account) {
        Map<String, Object> m = new HashMap<>();
        m.put("currency", currency);
        m.put("amount", amount);
        m.put("exchangeRate", rate);
        m.put("cashAccountId", account);
        return m;
    }

    private long product(String name, String qty, String cost) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("categoryId", categoryId);
        m.put("unitId", pieceUnit);
        m.put("openingQuantity", qty);
        m.put("openingUnitCost", cost);
        return data(perform(admin, post("/api/v1/products"), m).andExpect(status().isCreated())).get("id").asLong();
    }

    private long customer(String name) throws Exception {
        return data(perform(admin, post("/api/v1/customers"), Map.of("name", name + " " + SEQ.incrementAndGet())).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    private BigDecimal stockQty(long product) {
        return jdbc.queryForObject("select quantity_on_hand from product_stock where product_id = ?", BigDecimal.class, product);
    }

    private BigDecimal cash(long account) {
        return jdbc.queryForObject("select current_balance from cash_accounts where id = ?", BigDecimal.class, account);
    }

    private BigDecimal balance(long customer, String currency) throws Exception {
        JsonNode b = data(perform(admin, get("/api/v1/customers/" + customer), null)).get("balances").get(currency);
        return b == null ? BigDecimal.ZERO : new BigDecimal(b.asText());
    }

    private ResultActions perform(String token, MockHttpServletRequestBuilder req, Object body) throws Exception {
        req.header("Authorization", bearer(token));
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
