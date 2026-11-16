package af.storemis.dashboard.service;

import af.storemis.common.money.Money;
import af.storemis.common.security.SecurityUtils;
import af.storemis.common.time.BusinessClock;
import af.storemis.currency.service.CurrencyService;
import af.storemis.reporting.service.ProfitLossService;
import af.storemis.reporting.service.ProfitLossService.ProfitLoss;
import af.storemis.reporting.service.ReportSql;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only figures for the Admin dashboard and for an Entry User's "My Day". */
@Service
public class DashboardService {

    private final ReportSql sql;
    private final ProfitLossService profitLoss;
    private final CurrencyService currency;
    private final BusinessClock clock;

    public DashboardService(ReportSql sql, ProfitLossService profitLoss, CurrencyService currency, BusinessClock clock) {
        this.sql = sql;
        this.profitLoss = profitLoss;
        this.currency = currency;
        this.clock = clock;
    }

    /** KPIs for the period, each with the change against the previous period of the same length. */
    @Transactional(readOnly = true)
    public Map<String, Object> summary(LocalDate from, LocalDate to) {
        ProfitLoss pl = profitLoss.compute(from, to);
        long days = to.toEpochDay() - from.toEpochDay() + 1;
        ProfitLoss prev = profitLoss.compute(from.minusDays(days), from.minusDays(1));
        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("netSales", kpi(pl.netSales(), prev.netSales()));
        kpi.put("grossProfit", kpi(pl.grossProfit(), prev.grossProfit()));
        kpi.put("expenses", kpi(pl.expenses(), prev.expenses()));
        kpi.put("netProfit", kpi(pl.netProfit(), prev.netProfit()));
        Map<String, Object> invoices = new LinkedHashMap<>();
        invoices.put("value", pl.invoices());
        invoices.put("previous", prev.invoices());
        invoices.put("change", change(BigDecimal.valueOf(pl.invoices()), BigDecimal.valueOf(prev.invoices())));
        kpi.put("invoices", invoices);

        MapSqlParameterSource none = new MapSqlParameterSource();
        List<Map<String, Object>> cash = sql.list("""
                select id as "id", name as "name", rtrim(currency_code) as "currency", current_balance as "balance"
                from cash_accounts where active order by id""", none);
        Map<String, Object> stock = sql.one("""
                select coalesce(sum(st.inventory_value), 0) as "value", count(*) as "products",
                       count(*) filter (where p.min_stock_level > 0 and st.quantity_on_hand <= p.min_stock_level and st.quantity_on_hand > 0) as "lowStock",
                       count(*) filter (where st.quantity_on_hand <= 0) as "outOfStock"
                from products p join product_stock st on st.product_id = p.id where p.active""", none);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from", from);
        out.put("to", to);
        out.put("previousFrom", prev.from());
        out.put("previousTo", prev.to());
        out.put("baseCurrency", currency.baseCurrency());
        out.put("kpi", kpi);
        out.put("grossMargin", pl.grossMarginPercent());
        out.put("salesByCurrency", pl.salesByCurrency());
        out.put("cash", cash);
        out.put("receivables", sideTotals("select rtrim(currency_code) as c, sum(debit - credit) as b from customer_ledger_entries group by customer_id, 1"));
        out.put("payables", sideTotals("select rtrim(currency_code) as c, sum(credit - debit) as b from supplier_ledger_entries group by supplier_id, 1"));
        out.put("stock", stock);
        return out;
    }

    /** Sales and profit over time (daily up to two months, otherwise monthly), sales mix, expenses and top products. */
    @Transactional(readOnly = true)
    public Map<String, Object> charts(LocalDate from, LocalDate to) {
        long days = to.toEpochDay() - from.toEpochDay() + 1;
        String unit = days <= 62 ? "day" : "month";
        String step = days <= 62 ? "1 day" : "1 month";
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("from", from).addValue("to", to);
        List<Map<String, Object>> series = sql.list("""
                with b as (select generate_series(date_trunc('%1$s', cast(:from as date)::timestamp), cast(:to as date)::timestamp, interval '%2$s')::date as p),
                s as (select date_trunc('%1$s', business_date::timestamp)::date as p, sum(round(grand_total * exchange_rate, 2)) as net,
                             sum(total_cost_base) as cost
                      from sales where status = 'CONFIRMED' and business_date between :from and :to group by 1),
                r as (select date_trunc('%1$s', business_date::timestamp)::date as p, sum(round(total_amount * exchange_rate, 2)) as ret,
                             sum(total_cost_base) as rcost
                      from sales_returns where business_date between :from and :to group by 1),
                e as (select date_trunc('%1$s', business_date::timestamp)::date as p, sum(round(amount * exchange_rate, 2)) as exp
                      from expenses where status = 'POSTED' and business_date between :from and :to group by 1),
                pu as (select date_trunc('%1$s', business_date::timestamp)::date as p, sum(round(grand_total * exchange_rate, 2)) as pur
                       from purchases where status = 'CONFIRMED' and business_date between :from and :to group by 1)
                select b.p as "period", coalesce(s.net, 0) - coalesce(r.ret, 0) as "netSales",
                       coalesce(s.net, 0) - coalesce(r.ret, 0) - coalesce(s.cost, 0) + coalesce(r.rcost, 0) as "grossProfit",
                       coalesce(e.exp, 0) as "expenses", coalesce(pu.pur, 0) as "purchases"
                from b left join s on s.p = b.p left join r on r.p = b.p left join e on e.p = b.p left join pu on pu.p = b.p
                order by b.p""".formatted(unit, step), p);
        ProfitLoss pl = profitLoss.compute(from, to);
        List<Map<String, Object>> top = sql.list("""
                select p.name as "productName", sum(round((si.line_total - si.overall_discount_share) * s.exchange_rate, 2)) as "netSales",
                       sum(si.quantity) as "quantity", u.name as "unitName"
                from sale_items si join sales s on s.id = si.sale_id join products p on p.id = si.product_id join units u on u.id = p.unit_id
                where s.status = 'CONFIRMED' and s.business_date between :from and :to
                group by p.id, p.name, u.name order by 2 desc limit 5""", p);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("unit", unit);
        out.put("series", series);
        out.put("byType", pl.netSalesByType());
        out.put("expensesByCategory", pl.expensesByCategory());
        out.put("topProducts", top);
        return out;
    }

    /** Latest documents, payments, activity and the products that need re-ordering. */
    @Transactional(readOnly = true)
    public Map<String, Object> recent() {
        MapSqlParameterSource none = new MapSqlParameterSource();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sales", sql.list("""
                select s.id as "id", s.invoice_no as "invoiceNo", s.sale_date as "date", c.name as "customerName", c.is_walk_in as "walkIn",
                       rtrim(s.currency_code) as "currency", s.grand_total as "grandTotal", s.payment_status as "paymentStatus", s.status as "status"
                from sales s join customers c on c.id = s.customer_id where s.status <> 'DRAFT' order by s.id desc limit 7""", none));
        out.put("purchases", sql.list("""
                select p.id as "id", p.purchase_no as "purchaseNo", p.business_date as "date", s.name as "supplierName",
                       rtrim(p.currency_code) as "currency", p.grand_total as "grandTotal", p.payment_status as "paymentStatus"
                from purchases p join suppliers s on s.id = p.supplier_id where p.status = 'CONFIRMED' order by p.id desc limit 5""", none));
        out.put("payments", sql.list("""
                select x.id as "id", x.payment_no as "paymentNo", x.payment_date as "date", c.name as "customerName",
                       rtrim(x.currency_code) as "currency", x.amount as "amount", x.status as "status"
                from customer_payments x join customers c on c.id = x.customer_id where x.payment_kind = 'COLLECTION' order by x.id desc limit 5""", none));
        out.put("lowStock", sql.list("""
                select p.id as "productId", p.name as "productName", p.code as "productCode", u.name as "unitName",
                       st.quantity_on_hand as "quantity", p.min_stock_level as "minStock"
                from products p join product_stock st on st.product_id = p.id join units u on u.id = p.unit_id
                where p.active and p.min_stock_level > 0 and st.quantity_on_hand <= p.min_stock_level
                order by st.quantity_on_hand <= 0 desc, (st.quantity_on_hand / nullif(p.min_stock_level, 0)) asc limit 8""", none));
        out.put("activity", sql.list("""
                select a.id as "id", a.occurred_at as "at", coalesce(u.full_name, a.username) as "userName", a.action as "action",
                       a.module as "module", a.reference as "reference", a.description as "description"
                from audit_logs a left join users u on u.id = a.user_id
                where a.action not in ('LOGIN', 'LOGOUT', 'LOGIN_FAILED') order by a.id desc limit 8""", none));
        return out;
    }

    /** What the signed-in user did today: their sales per currency, payments they received, held carts. */
    @Transactional(readOnly = true)
    public Map<String, Object> myDay() {
        LocalDate today = clock.today();
        Long me = SecurityUtils.currentUserId().orElse(-1L);
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("today", today).addValue("me", me);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", today);
        out.put("salesCount", sql.one("""
                select count(*) as "n" from sales where created_by = :me and status = 'CONFIRMED' and business_date = :today""", p).get("n"));
        out.put("salesByCurrency", amounts(sql.list("""
                select rtrim(currency_code) as c, sum(grand_total) as a from sales
                where created_by = :me and status = 'CONFIRMED' and business_date = :today group by 1""", p)));
        out.put("receivedByCurrency", amounts(sql.list("""
                select rtrim(currency_code) as c, sum(amount) as a from customer_payments
                where created_by = :me and status = 'POSTED' and business_date = :today group by 1""", p)));
        out.put("heldCarts", sql.one("select count(*) as \"n\" from sales where created_by = :me and status = 'DRAFT'", p).get("n"));
        out.put("recentSales", sql.list("""
                select s.id as "id", s.invoice_no as "invoiceNo", s.sale_date as "date", c.name as "customerName", c.is_walk_in as "walkIn",
                       rtrim(s.currency_code) as "currency", s.grand_total as "grandTotal", s.payment_status as "paymentStatus", s.status as "status"
                from sales s join customers c on c.id = s.customer_id
                where s.created_by = :me and s.status <> 'DRAFT' order by s.id desc limit 8""", p));
        out.put("lowStock", sql.one("""
                select count(*) as "n" from products p join product_stock st on st.product_id = p.id
                where p.active and p.min_stock_level > 0 and st.quantity_on_hand <= p.min_stock_level""", p).get("n"));
        return out;
    }

    private Map<String, Map<String, BigDecimal>> sideTotals(String perParty) {
        Map<String, BigDecimal> owed = new TreeMap<>();
        Map<String, BigDecimal> credit = new TreeMap<>();
        for (Map<String, Object> r : sql.list(perParty, new MapSqlParameterSource())) {
            BigDecimal b = Money.amount(new BigDecimal(r.get("b").toString()));
            if (b.signum() > 0) owed.merge((String) r.get("c"), b, BigDecimal::add);
            else if (b.signum() < 0) credit.merge((String) r.get("c"), b.negate(), BigDecimal::add);
        }
        Map<String, Map<String, BigDecimal>> out = new LinkedHashMap<>();
        out.put("owed", owed);
        out.put("credit", credit);
        return out;
    }

    private static Map<String, BigDecimal> amounts(List<Map<String, Object>> rows) {
        Map<String, BigDecimal> out = new TreeMap<>();
        for (Map<String, Object> r : rows) out.put((String) r.get("c"), Money.amount(new BigDecimal(r.get("a").toString())));
        return out;
    }

    private static Map<String, Object> kpi(BigDecimal value, BigDecimal previous) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", value);
        m.put("previous", previous);
        m.put("change", change(value, previous));
        return m;
    }

    /** Percentage change, or null when there is nothing to compare with. */
    static BigDecimal change(BigDecimal value, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) return null;
        return value.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous.abs(), 1, RoundingMode.HALF_UP);
    }
}
