-- Append-only stock ledger and stock adjustments.

CREATE TABLE stock_movements (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id       BIGINT         NOT NULL REFERENCES products (id),
    movement_type    VARCHAR(30)    NOT NULL CHECK (movement_type IN (
                        'OPENING', 'PURCHASE', 'PURCHASE_CANCEL', 'PURCHASE_RETURN', 'SALE', 'SALE_CANCEL',
                        'SALE_RETURN', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'DAMAGE')),
    quantity         NUMERIC(14, 3) NOT NULL CHECK (quantity <> 0),
    unit_cost_base   NUMERIC(19, 4) NOT NULL CHECK (unit_cost_base >= 0),
    total_cost_base  NUMERIC(19, 4) NOT NULL,
    quantity_after   NUMERIC(14, 3) NOT NULL,
    avg_cost_after   NUMERIC(19, 4) NOT NULL,
    value_after      NUMERIC(19, 4) NOT NULL,
    source_type      VARCHAR(30)    NOT NULL,
    source_id        BIGINT         NOT NULL,
    source_item_id   BIGINT,
    reference_no     VARCHAR(30)    NOT NULL,
    business_date    DATE           NOT NULL,
    notes            VARCHAR(300),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by       BIGINT         REFERENCES users (id)
);
CREATE INDEX ix_stock_movements_product ON stock_movements (product_id, id);
CREATE INDEX ix_stock_movements_date ON stock_movements (business_date);
CREATE INDEX ix_stock_movements_source ON stock_movements (source_type, source_id);
CREATE INDEX ix_stock_movements_type_date ON stock_movements (movement_type, business_date);

CREATE TABLE stock_adjustments (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    adjustment_no    VARCHAR(30)    NOT NULL UNIQUE,
    client_request_id UUID          UNIQUE,
    product_id       BIGINT         NOT NULL REFERENCES products (id),
    adjustment_type  VARCHAR(20)    NOT NULL CHECK (adjustment_type IN ('OPENING', 'INCREASE', 'DECREASE', 'DAMAGE')),
    quantity         NUMERIC(14, 3) NOT NULL CHECK (quantity > 0),
    unit_cost_base   NUMERIC(19, 4) NOT NULL CHECK (unit_cost_base >= 0),
    reason           VARCHAR(300)   NOT NULL,
    business_date    DATE           NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by       BIGINT         REFERENCES users (id)
);
CREATE INDEX ix_stock_adjustments_product ON stock_adjustments (product_id);
CREATE INDEX ix_stock_adjustments_date ON stock_adjustments (business_date);
