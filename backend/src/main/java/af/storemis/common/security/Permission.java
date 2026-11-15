package af.storemis.common.security;

/** Toggle permissions an Admin can grant to Entry Users. Admins implicitly hold all of them. */
public enum Permission {
    RECEIVE_CUSTOMER_PAYMENTS,
    GIVE_DISCOUNT,
    RECORD_PURCHASES,
    PAY_SUPPLIERS,
    RECORD_EXPENSES,
    PROCESS_SALES_RETURNS,
    VIEW_COST_PRICES,
    ADJUST_STOCK,
    VIEW_ALL_SALES
}
