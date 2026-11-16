package af.storemis.inventory.entity;

public enum AdjustmentType {
    /** First stock of a product at go-live, with its cost. Allowed once, before any other movement. */
    OPENING(MovementType.OPENING, true),
    /** Found or counted more than recorded. */
    INCREASE(MovementType.ADJUSTMENT_IN, true),
    /** Counted less than recorded (loss, theft, counting error). */
    DECREASE(MovementType.ADJUSTMENT_OUT, false),
    /** Broken or unsellable goods written off. */
    DAMAGE(MovementType.DAMAGE, false);

    private final MovementType movementType;
    private final boolean inbound;

    AdjustmentType(MovementType movementType, boolean inbound) {
        this.movementType = movementType;
        this.inbound = inbound;
    }

    public MovementType movementType() {
        return movementType;
    }

    public boolean isInbound() {
        return inbound;
    }
}
