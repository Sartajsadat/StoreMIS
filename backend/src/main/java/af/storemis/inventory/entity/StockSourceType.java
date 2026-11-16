package af.storemis.inventory.entity;

/** The document that caused a stock movement. */
public enum StockSourceType {
    STOCK_ADJUSTMENT,
    PURCHASE,
    SALE,
    SALES_RETURN,
    PURCHASE_RETURN
}
