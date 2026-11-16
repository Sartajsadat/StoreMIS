package af.storemis.inventory.entity;

/** Every kind of stock change. The sign of the quantity is decided by the posting service, not by callers. */
public enum MovementType {
    OPENING,
    PURCHASE,
    PURCHASE_CANCEL,
    PURCHASE_RETURN,
    SALE,
    SALE_CANCEL,
    SALE_RETURN,
    ADJUSTMENT_IN,
    ADJUSTMENT_OUT,
    DAMAGE
}
