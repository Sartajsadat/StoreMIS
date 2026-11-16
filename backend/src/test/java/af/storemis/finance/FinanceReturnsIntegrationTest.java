package af.storemis.finance;

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
import java.time.LocalDate;
import java.time.ZoneId;
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

/** Phase 7: returns, expenses, owner cash, transfers and exchange, daily closing. */
class FinanceReturnsIntegrationTest extends IntegrationTest {

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
        categoryId = data(perform(post("/api/v1/categories"), Map.of("name", "Finance cat " + UUID.randomUUID()))).get("id").asLong();
        pieceUnit = jdbc.queryForObject("select id from units where code = 'PIECE'", Long.class);
        afnCash = jdbc.queryForObject("select id from cash_accounts where name = 'AFN Cash'", Long.class);
        usdCash = jdbc.queryForObject("select id from cash_accounts where name = 'USD Cash'", Long.class);
        if (jdbc.queryForObject("select count(*) from cash_transactions where cash_account_id = ?", Integer.class, afnCash) == 0) {
            perform(post("/api/v1/cash-accounts/" + afnCash + "/opening-balance"), Map.of("amount", "1000000"));
            perform(post("/api/v1/cash-accounts/" + usdCash + "/opening-balance"), Map.of("amount", "10000"));
        }
        jdbc.update("update business_settings set block_sale_below_cost = false");
    }

    // ---------------------------------------------------------------- sales returns

    @Test
    void salesReturnFirstReducesWhatIsOwedThenRefundsOrCredits() throws Exception {
        long product = product("Monitor " + SEQ.incrementAndGet(), "10", "3000");
        long customer = customer("Return Customer");
        BigDecimal cashBefore = cash(afnCash);
        JsonNode sale = data(perform(post("/api/v1/sales/confirm"),
                sale(customer, List.of(line(product, "2", "5000")), null, List.of(pay("6000"))))
                .andExpect(status().isCreated()));
        long saleId = sale.get("id").asLong();
        long itemId = sale.get("items").get(0).get("id").asLong();

        JsonNode returnable = data(perform(get("/api/v1/sales/" + saleId + "/returnable-items"), null));
        assertThat(dec(returnable.get("items").get(0).get("unitNetPrice"))).isEqualByComparingTo("5000");

        // 1 sellable unit back: 4,000 still owed is cancelled, 1,000 is paid back in cash.
        JsonNode r1 = data(perform(post("/api/v1/sales-returns"), ret(saleId, "REFUND", afnCash, List.of(rline(itemId, "1", "SELLABLE"))))
                .andExpect(status().isCreated()));
        assertThat(r1.get("returnNo").asText()).matches("SRN-\\d{4}-\\d{6}");
        assertThat(dec(r1.get("totalAmount"))).isEqualByComparingTo("5000");
        assertThat(dec(r1.get("reduction"))).isEqualByComparingTo("4000");
        assertThat(dec(r1.get("cashRefundAmount"))).isEqualByComparingTo("1000");
        assertThat(stockQty(product)).isEqualByComparingTo("9");
        assertThat(cash(afnCash).subtract(cashBefore)).isEqualByComparingTo("5000");
        assertThat(customerBalance(customer)).isEqualByComparingTo("0");
        JsonNode after = data(perform(get("/api/v1/sales/" + saleId), null));
        assertThat(dec(after.get("balanceDue"))).isEqualByComparingTo("0");
        assertThat(dec(after.get("returnedAmount"))).isEqualByComparingTo("5000");
        assertThat(after.get("paymentStatus").asText()).isEqualTo("PAID");

        // The other unit comes back damaged and is kept as customer credit: no stock, no cash.
        JsonNode r2 = data(perform(post("/api/v1/sales-returns"), ret(saleId, "CREDIT", null, List.of(rline(itemId, "1", "DAMAGED"))))
                .andExpect(status().isCreated()));
        assertThat(dec(r2.get("creditAmount"))).isEqualByComparingTo("5000");
        assertThat(stockQty(product)).isEqualByComparingTo("9");
        assertThat(customerBalance(customer)).as("customer credit").isEqualByComparingTo("-5000");
        assertThat(jdbc.queryForObject("select item_condition from sales_return_items where sales_return_id = ?", String.class, r2.get("id").asLong()))
                .isEqualTo("DAMAGED");

        perform(post("/api/v1/sales-returns"), ret(saleId, "CREDIT", null, List.of(rline(itemId, "1", "SELLABLE"))))
                .andExpect(jsonPath("$.error.code").value("RETURN_QUANTITY_EXCEEDED"));
        perform(post("/api/v1/sales/" + saleId + "/cancel"), Map.of("reason", "test", "paymentHandling", "REFUND"))
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
        assertThat(data(perform(get("/api/v1/sales/" + saleId + "/returns"), null))).hasSize(2);
    }

    @Test
    void walkInReturnMustBeRefundedInTheSaleCurrency() throws Exception {
        long product = product("Speaker " + SEQ.incrementAndGet(), "5", "3500");
        JsonNode sale = data(perform(post("/api/v1/sales/confirm"), sale(null, List.of(line(product, "2", "5000")), null, List.of(pay("10000"))))
                .andExpect(status().isCreated()));
        long itemId = sale.get("items").get(0).get("id").asLong();
        long saleId = sale.get("id").asLong();
        perform(post("/api/v1/sales-returns"), ret(saleId, "CREDIT", null, List.of(rline(itemId, "1", "DAMAGED"))))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        perform(post("/api/v1/sales-returns"), ret(saleId, "REFUND", usdCash, List.of(rline(itemId, "1", "DAMAGED"))))
                .andExpect(jsonPath("$.error.code").value("CURRENCY_MISMATCH"));
        BigDecimal before = cash(afnCash);
        perform(post("/api/v1/sales-returns"), ret(saleId, "REFUND", afnCash, List.of(rline(itemId, "1", "DAMAGED")))).andExpect(status().isCreated());
        assertThat(before.subtract(cash(afnCash))).isEqualByComparingTo("5000");
        assertThat(stockQty(product)).as("damaged goods do not go back to stock").isEqualByComparingTo("3");
    }

    @Test
    void returnsUseTheNetPriceAndAddUpExactly() throws Exception {
        long product = product("Cable " + SEQ.incrementAndGet(), "10", "100");
        long customer = customer("Discount Customer");
        JsonNode sale = data(perform(post("/api/v1/sales/confirm"), sale(customer, List.of(line(product, "3", "1000")), "100", List.of()))
                .andExpect(status().isCreated()));
        long saleId = sale.get("id").asLong();
        long itemId = sale.get("items").get(0).get("id").asLong();
        JsonNode a = data(perform(post("/api/v1/sales-returns"), ret(saleId, "CREDIT", null, List.of(rline(itemId, "1", "SELLABLE")))));
        JsonNode b = data(perform(post("/api/v1/sales-returns"), ret(saleId, "CREDIT", null, List.of(rline(itemId, "2", "SELLABLE")))));
        assertThat(dec(a.get("totalAmount"))).isEqualByComparingTo("966.67");
        assertThat(dec(a.get("totalAmount")).add(dec(b.get("totalAmount")))).as("2,900 net in total").isEqualByComparingTo("2900");
        assertThat(customerBalance(customer)).isEqualByComparingTo("0");
    }

    // ---------------------------------------------------------------- purchase returns

    @Test
    void purchaseReturnReducesThePayableThenTheSupplierRefunds() throws Exception {
        long product = product("Printer " + SEQ.incrementAndGet(), null, null);
        long supplier = data(perform(post("/api/v1/suppliers"), Map.of("name", "Return Supplier " + SEQ.incrementAndGet()))).get("id").asLong();
        Map<String, Object> p = new HashMap<>();
        p.put("supplierId", supplier);
        p.put("purchaseDate", LocalDate.now(ZoneId.of("Asia/Kabul")).toString());
        p.put("currency", "AFN");
        p.put("supplierInvoiceNo", "S-" + SEQ.incrementAndGet());
        p.put("items", List.of(Map.of("productId", product, "quantity", "10", "unitCost", "400")));
        long purchaseId = data(perform(post("/api/v1/purchases"), p)).get("id").asLong();
        perform(post("/api/v1/purchases/" + purchaseId + "/confirm"), Map.of("amountPaid", "1000", "cashAccountId", afnCash)).andExpect(status().isOk());
        long itemId = data(perform(get("/api/v1/purchases/" + purchaseId), null)).get("items").get(0).get("id").asLong();
        BigDecimal cashBefore = cash(afnCash);

        JsonNode r1 = data(perform(post("/api/v1/purchase-returns"), ret(purchaseId, null, null, List.of(rline(itemId, "4", null))))
                .andExpect(status().isCreated()));
        assertThat(r1.get("returnNo").asText()).startsWith("PRN-");
        assertThat(dec(r1.get("reduction"))).isEqualByComparingTo("1600");
        assertThat(stockQty(product)).isEqualByComparingTo("6");
        assertThat(supplierBalance(supplier)).isEqualByComparingTo("1400");

        perform(post("/api/v1/purchase-returns"), ret(purchaseId, "REFUND", afnCash, List.of(rline(itemId, "6", null)))).andExpect(status().isCreated());
        assertThat(stockQty(product)).isEqualByComparingTo("0");
        assertThat(cash(afnCash).subtract(cashBefore)).as("supplier gives 1,000 back").isEqualByComparingTo("1000");
        assertThat(supplierBalance(supplier)).isEqualByComparingTo("0");
        JsonNode purchase = data(perform(get("/api/v1/purchases/" + purchaseId), null));
        assertThat(dec(purchase.get("returnedAmount"))).isEqualByComparingTo("4000");
        assertThat(dec(purchase.get("balanceDue"))).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("select inventory_value from product_stock where product_id = ?", BigDecimal.class, product)).isEqualByComparingTo("0");
    }

    // ---------------------------------------------------------------- cash and expenses

    @Test
    void expenseTakesCashAndVoidPutsItBack() throws Exception {
        long rent = jdbc.queryForObject("select id from expense_categories where code = 'RENT'", Long.class);
        BigDecimal before = cash(afnCash);
        Map<String, Object> body = new HashMap<>(Map.of("categoryId", rent, "description", "Shop rent", "amount", "15000", "cashAccountId", afnCash));
        body.put("clientRequestId", UUID.randomUUID());
        JsonNode e = data(perform(post("/api/v1/expenses"), body).andExpect(status().isCreated()));
        perform(post("/api/v1/expenses"), body);
        assertThat(e.get("expenseNo").asText()).startsWith("EXP-");
        assertThat(e.get("currency").asText()).isEqualTo("AFN");
        assertThat(before.subtract(cash(afnCash))).as("retry does not pay twice").isEqualByComparingTo("15000");

        perform(post("/api/v1/expenses/" + e.get("id").asLong() + "/void"), Map.of("reason", "Entered twice")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VOIDED"));
        assertThat(cash(afnCash)).isEqualByComparingTo(before);
        perform(post("/api/v1/expenses/" + e.get("id").asLong() + "/void"), Map.of("reason", "again")).andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
    }

    @Test
    void ownerCashTransferAndExchange() throws Exception {
        BigDecimal afn = cash(afnCash);
        BigDecimal usd = cash(usdCash);
        perform(post("/api/v1/cash-transactions/deposit"), Map.of("accountId", afnCash, "amount", "20000", "notes", "Capital")).andExpect(status().isOk());
        perform(post("/api/v1/cash-transactions/withdrawal"), Map.of("accountId", afnCash, "amount", "5000", "notes", "Owner")).andExpect(status().isOk());
        assertThat(cash(afnCash).subtract(afn)).isEqualByComparingTo("15000");

        JsonNode x = data(perform(post("/api/v1/cash-transfers"), Map.of("type", "EXCHANGE", "fromAccountId", usdCash, "toAccountId", afnCash,
                "fromAmount", "100", "toAmount", "7050")).andExpect(status().isOk()));
        assertThat(dec(x.get("exchangeRate"))).isEqualByComparingTo("70.5");
        assertThat(cash(usdCash).subtract(usd)).isEqualByComparingTo("-100");
        assertThat(cash(afnCash).subtract(afn)).isEqualByComparingTo("22050");
        assertThat(jdbc.queryForObject("select exchange_rate from cash_transactions where source_type = 'CASH_TRANSFER' and source_id = ? and txn_type = 'EXCHANGE_OUT'",
                BigDecimal.class, x.get("id").asLong())).as("USD leg valued at the deal's rate").isEqualByComparingTo("70.5");

        perform(post("/api/v1/cash-transfers"), Map.of("type", "TRANSFER", "fromAccountId", usdCash, "toAccountId", afnCash, "fromAmount", "10"))
                .andExpect(jsonPath("$.error.code").value("CURRENCY_MISMATCH"));
    }

    @Test
    void dailyClosingPostsTheDifferenceAndLocksTheDay() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kabul"));
        JsonNode preview = data(perform(get("/api/v1/cash-closings/preview?date=" + today), null));
        assertThat(preview.get("canClose").asBoolean()).isTrue();
        List<Map<String, Object>> counted = new ArrayList<>();
        BigDecimal afnExpected = null;
        for (JsonNode a : preview.get("accounts")) {
            BigDecimal expected = dec(a.get("expectedBalance"));
            BigDecimal count = expected;
            if (a.get("accountId").asLong() == afnCash) {
                afnExpected = expected;
                count = expected.subtract(new BigDecimal("50"));
            }
            counted.add(Map.of("accountId", a.get("accountId").asLong(), "countedBalance", count.toPlainString()));
        }
        perform(post("/api/v1/cash-closings"), Map.of("date", today.toString(), "accounts", counted))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        try {
            JsonNode closed = data(perform(post("/api/v1/cash-closings"), Map.of("date", today.toString(), "accounts", counted, "notes", "50 short"))
                    .andExpect(status().isOk()));
            assertThat(closed.get("closed").asBoolean()).isTrue();
            assertThat(cash(afnCash)).as("ledger now matches the drawer").isEqualByComparingTo(afnExpected.subtract(new BigDecimal("50")));
            long rent = jdbc.queryForObject("select id from expense_categories where code = 'RENT'", Long.class);
            perform(post("/api/v1/expenses"), Map.of("categoryId", rent, "description", "Late", "amount", "10", "cashAccountId", afnCash))
                    .andExpect(jsonPath("$.error.code").value("DAY_CLOSED"));
        } finally {
            perform(post("/api/v1/cash-closings/" + today + "/reopen"), Map.of("reason", "Test finished")).andExpect(status().isOk());
        }
        JsonNode reopened = data(perform(get("/api/v1/cash-closings/preview?date=" + today), null));
        assertThat(reopened.get("closed").asBoolean()).isFalse();
        assertThat(reopened.get("canClose").asBoolean()).isTrue();
    }

    // ---------------------------------------------------------------- helpers

    private Map<String, Object> sale(Long customer, List<Map<String, Object>> items, String overall, List<Map<String, Object>> payments) {
        Map<String, Object> m = new HashMap<>();
        m.put("customerId", customer);
        m.put("currency", "AFN");
        m.put("overallDiscount", overall);
        m.put("items", items);
        m.put("payments", payments);
        return m;
    }

    private static Map<String, Object> line(long product, String qty, String price) {
        return Map.of("productId", product, "quantity", qty, "unitPrice", price);
    }

    private Map<String, Object> pay(String amount) {
        return Map.of("currency", "AFN", "amount", amount, "cashAccountId", afnCash);
    }

    private static Map<String, Object> ret(long documentId, String settlement, Long account, List<Map<String, Object>> items) {
        Map<String, Object> m = new HashMap<>();
        m.put("documentId", documentId);
        m.put("reason", "Customer changed mind");
        m.put("settlement", settlement);
        m.put("refundCashAccountId", account);
        m.put("items", items);
        return m;
    }

    private static Map<String, Object> rline(long itemId, String qty, String condition) {
        Map<String, Object> m = new HashMap<>();
        m.put("itemId", itemId);
        m.put("quantity", qty);
        m.put("condition", condition);
        return m;
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

    private long customer(String name) throws Exception {
        return data(perform(post("/api/v1/customers"), Map.of("name", name + " " + SEQ.incrementAndGet())).andExpect(status().isCreated())).get("id").asLong();
    }

    private BigDecimal stockQty(long product) {
        return jdbc.queryForObject("select quantity_on_hand from product_stock where product_id = ?", BigDecimal.class, product);
    }

    private BigDecimal cash(long account) {
        return jdbc.queryForObject("select current_balance from cash_accounts where id = ?", BigDecimal.class, account);
    }

    private BigDecimal customerBalance(long customer) throws Exception {
        JsonNode b = data(perform(get("/api/v1/customers/" + customer), null)).get("balances").get("AFN");
        return b == null ? BigDecimal.ZERO : new BigDecimal(b.asText());
    }

    private BigDecimal supplierBalance(long supplier) throws Exception {
        JsonNode b = data(perform(get("/api/v1/suppliers/" + supplier), null)).get("balances").get("AFN");
        return b == null ? BigDecimal.ZERO : new BigDecimal(b.asText());
    }

    private ResultActions perform(MockHttpServletRequestBuilder req, Object body) throws Exception {
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
