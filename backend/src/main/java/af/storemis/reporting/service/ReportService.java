package af.storemis.reporting.service;

import static af.storemis.reporting.dto.ReportColumn.balances;
import static af.storemis.reporting.dto.ReportColumn.base;
import static af.storemis.reporting.dto.ReportColumn.code;
import static af.storemis.reporting.dto.ReportColumn.date;
import static af.storemis.reporting.dto.ReportColumn.enumOf;
import static af.storemis.reporting.dto.ReportColumn.money;
import static af.storemis.reporting.dto.ReportColumn.number;
import static af.storemis.reporting.dto.ReportColumn.percent;
import static af.storemis.reporting.dto.ReportColumn.qty;
import static af.storemis.reporting.dto.ReportColumn.text;
import static af.storemis.reporting.dto.ReportColumn.userText;
import static af.storemis.reporting.service.ProfitLossService.dec;

import af.storemis.common.exception.NotFoundException;
import af.storemis.common.money.Money;
import af.storemis.currency.service.CurrencyService;
import af.storemis.reporting.dto.ReportColumn;
import af.storemis.reporting.dto.ReportQuery;
import af.storemis.reporting.dto.ReportResult;
import af.storemis.reporting.service.ProfitLossService.ProfitLoss;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The report catalogue (design: API plan, reports). All reports are read-only SQL over the posted documents and
 * ledgers. Money is converted to the base currency at each document's own rate unless a currency column says otherwise.
 */
@Service
public class ReportService {

    public static final Set<String> KEYS = Set.of("sales-daily", "sales-monthly", "sales-yearly", "sales-by-type", "best-selling", "purchases",
            "suppliers", "inventory", "stock-movements", "low-stock", "damaged-stock", "customer-balances", "supplier-balances", "customer-payments",
            "expenses", "cash-transactions", "profit-loss");

    private final ReportSql sql;
    private final ProfitLossService profitLoss;
    private final CurrencyService currency;

    public ReportService(ReportSql sql, ProfitLossService profitLoss, CurrencyService currency) {
        this.sql = sql;
        this.profitLoss = profitLoss;
        this.currency = currency;
    }

    @Transactional(readOnly = true)
    public ReportResult run(String key, ReportQuery q) {
        return switch (key) {
            case "sales-daily" -> salesByPeriod(key, q, "day");
            case "sales-monthly" -> salesByPeriod(key, q, "month");
            case "sales-yearly" -> salesByPeriod(key, q, "year");
            case "sales-by-type" -> salesByType(q);
            case "best-selling" -> bestSelling(q);
            case "purchases" -> purchases(q);
            case "suppliers" -> suppliers(q);
            case "inventory" -> inventory(q);
            case "stock-movements" -> stockMovements(q);
            case "low-stock" -> lowStock(q);
            case "damaged-stock" -> damagedStock(q);
            case "customer-balances" -> partyBalances(q, true);
            case "supplier-balances" -> partyBalances(q, false);
            case "customer-payments" -> customerPayments(q);
            case "expenses" -> expenses(q);
            case "cash-transactions" -> cashTransactions(q);
            case "profit-loss" -> profitLoss(q);
            default -> throw new NotFoundException("Report", key);
        };
    }

    // ---------------------------------------------------------------- sales

    private ReportResult salesByPeriod(String key, ReportQuery q, String unit) {
        // unit comes from a fixed list, never from the request.
        String sqlText = """
                with s as (
                    select date_trunc('%1$s', business_date::timestamp)::date as p, count(*) as n,
                           sum(round(subtotal * exchange_rate, 2)) as gross,
                           sum(round((item_discount_total + overall_discount) * exchange_rate, 2)) as disc,
                           sum(total_cost_base) as cost
                    from sales where status = 'CONFIRMED' and business_date between :from and :to group by 1),
                r as (
                    select date_trunc('%1$s', business_date::timestamp)::date as p, sum(round(total_amount * exchange_rate, 2)) as ret,
                           sum(total_cost_base) as rcost
                    from sales_returns where business_date between :from and :to group by 1)
                select coalesce(s.p, r.p) as "period", coalesce(s.n, 0) as "invoices", coalesce(s.gross, 0) as "grossSales",
                       coalesce(s.disc, 0) as "discounts", coalesce(r.ret, 0) as "returns",
                       coalesce(s.gross, 0) - coalesce(s.disc, 0) - coalesce(r.ret, 0) as "netSales",
                       coalesce(s.cost, 0) - coalesce(r.rcost, 0) as "cogs"
                from s full join r on r.p = s.p order by 1""".formatted(unit);
        List<Map<String, Object>> rows = sql.list(sqlText, range(q));
        for (Map<String, Object> r : rows) withProfit(r);
        List<ReportColumn> cols = List.of(date("period"), number("invoices"), base("grossSales"), base("discounts"), base("returns"), base("netSales"),
                base("cogs"), base("grossProfit"), percent("margin"));
        Map<String, Object> totals = sum(rows, "invoices", "grossSales", "discounts", "returns", "netSales", "cogs", "grossProfit");
        withProfit(totals);
        totals.put("grossProfit", dec(totals.get("netSales")).subtract(dec(totals.get("cogs"))));
        return all(key, q, cols, rows, totals, Map.of("unit", unit));
    }

    private ReportResult salesByType(ReportQuery q) {
        List<Map<String, Object>> rows = sql.list("""
                with s as (
                    select sale_type as t, count(*) as n, sum(round(grand_total * exchange_rate, 2)) as net, sum(total_cost_base) as cost
                    from sales where status = 'CONFIRMED' and business_date between :from and :to group by sale_type),
                r as (
                    select s.sale_type as t, sum(round(r.total_amount * r.exchange_rate, 2)) as ret, sum(r.total_cost_base) as rcost
                    from sales_returns r join sales s on s.id = r.sale_id where r.business_date between :from and :to group by s.sale_type)
                select x.t as "saleType", coalesce(s.n, 0) as "invoices", coalesce(s.net, 0) - coalesce(r.ret, 0) as "netSales",
                       coalesce(s.cost, 0) - coalesce(r.rcost, 0) as "cogs"
                from (values ('RETAIL'), ('WHOLESALE')) as x(t) left join s on s.t = x.t left join r on r.t = x.t order by 1""", range(q));
        for (Map<String, Object> r : rows) withProfit(r);
        Map<String, Object> totals = sum(rows, "invoices", "netSales", "cogs", "grossProfit");
        BigDecimal net = dec(totals.get("netSales"));
        for (Map<String, Object> r : rows) {
            r.put("share", net.signum() == 0 ? BigDecimal.ZERO : dec(r.get("netSales")).multiply(BigDecimal.valueOf(100)).divide(net, 1, RoundingMode.HALF_UP));
        }
        withProfit(totals);
        totals.put("share", net.signum() == 0 ? null : BigDecimal.valueOf(100));
        List<ReportColumn> cols = List.of(enumOf("saleType", "saleTypes"), number("invoices"), base("netSales"), percent("share"), base("cogs"),
                base("grossProfit"), percent("margin"));
        return all("sales-by-type", q, cols, rows, totals, Map.of());
    }

    private ReportResult bestSelling(ReportQuery q) {
        MapSqlParameterSource p = range(q).addValue("q", like(q.q())).addValue("categoryId", q.categoryId());
        String body = """
                with sold as (
                    select si.product_id, sum(si.quantity) as qty, sum(round((si.line_total - si.overall_discount_share) * s.exchange_rate, 2)) as revenue,
                           sum(si.line_cost_base) as cost
                    from sale_items si join sales s on s.id = si.sale_id
                    where s.status = 'CONFIRMED' and s.business_date between :from and :to group by si.product_id),
                ret as (
                    select ri.product_id, sum(ri.quantity) as qty, sum(round(ri.line_total * r.exchange_rate, 2)) as revenue,
                           sum(round(ri.quantity * ri.unit_cost_base, 2)) as cost
                    from sales_return_items ri join sales_returns r on r.id = ri.sales_return_id
                    where r.business_date between :from and :to group by ri.product_id)
                select p.id as "productId", p.code as "productCode", p.name as "productName", c.name as "categoryName", u.name as "unitName",
                       coalesce(sold.qty, 0) - coalesce(ret.qty, 0) as "quantity",
                       coalesce(sold.revenue, 0) - coalesce(ret.revenue, 0) as "netSales",
                       coalesce(sold.cost, 0) - coalesce(ret.cost, 0) as "cogs",
                       coalesce(sold.revenue, 0) - coalesce(ret.revenue, 0) - coalesce(sold.cost, 0) + coalesce(ret.cost, 0) as "grossProfit"
                from sold full join ret using (product_id)
                join products p on p.id = product_id join categories c on c.id = p.category_id join units u on u.id = p.unit_id
                where (cast(:q as text) is null or lower(p.name) like :q or lower(coalesce(p.code, '')) like :q)
                  and (cast(:categoryId as bigint) is null or p.category_id = :categoryId)""";
        String order = "REVENUE".equals(q.type()) ? "\"netSales\" desc, \"quantity\" desc" : "\"quantity\" desc, \"netSales\" desc";
        long count = sql.count(body, p);
        List<Map<String, Object>> rows = sql.page(body, order, p, q.page(), q.size());
        int rank = q.page() * q.size();
        for (Map<String, Object> r : rows) {
            r.put("rank", ++rank);
            r.put("margin", margin(r));
        }
        Map<String, Object> totals = sql.one("select sum(\"netSales\") as \"netSales\", sum(\"cogs\") as \"cogs\", sum(\"grossProfit\") as \"grossProfit\" from ("
                + body + ") x", p);
        totals.put("margin", margin(totals));
        List<ReportColumn> cols = List.of(number("rank"), code("productCode"), userText("productName"), userText("categoryName"), qty("quantity", "unitName"),
                base("netSales"), base("cogs"), base("grossProfit"), percent("margin"));
        return paged("best-selling", q, cols, rows, totals, count, Map.of());
    }

    // ---------------------------------------------------------------- purchases and suppliers

    private ReportResult purchases(ReportQuery q) {
        MapSqlParameterSource p = range(q).addValue("q", like(q.q())).addValue("currency", blank(q.currency())).addValue("status", blank(q.status()));
        String body = """
                select p.id as "id", p.purchase_no as "purchaseNo", p.business_date as "date", s.name as "supplierName",
                       p.supplier_invoice_no as "supplierInvoiceNo", rtrim(p.currency_code) as "currency", p.grand_total as "grandTotal",
                       p.amount_paid as "amountPaid", p.returned_amount as "returnedAmount", p.balance_due as "balanceDue",
                       round(p.grand_total * p.exchange_rate, 2) as "baseTotal", p.payment_status as "paymentStatus"
                from purchases p join suppliers s on s.id = p.supplier_id
                where p.status = 'CONFIRMED' and p.business_date between :from and :to
                  and (cast(:q as text) is null or lower(p.purchase_no) like :q or lower(s.name) like :q or lower(coalesce(p.supplier_invoice_no, '')) like :q)
                  and (cast(:currency as text) is null or p.currency_code = :currency)
                  and (cast(:status as text) is null or p.payment_status = :status)""";
        long count = sql.count(body, p);
        List<Map<String, Object>> rows = sql.page(body, "\"date\" desc, \"id\" desc", p, q.page(), q.size());
        Map<String, Object> totals = sql.one("select sum(\"baseTotal\") as \"baseTotal\", count(*) as \"count\" from (" + body + ") x", p);
        totals.put("byCurrency", perCurrency("select \"currency\" as c, sum(\"grandTotal\") as a, sum(\"amountPaid\") as b, sum(\"balanceDue\") as d from ("
                + body + ") x group by 1", p, "grandTotal", "amountPaid", "balanceDue"));
        List<ReportColumn> cols = List.of(code("purchaseNo"), date("date"), userText("supplierName"), code("supplierInvoiceNo"),
                money("grandTotal", "currency"), money("amountPaid", "currency"), money("returnedAmount", "currency"), money("balanceDue", "currency"),
                base("baseTotal"), enumOf("paymentStatus", "status"));
        return paged("purchases", q, cols, rows, totals, count, Map.of());
    }

    private ReportResult suppliers(ReportQuery q) {
        MapSqlParameterSource p = range(q).addValue("q", like(q.q()));
        List<Map<String, Object>> rows = sql.list("""
                select * from (
                    select s.id as "supplierId", s.name as "supplierName", s.phone as "phone",
                        (select count(*) from purchases x where x.supplier_id = s.id and x.status = 'CONFIRMED' and x.business_date between :from and :to) as "purchases",
                        (select coalesce(sum(round(x.grand_total * x.exchange_rate, 2)), 0) from purchases x
                         where x.supplier_id = s.id and x.status = 'CONFIRMED' and x.business_date between :from and :to) as "purchasesBase",
                        (select coalesce(sum(round(x.total_amount * x.exchange_rate, 2)), 0) from purchase_returns x
                         where x.supplier_id = s.id and x.business_date between :from and :to) as "returnsBase",
                        (select coalesce(sum(round(x.amount * x.exchange_rate, 2)), 0) from supplier_payments x
                         where x.supplier_id = s.id and x.status = 'POSTED' and x.business_date between :from and :to) as "paymentsBase"
                    from suppliers s
                    where cast(:q as text) is null or lower(s.name) like :q or lower(coalesce(s.phone, '')) like :q) t
                where "purchases" > 0 or "returnsBase" > 0 or "paymentsBase" > 0
                   or exists (select 1 from supplier_ledger_entries e where e.supplier_id = t."supplierId" group by e.currency_code having sum(e.credit - e.debit) <> 0)
                order by "purchasesBase" desc, "supplierName" """, p);
        Map<Long, Map<String, BigDecimal>> bal = ledgerBalances(false);
        for (Map<String, Object> r : rows) r.put("balances", bal.getOrDefault(((Number) r.get("supplierId")).longValue(), Map.of()));
        Map<String, Object> totals = sum(rows, "purchases", "purchasesBase", "returnsBase", "paymentsBase");
        totals.put("balances", totalBalances(rows));
        List<ReportColumn> cols = List.of(userText("supplierName"), code("phone"), number("purchases"), base("purchasesBase"), base("returnsBase"),
                base("paymentsBase"), balances("balances"));
        return all("suppliers", q, cols, rows, totals, Map.of());
    }

    // ---------------------------------------------------------------- inventory

    private static final String STOCK_STATUS = """
            case when st.quantity_on_hand <= 0 then 'OUT_OF_STOCK'
                 when p.min_stock_level > 0 and st.quantity_on_hand <= p.min_stock_level then 'LOW_STOCK' else 'IN_STOCK' end""";

    private ReportResult inventory(ReportQuery q) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("q", like(q.q())).addValue("categoryId", q.categoryId())
                .addValue("status", blank(q.status()));
        String body = """
                select * from (
                    select p.id as "productId", p.code as "productCode", p.name as "productName", c.name as "categoryName", u.name as "unitName",
                           st.quantity_on_hand as "quantity", st.avg_unit_cost as "avgCost", st.inventory_value as "value",
                           p.min_stock_level as "minStock", %s as "stockStatus"
                    from products p join product_stock st on st.product_id = p.id join categories c on c.id = p.category_id join units u on u.id = p.unit_id
                    where p.active and (cast(:q as text) is null or lower(p.name) like :q or lower(coalesce(p.code, '')) like :q)
                      and (cast(:categoryId as bigint) is null or p.category_id = :categoryId)) t
                where cast(:status as text) is null or "stockStatus" = :status""".formatted(STOCK_STATUS);
        long count = sql.count(body, p);
        List<Map<String, Object>> rows = sql.page(body, "\"productName\"", p, q.page(), q.size());
        Map<String, Object> totals = sql.one("select coalesce(sum(\"value\"), 0) as \"value\", count(*) as \"count\" from (" + body + ") x", p);
        List<ReportColumn> cols = List.of(code("productCode"), userText("productName"), userText("categoryName"), qty("quantity", "unitName"),
                base("avgCost"), base("value"), qty("minStock", "unitName"), enumOf("stockStatus", "status"));
        return paged("inventory", q, cols, rows, totals, count, Map.of());
    }

    private ReportResult stockMovements(ReportQuery q) {
        MapSqlParameterSource p = range(q).addValue("q", like(q.q())).addValue("type", blank(q.type())).addValue("categoryId", q.categoryId());
        String body = """
                select m.id as "id", m.business_date as "date", m.reference_no as "reference", p.code as "productCode", p.name as "productName",
                       u.name as "unitName", m.movement_type as "movementType", m.quantity as "quantity", m.unit_cost_base as "unitCost",
                       m.total_cost_base as "totalCost", m.quantity_after as "quantityAfter", usr.full_name as "userName", m.notes as "notes"
                from stock_movements m join products p on p.id = m.product_id join units u on u.id = p.unit_id
                left join users usr on usr.id = m.created_by
                where m.business_date between :from and :to
                  and (cast(:type as text) is null or m.movement_type = :type)
                  and (cast(:categoryId as bigint) is null or p.category_id = :categoryId)
                  and (cast(:q as text) is null or lower(p.name) like :q or lower(coalesce(p.code, '')) like :q or lower(coalesce(m.reference_no, '')) like :q)""";
        long count = sql.count(body, p);
        List<Map<String, Object>> rows = sql.page(body, "\"id\" desc", p, q.page(), q.size());
        Map<String, Object> totals = sql.one("""
                select coalesce(sum(case when "totalCost" > 0 then "totalCost" end), 0) as "valueIn",
                       coalesce(sum(case when "totalCost" < 0 then -"totalCost" end), 0) as "valueOut" from (""" + body + ") x", p);
        List<ReportColumn> cols = List.of(date("date"), code("reference"), code("productCode"), userText("productName"),
                enumOf("movementType", "inventory.movementTypes"), qty("quantity", "unitName"), base("unitCost"), base("totalCost"),
                qty("quantityAfter", "unitName"), userText("userName"), userText("notes"));
        return paged("stock-movements", q, cols, rows, totals, count, Map.of());
    }

    private ReportResult lowStock(ReportQuery q) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("q", like(q.q())).addValue("categoryId", q.categoryId());
        String body = """
                select p.id as "productId", p.code as "productCode", p.name as "productName", c.name as "categoryName", u.name as "unitName",
                       st.quantity_on_hand as "quantity", p.min_stock_level as "minStock",
                       greatest(p.min_stock_level - st.quantity_on_hand, 0) as "shortage", st.avg_unit_cost as "avgCost",
                       round(greatest(p.min_stock_level - st.quantity_on_hand, 0) * st.avg_unit_cost, 2) as "reorderValue", %s as "stockStatus"
                from products p join product_stock st on st.product_id = p.id join categories c on c.id = p.category_id join units u on u.id = p.unit_id
                where p.active and p.min_stock_level > 0 and st.quantity_on_hand <= p.min_stock_level
                  and (cast(:q as text) is null or lower(p.name) like :q or lower(coalesce(p.code, '')) like :q)
                  and (cast(:categoryId as bigint) is null or p.category_id = :categoryId)""".formatted(STOCK_STATUS);
        long count = sql.count(body, p);
        List<Map<String, Object>> rows = sql.page(body, "\"quantity\" <= 0 desc, \"shortage\" desc, \"productName\"", p, q.page(), q.size());
        Map<String, Object> totals = sql.one("select coalesce(sum(\"reorderValue\"), 0) as \"reorderValue\", count(*) as \"count\" from (" + body + ") x", p);
        List<ReportColumn> cols = List.of(code("productCode"), userText("productName"), userText("categoryName"), qty("quantity", "unitName"),
                qty("minStock", "unitName"), qty("shortage", "unitName"), base("avgCost"), base("reorderValue"), enumOf("stockStatus", "status"));
        return paged("low-stock", q, cols, rows, totals, count, Map.of());
    }

    private ReportResult damagedStock(ReportQuery q) {
        MapSqlParameterSource p = range(q).addValue("q", like(q.q()));
        String body = """
                select * from (
                    select m.business_date as "date", m.reference_no as "reference", p.code as "productCode", p.name as "productName", u.name as "unitName",
                           -m.quantity as "quantity", m.unit_cost_base as "unitCost", -m.total_cost_base as "totalCost", 'DAMAGE' as "source",
                           m.notes as "notes", m.id as "sortId"
                    from stock_movements m join products p on p.id = m.product_id join units u on u.id = p.unit_id
                    where m.movement_type = 'DAMAGE' and m.business_date between :from and :to
                    union all
                    select r.business_date, r.return_no, p.code, p.name, u.name, i.quantity, i.unit_cost_base, round(i.quantity * i.unit_cost_base, 2),
                           'DAMAGED_RETURN', coalesce(i.reason, r.reason), i.id
                    from sales_return_items i join sales_returns r on r.id = i.sales_return_id join products p on p.id = i.product_id
                    join units u on u.id = p.unit_id
                    where i.item_condition = 'DAMAGED' and r.business_date between :from and :to) t
                where cast(:q as text) is null or lower("productName") like :q or lower(coalesce("productCode", '')) like :q
                   or lower(coalesce("reference", '')) like :q""";
        long count = sql.count(body, p);
        List<Map<String, Object>> rows = sql.page(body, "\"date\" desc, \"sortId\" desc", p, q.page(), q.size());
        Map<String, Object> totals = sql.one("select coalesce(sum(\"totalCost\"), 0) as \"totalCost\", count(*) as \"count\" from (" + body + ") x", p);
        List<ReportColumn> cols = List.of(date("date"), code("reference"), enumOf("source", "reports.sources"), code("productCode"), userText("productName"),
                qty("quantity", "unitName"), base("unitCost"), base("totalCost"), userText("notes"));
        return paged("damaged-stock", q, cols, rows, totals, count, Map.of());
    }

    // ---------------------------------------------------------------- balances and payments

    private ReportResult partyBalances(ReportQuery q, boolean customers) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("q", like(q.q())).addValue("nonZero", q.nonZero()).addValue("type", blank(q.type()));
        String body = customers ? """
                select c.id as "partyId", c.name as "partyName", c.phone as "phone", c.customer_type as "customerType",
                       (select max(s.business_date) from sales s where s.customer_id = c.id and s.status = 'CONFIRMED') as "lastSale",
                       (select max(x.business_date) from customer_payments x where x.customer_id = c.id and x.status = 'POSTED') as "lastPayment"
                from customers c
                where not c.is_walk_in
                  and (cast(:q as text) is null or lower(c.name) like :q or lower(coalesce(c.phone, '')) like :q)
                  and (cast(:type as text) is null or c.customer_type = :type)
                  and (not :nonZero or exists (select 1 from customer_ledger_entries e where e.customer_id = c.id
                                               group by e.currency_code having sum(e.debit - e.credit) <> 0))"""
                : """
                select s.id as "partyId", s.name as "partyName", s.phone as "phone",
                       (select max(x.business_date) from purchases x where x.supplier_id = s.id and x.status = 'CONFIRMED') as "lastPurchase",
                       (select max(x.business_date) from supplier_payments x where x.supplier_id = s.id and x.status = 'POSTED') as "lastPayment"
                from suppliers s
                where (cast(:q as text) is null or lower(s.name) like :q or lower(coalesce(s.phone, '')) like :q)
                  and (not :nonZero or exists (select 1 from supplier_ledger_entries e where e.supplier_id = s.id
                                               group by e.currency_code having sum(e.credit - e.debit) <> 0))""";
        long count = sql.count(body, p);
        List<Map<String, Object>> rows = sql.page(body, "\"partyName\"", p, q.page(), q.size());
        Map<Long, Map<String, BigDecimal>> bal = ledgerBalances(customers);
        for (Map<String, Object> r : rows) r.put("balances", bal.getOrDefault(((Number) r.get("partyId")).longValue(), Map.of()));
        // Totals over everyone: what is owed (positive balances) and what is held as credit (negative), per currency.
        Map<String, BigDecimal> owed = new TreeMap<>();
        Map<String, BigDecimal> credit = new TreeMap<>();
        bal.values().forEach(m -> m.forEach((cur, v) -> {
            if (v.signum() > 0) owed.merge(cur, v, BigDecimal::add);
            else if (v.signum() < 0) credit.merge(cur, v.negate(), BigDecimal::add);
        }));
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("owed", owed);
        totals.put("credit", credit);
        totals.put("count", count);
        List<ReportColumn> cols = customers
                ? List.of(userText("partyName"), code("phone"), enumOf("customerType", "saleTypes"), balances("balances"), date("lastSale"), date("lastPayment"))
                : List.of(userText("partyName"), code("phone"), balances("balances"), date("lastPurchase"), date("lastPayment"));
        return paged(customers ? "customer-balances" : "supplier-balances", q, cols, rows, totals, count, Map.of());
    }

    private ReportResult customerPayments(ReportQuery q) {
        MapSqlParameterSource p = range(q).addValue("q", like(q.q())).addValue("currency", blank(q.currency())).addValue("status", blank(q.status()))
                .addValue("accountId", q.accountId());
        String body = """
                select x.id as "id", x.payment_no as "paymentNo", x.business_date as "date", c.name as "customerName", x.payment_kind as "kind",
                       rtrim(x.currency_code) as "currency", x.amount as "amount", round(x.amount * x.exchange_rate, 2) as "baseAmount",
                       a.name as "accountName", x.status as "status", u.full_name as "userName"
                from customer_payments x join customers c on c.id = x.customer_id join cash_accounts a on a.id = x.cash_account_id
                left join users u on u.id = x.created_by
                where x.business_date between :from and :to
                  and (cast(:q as text) is null or lower(x.payment_no) like :q or lower(c.name) like :q)
                  and (cast(:currency as text) is null or x.currency_code = :currency)
                  and (cast(:status as text) is null or x.status = :status)
                  and (cast(:accountId as bigint) is null or x.cash_account_id = :accountId)""";
        long count = sql.count(body, p);
        List<Map<String, Object>> rows = sql.page(body, "\"date\" desc, \"id\" desc", p, q.page(), q.size());
        Map<String, Object> totals = sql.one("select coalesce(sum(case when \"status\" = 'POSTED' then \"baseAmount\" end), 0) as \"baseAmount\", count(*) as \"count\" from ("
                + body + ") x", p);
        totals.put("byCurrency", perCurrency("select \"currency\" as c, sum(\"amount\") as a from (" + body + ") x where \"status\" = 'POSTED' group by 1", p,
                "amount"));
        List<ReportColumn> cols = List.of(code("paymentNo"), date("date"), userText("customerName"), enumOf("kind", "customerPayments.kind"),
                money("amount", "currency"), base("baseAmount"), text("accountName"), enumOf("status", "status"), userText("userName"));
        return paged("customer-payments", q, cols, rows, totals, count, Map.of());
    }

    // ---------------------------------------------------------------- finance

    private ReportResult expenses(ReportQuery q) {
        MapSqlParameterSource p = range(q).addValue("q", like(q.q())).addValue("categoryId", q.categoryId())
                .addValue("status", q.status() == null || q.status().isBlank() ? "POSTED" : q.status()).addValue("accountId", q.accountId());
        String body = """
                select e.id as "id", e.expense_no as "expenseNo", e.expense_date as "date", coalesce(c.code, c.name) as "category",
                       e.description as "description", rtrim(e.currency_code) as "currency", e.amount as "amount",
                       round(e.amount * e.exchange_rate, 2) as "baseAmount", a.name as "accountName", u.full_name as "userName", e.status as "status"
                from expenses e join expense_categories c on c.id = e.category_id join cash_accounts a on a.id = e.cash_account_id
                left join users u on u.id = e.created_by
                where e.business_date between :from and :to and (:status = 'ALL' or e.status = :status)
                  and (cast(:categoryId as bigint) is null or e.category_id = :categoryId)
                  and (cast(:accountId as bigint) is null or e.cash_account_id = :accountId)
                  and (cast(:q as text) is null or lower(e.expense_no) like :q or lower(e.description) like :q)""";
        long count = sql.count(body, p);
        List<Map<String, Object>> rows = sql.page(body, "\"date\" desc, \"id\" desc", p, q.page(), q.size());
        Map<String, Object> totals = sql.one("select coalesce(sum(case when \"status\" = 'POSTED' then \"baseAmount\" end), 0) as \"baseAmount\", count(*) as \"count\" from ("
                + body + ") x", p);
        totals.put("byCurrency", perCurrency("select \"currency\" as c, sum(\"amount\") as a from (" + body + ") x where \"status\" = 'POSTED' group by 1", p,
                "amount"));
        List<Map<String, Object>> byCategory = sql.list("select \"category\", sum(\"baseAmount\") as \"amount\" from (" + body
                + ") x where \"status\" = 'POSTED' group by 1 order by 2 desc", p);
        List<ReportColumn> cols = List.of(code("expenseNo"), date("date"), enumOf("category", "expenses.categoryCodes"), userText("description"),
                money("amount", "currency"), base("baseAmount"), text("accountName"), userText("userName"), enumOf("status", "status"));
        return paged("expenses", q, cols, rows, totals, count, Map.of("byCategory", byCategory));
    }

    private ReportResult cashTransactions(ReportQuery q) {
        MapSqlParameterSource p = range(q).addValue("q", like(q.q())).addValue("type", blank(q.type())).addValue("accountId", q.accountId());
        String body = """
                select t.id as "id", t.business_date as "date", t.txn_no as "txnNo", a.name as "accountName", rtrim(a.currency_code) as "currency",
                       t.txn_type as "txnType", case when t.direction = 'IN' then t.amount end as "amountIn",
                       case when t.direction = 'OUT' then t.amount end as "amountOut", t.balance_after as "balanceAfter",
                       t.reference_no as "reference", t.description as "description", u.full_name as "userName"
                from cash_transactions t join cash_accounts a on a.id = t.cash_account_id left join users u on u.id = t.created_by
                where t.business_date between :from and :to
                  and (cast(:type as text) is null or t.txn_type = :type)
                  and (cast(:accountId as bigint) is null or t.cash_account_id = :accountId)
                  and (cast(:q as text) is null or lower(t.txn_no) like :q or lower(coalesce(t.reference_no, '')) like :q
                       or lower(coalesce(t.description, '')) like :q)""";
        long count = sql.count(body, p);
        List<Map<String, Object>> rows = sql.page(body, "\"id\" desc", p, q.page(), q.size());
        // Per account for the whole period (not only the filtered rows): opening, money in, money out, closing.
        List<Map<String, Object>> byAccount = sql.list("""
                select a.id as "accountId", a.name as "accountName", rtrim(a.currency_code) as "currency",
                       coalesce(sum(case when t.business_date < :from then case when t.direction = 'IN' then t.amount else -t.amount end end), 0) as "opening",
                       coalesce(sum(case when t.business_date between :from and :to and t.direction = 'IN' then t.amount end), 0) as "totalIn",
                       coalesce(sum(case when t.business_date between :from and :to and t.direction = 'OUT' then t.amount end), 0) as "totalOut",
                       coalesce(sum(case when t.business_date <= :to then case when t.direction = 'IN' then t.amount else -t.amount end end), 0) as "closing"
                from cash_accounts a left join cash_transactions t on t.cash_account_id = a.id
                group by a.id, a.name, a.currency_code order by a.id""", p);
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("count", count);
        List<ReportColumn> cols = List.of(date("date"), code("txnNo"), text("accountName"), enumOf("txnType", "cash.types"), money("amountIn", "currency"),
                money("amountOut", "currency"), money("balanceAfter", "currency"), code("reference"), userText("description"), userText("userName"));
        return paged("cash-transactions", q, cols, rows, totals, count, Map.of("byAccount", byAccount));
    }

    private ReportResult profitLoss(ReportQuery q) {
        ProfitLoss pl = profitLoss.compute(q.from(), q.to());
        long days = q.to().toEpochDay() - q.from().toEpochDay() + 1;
        ProfitLoss previous = profitLoss.compute(q.from().minusDays(days), q.from().minusDays(1));
        List<Map<String, Object>> rows = profitLoss.statement(pl);
        List<Map<String, Object>> prevRows = profitLoss.statement(previous);
        // Match previous-period figures by line (and category for expense details).
        for (Map<String, Object> r : rows) {
            Object prev = prevRows.stream().filter(x -> x.get("line").equals(r.get("line")) && java.util.Objects.equals(x.get("category"), r.get("category")))
                    .map(x -> x.get("amount")).findFirst().orElse(Money.ZERO);
            r.put("previous", prev);
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("summary", pl);
        meta.put("grossMargin", pl.grossMarginPercent());
        meta.put("previousFrom", previous.from());
        meta.put("previousTo", previous.to());
        meta.put("previousNetProfit", previous.netProfit());
        List<ReportColumn> cols = List.of(enumOf("line", "reports.pl"), base("amount"), base("previous"));
        return all("profit-loss", q, cols, rows, Map.of(), meta);
    }

    // ---------------------------------------------------------------- helpers

    /** Balance per party and currency from the ledger. Customers: what they owe; suppliers: what we owe. */
    private Map<Long, Map<String, BigDecimal>> ledgerBalances(boolean customers) {
        String s = customers
                ? "select customer_id as id, rtrim(currency_code) as c, sum(debit - credit) as b from customer_ledger_entries group by 1, 2 having sum(debit - credit) <> 0"
                : "select supplier_id as id, rtrim(currency_code) as c, sum(credit - debit) as b from supplier_ledger_entries group by 1, 2 having sum(credit - debit) <> 0";
        Map<Long, Map<String, BigDecimal>> out = new LinkedHashMap<>();
        for (Map<String, Object> r : sql.list(s, new MapSqlParameterSource())) {
            out.computeIfAbsent(((Number) r.get("id")).longValue(), k -> new TreeMap<>()).put((String) r.get("c"), dec(r.get("b")));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, BigDecimal> totalBalances(List<Map<String, Object>> rows) {
        Map<String, BigDecimal> t = new TreeMap<>();
        for (Map<String, Object> r : rows) ((Map<String, BigDecimal>) r.get("balances")).forEach((c, v) -> t.merge(c, v, BigDecimal::add));
        return t;
    }

    /** {currency: {name: amount}} from a query returning c, a[, b, d]. */
    private Map<String, Map<String, BigDecimal>> perCurrency(String query, MapSqlParameterSource p, String... names) {
        Map<String, Map<String, BigDecimal>> out = new TreeMap<>();
        String[] cols = {"a", "b", "d"};
        for (Map<String, Object> r : sql.list(query, p)) {
            Map<String, BigDecimal> m = new LinkedHashMap<>();
            for (int i = 0; i < names.length; i++) m.put(names[i], dec(r.get(cols[i])));
            out.put((String) r.get("c"), m);
        }
        return out;
    }

    private static void withProfit(Map<String, Object> r) {
        if (r.containsKey("netSales") && r.containsKey("cogs")) r.put("grossProfit", dec(r.get("netSales")).subtract(dec(r.get("cogs"))));
        r.put("margin", margin(r));
    }

    private static BigDecimal margin(Map<String, Object> r) {
        BigDecimal net = dec(r.get("netSales"));
        return net.signum() == 0 ? null : dec(r.get("grossProfit")).multiply(BigDecimal.valueOf(100)).divide(net, 1, RoundingMode.HALF_UP);
    }

    private static Map<String, Object> sum(List<Map<String, Object>> rows, String... keys) {
        Map<String, Object> t = new LinkedHashMap<>();
        for (String k : keys) {
            BigDecimal s = BigDecimal.ZERO;
            for (Map<String, Object> r : rows) if (r.get(k) != null) s = s.add(new BigDecimal(r.get(k).toString()));
            t.put(k, k.equals("invoices") || k.equals("purchases") ? s.longValue() : Money.amount(s));
        }
        return t;
    }

    private ReportResult all(String key, ReportQuery q, List<ReportColumn> cols, List<Map<String, Object>> rows, Map<String, Object> totals,
            Map<String, Object> meta) {
        return new ReportResult(key, q.from(), q.to(), currency.baseCurrency(), cols, rows, totals, rows.size(), 0, Math.max(rows.size(), 1), meta);
    }

    private ReportResult paged(String key, ReportQuery q, List<ReportColumn> cols, List<Map<String, Object>> rows, Map<String, Object> totals, long count,
            Map<String, Object> meta) {
        return new ReportResult(key, q.from(), q.to(), currency.baseCurrency(), cols, rows, totals, count, q.page(), q.size(), meta);
    }

    private static MapSqlParameterSource range(ReportQuery q) {
        return new MapSqlParameterSource().addValue("from", q.from()).addValue("to", q.to());
    }

    private static String like(String q) {
        return ReportSql.like(q);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
