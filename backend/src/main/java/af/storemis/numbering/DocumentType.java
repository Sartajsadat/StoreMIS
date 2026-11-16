package af.storemis.numbering;

/** Document types with their own gap-free number sequence (rows of document_sequences). */
public enum DocumentType {
    SALE,
    PURCHASE,
    SALES_RETURN,
    PURCHASE_RETURN,
    CUSTOMER_PAYMENT,
    SUPPLIER_PAYMENT,
    EXPENSE,
    STOCK_ADJUSTMENT,
    CASH_TRANSFER,
    CASH_TXN
}
