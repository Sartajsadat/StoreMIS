package af.storemis.reporting.dto;

/**
 * How the UI shows and exports one column. The header is translated with {@code reports.columns.<key>}.
 * <ul>
 *   <li>type: TEXT, USER_TEXT, CODE, DATE, DATETIME, MONEY, QTY, NUMBER, PERCENT, ENUM, BALANCES</li>
 *   <li>currencyField: for MONEY, the row field holding the currency; null = base currency</li>
 *   <li>enumPrefix: for ENUM, the translation prefix of the value (e.g. {@code cash.types})</li>
 *   <li>unitField: for QTY, the row field holding the unit name</li>
 * </ul>
 */
public record ReportColumn(String key, String type, String currencyField, String enumPrefix, String unitField) {

    public static ReportColumn text(String key) {
        return new ReportColumn(key, "TEXT", null, null, null);
    }

    public static ReportColumn userText(String key) {
        return new ReportColumn(key, "USER_TEXT", null, null, null);
    }

    public static ReportColumn code(String key) {
        return new ReportColumn(key, "CODE", null, null, null);
    }

    public static ReportColumn date(String key) {
        return new ReportColumn(key, "DATE", null, null, null);
    }

    public static ReportColumn dateTime(String key) {
        return new ReportColumn(key, "DATETIME", null, null, null);
    }

    /** Money in the base currency. */
    public static ReportColumn base(String key) {
        return new ReportColumn(key, "MONEY", null, null, null);
    }

    /** Money in the currency held by {@code currencyField}. */
    public static ReportColumn money(String key, String currencyField) {
        return new ReportColumn(key, "MONEY", currencyField, null, null);
    }

    public static ReportColumn qty(String key, String unitField) {
        return new ReportColumn(key, "QTY", null, null, unitField);
    }

    public static ReportColumn number(String key) {
        return new ReportColumn(key, "NUMBER", null, null, null);
    }

    public static ReportColumn percent(String key) {
        return new ReportColumn(key, "PERCENT", null, null, null);
    }

    public static ReportColumn enumOf(String key, String prefix) {
        return new ReportColumn(key, "ENUM", null, prefix, null);
    }

    /** A map currency → amount (e.g. a customer's balance in AFN and USD). */
    public static ReportColumn balances(String key) {
        return new ReportColumn(key, "BALANCES", null, null, null);
    }
}
