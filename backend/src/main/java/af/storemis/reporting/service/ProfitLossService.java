package af.storemis.reporting.service;

import af.storemis.common.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Profit and loss for a period, in the base currency, every amount converted at its own document rate
 * (design: business workflows § 8). Purchases, owner money, transfers and receivable/payable movements are not part of it.
 */
@Service
public class ProfitLossService {

    /** All figures of one period. */
    public record ProfitLoss(LocalDate from, LocalDate to, long invoices, BigDecimal grossSales, BigDecimal discounts, BigDecimal salesReturns,
            BigDecimal netSales, BigDecimal salesCost, BigDecimal returnedCost, BigDecimal cogs, BigDecimal grossProfit, BigDecimal expenses,
            Map<String, BigDecimal> expensesByCategory, BigDecimal damage, BigDecimal adjustmentsOut, BigDecimal adjustmentsIn, BigDecimal damagedReturns,
            BigDecimal inventoryLosses, BigDecimal netProfit, Map<String, BigDecimal> salesByCurrency, Map<String, BigDecimal> netSalesByType) {

        public BigDecimal grossMarginPercent() {
            return netSales.signum() == 0 ? BigDecimal.ZERO : grossProfit.multiply(BigDecimal.valueOf(100)).divide(netSales, 1, RoundingMode.HALF_UP);
        }
    }

    private final ReportSql sql;

    public ProfitLossService(ReportSql sql) {
        this.sql = sql;
    }

    @Transactional(readOnly = true)
    public ProfitLoss compute(LocalDate from, LocalDate to) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("from", from).addValue("to", to);
        Map<String, Object> s = sql.one("""
                select count(*) as "invoices",
                       coalesce(sum(round(subtotal * exchange_rate, 2)), 0) as "gross",
                       coalesce(sum(round((item_discount_total + overall_discount) * exchange_rate, 2)), 0) as "discounts",
                       coalesce(sum(total_cost_base), 0) as "cost"
                from sales where status = 'CONFIRMED' and business_date between :from and :to""", p);
        Map<String, Object> r = sql.one("""
                select coalesce(sum(round(total_amount * exchange_rate, 2)), 0) as "returns", coalesce(sum(total_cost_base), 0) as "cost"
                from sales_returns where business_date between :from and :to""", p);
        Map<String, Object> m = sql.one("""
                select coalesce(sum(case when movement_type = 'DAMAGE' then -total_cost_base end), 0) as "damage",
                       coalesce(sum(case when movement_type = 'ADJUSTMENT_OUT' then -total_cost_base end), 0) as "adjOut",
                       coalesce(sum(case when movement_type = 'ADJUSTMENT_IN' then total_cost_base end), 0) as "adjIn"
                from stock_movements where business_date between :from and :to""", p);
        BigDecimal damagedReturns = sql.decimal("""
                select coalesce(sum(round(i.quantity * i.unit_cost_base, 2)), 0) from sales_return_items i
                join sales_returns r on r.id = i.sales_return_id
                where i.item_condition = 'DAMAGED' and r.business_date between :from and :to""", p);
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (Map<String, Object> row : sql.list("""
                select coalesce(c.code, c.name) as "category", sum(round(e.amount * e.exchange_rate, 2)) as "amount"
                from expenses e join expense_categories c on c.id = e.category_id
                where e.status = 'POSTED' and e.business_date between :from and :to
                group by coalesce(c.code, c.name) order by 2 desc""", p)) {
            byCategory.put((String) row.get("category"), dec(row.get("amount")));
        }
        Map<String, BigDecimal> byCurrency = new LinkedHashMap<>();
        for (Map<String, Object> row : sql.list("""
                select currency_code as "currency", sum(grand_total) as "total" from sales
                where status = 'CONFIRMED' and business_date between :from and :to group by currency_code order by 1""", p)) {
            byCurrency.put(((String) row.get("currency")).trim(), dec(row.get("total")));
        }
        Map<String, BigDecimal> byType = new LinkedHashMap<>();
        byType.put("RETAIL", Money.ZERO);
        byType.put("WHOLESALE", Money.ZERO);
        for (Map<String, Object> row : sql.list("""
                select t.sale_type as "type", sum(t.amount) as "amount" from (
                    select sale_type, round(grand_total * exchange_rate, 2) as amount from sales
                    where status = 'CONFIRMED' and business_date between :from and :to
                    union all
                    select s.sale_type, -round(r.total_amount * r.exchange_rate, 2) from sales_returns r join sales s on s.id = r.sale_id
                    where r.business_date between :from and :to) t group by t.sale_type""", p)) {
            byType.put((String) row.get("type"), dec(row.get("amount")));
        }

        BigDecimal gross = dec(s.get("gross"));
        BigDecimal discounts = dec(s.get("discounts"));
        BigDecimal returns = dec(r.get("returns"));
        BigDecimal net = gross.subtract(discounts).subtract(returns);
        BigDecimal salesCost = dec(s.get("cost"));
        BigDecimal returnedCost = dec(r.get("cost"));
        BigDecimal cogs = salesCost.subtract(returnedCost);
        BigDecimal grossProfit = net.subtract(cogs);
        BigDecimal expenses = byCategory.values().stream().reduce(Money.ZERO, BigDecimal::add);
        BigDecimal damage = dec(m.get("damage"));
        BigDecimal adjOut = dec(m.get("adjOut"));
        BigDecimal adjIn = dec(m.get("adjIn"));
        BigDecimal losses = damage.add(adjOut).subtract(adjIn).add(damagedReturns);
        BigDecimal netProfit = grossProfit.subtract(expenses).subtract(losses);
        return new ProfitLoss(from, to, ((Number) s.get("invoices")).longValue(), gross, discounts, returns, net, salesCost, returnedCost, cogs,
                grossProfit, expenses, byCategory, damage, adjOut, adjIn, damagedReturns, losses, netProfit, byCurrency, byType);
    }

    /** The statement as report rows: {line, category, amount, kind} where kind is ITEM, DETAIL, SUBTOTAL or TOTAL. */
    public List<Map<String, Object>> statement(ProfitLoss pl) {
        List<Map<String, Object>> rows = new ArrayList<>();
        add(rows, "GROSS_SALES", null, pl.grossSales(), "ITEM");
        add(rows, "DISCOUNTS", null, pl.discounts().negate(), "ITEM");
        add(rows, "SALES_RETURNS", null, pl.salesReturns().negate(), "ITEM");
        add(rows, "NET_SALES", null, pl.netSales(), "SUBTOTAL");
        add(rows, "COGS", null, pl.cogs().negate(), "ITEM");
        add(rows, "GROSS_PROFIT", null, pl.grossProfit(), "SUBTOTAL");
        add(rows, "OPERATING_EXPENSES", null, pl.expenses().negate(), "ITEM");
        pl.expensesByCategory().forEach((cat, amount) -> add(rows, "EXPENSE_CATEGORY", cat, amount.negate(), "DETAIL"));
        add(rows, "INVENTORY_LOSSES", null, pl.inventoryLosses().negate(), "ITEM");
        add(rows, "NET_PROFIT", null, pl.netProfit(), "TOTAL");
        return rows;
    }

    private static void add(List<Map<String, Object>> rows, String line, String category, BigDecimal amount, String kind) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("line", line);
        row.put("category", category);
        row.put("amount", amount);
        row.put("kind", kind);
        rows.add(row);
    }

    static BigDecimal dec(Object v) {
        return v == null ? Money.ZERO : Money.amount(new BigDecimal(v.toString()));
    }
}
