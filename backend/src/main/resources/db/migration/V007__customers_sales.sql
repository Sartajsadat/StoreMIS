-- Customers and their ledger, sales and sale items, customer payments.

CREATE TABLE customers (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name           VARCHAR(150) NOT NULL,
    phone          VARCHAR(30),
    address        VARCHAR(300),
    customer_type  VARCHAR(15)  NOT NULL DEFAULT 'RETAIL' CHECK (customer_type IN ('RETAIL', 'WHOLESALE')),
    notes          TEXT,
    is_walk_in     BOOLEAN      NOT NULL DEFAULT FALSE,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     BIGINT       REFERENCES users (id),
    updated_at     TIMESTAMPTZ,
    updated_by     BIGINT       REFERENCES users (id),
    version        INT          NOT NULL DEFAULT 0,
    CHECK (NOT is_walk_in OR active)
);
CREATE UNIQUE INDEX ux_customers_walk_in ON customers (is_walk_in) WHERE is_walk_in;
CREATE INDEX ix_customers_name_trgm ON customers USING gin (name gin_trgm_ops);
CREATE INDEX ix_customers_phone ON customers (phone);

INSERT INTO customers (name, customer_type, is_walk_in) VALUES ('Walk-in Customer', 'RETAIL', TRUE);

-- Receivable ledger per customer and currency. debit = the customer owes more; credit = owes less.
-- A negative balance is customer credit (advance or refund owed).
CREATE TABLE customer_ledger_entries (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id    BIGINT         NOT NULL REFERENCES customers (id),
    currency_code  CHAR(3)        NOT NULL REFERENCES currencies (code),
    entry_type     VARCHAR(30)    NOT NULL CHECK (entry_type IN ('OPENING_BALANCE', 'SALE', 'PAYMENT', 'SALES_RETURN', 'SALE_CANCEL',
                                                      'PAYMENT_REVERSAL', 'CREDIT_REFUND')),
    debit          NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (debit >= 0),
    credit         NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (credit >= 0),
    balance_after  NUMERIC(19, 2) NOT NULL,
    source_type    VARCHAR(30)    NOT NULL,
    source_id      BIGINT,
    reference_no   VARCHAR(30),
    business_date  DATE           NOT NULL,
    notes          VARCHAR(300),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by     BIGINT         REFERENCES users (id),
    CHECK (debit > 0 OR credit > 0)
);
CREATE INDEX ix_customer_ledger ON customer_ledger_entries (customer_id, currency_code, id);
CREATE INDEX ix_customer_ledger_date ON customer_ledger_entries (business_date);

CREATE TABLE sales (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_no           VARCHAR(30)    UNIQUE,
    client_request_id    UUID           UNIQUE,
    customer_id          BIGINT         NOT NULL REFERENCES customers (id),
    sale_type            VARCHAR(15)    NOT NULL CHECK (sale_type IN ('RETAIL', 'WHOLESALE')),
    sale_date            TIMESTAMPTZ    NOT NULL,
    business_date        DATE,
    currency_code        CHAR(3)        NOT NULL REFERENCES currencies (code),
    exchange_rate        NUMERIC(19, 6) NOT NULL CHECK (exchange_rate > 0),
    subtotal             NUMERIC(19, 2) NOT NULL DEFAULT 0,
    item_discount_total  NUMERIC(19, 2) NOT NULL DEFAULT 0,
    overall_discount     NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (overall_discount >= 0),
    grand_total          NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (grand_total >= 0),
    amount_paid          NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (amount_paid >= 0),
    returned_amount      NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (returned_amount >= 0),
    balance_due          NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (balance_due >= 0),
    total_cost_base      NUMERIC(19, 4) NOT NULL DEFAULT 0,
    status               VARCHAR(15)    NOT NULL CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED')),
    payment_status       VARCHAR(15)    NOT NULL DEFAULT 'UNPAID' CHECK (payment_status IN ('UNPAID', 'PARTIALLY_PAID', 'PAID')),
    notes                VARCHAR(500),
    cancelled_at         TIMESTAMPTZ,
    cancelled_by         BIGINT         REFERENCES users (id),
    cancel_reason        VARCHAR(300),
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by           BIGINT         REFERENCES users (id),
    updated_at           TIMESTAMPTZ,
    updated_by           BIGINT         REFERENCES users (id),
    version              INT            NOT NULL DEFAULT 0,
    CHECK (status <> 'CANCELLED' OR cancel_reason IS NOT NULL),
    CHECK (status = 'DRAFT' OR invoice_no IS NOT NULL)
);
CREATE INDEX ix_sales_business_date ON sales (business_date);
CREATE INDEX ix_sales_customer ON sales (customer_id, business_date);
CREATE INDEX ix_sales_created_by ON sales (created_by, business_date);
CREATE INDEX ix_sales_status ON sales (status, payment_status);

CREATE TABLE sale_items (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sale_id                 BIGINT         NOT NULL REFERENCES sales (id),
    line_no                 SMALLINT       NOT NULL,
    product_id              BIGINT         NOT NULL REFERENCES products (id),
    product_name            VARCHAR(200)   NOT NULL,
    product_code            VARCHAR(60),
    unit_name               VARCHAR(50)    NOT NULL,
    quantity                NUMERIC(14, 3) NOT NULL CHECK (quantity > 0),
    unit_price              NUMERIC(19, 2) NOT NULL CHECK (unit_price >= 0),
    discount_amount         NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    line_total              NUMERIC(19, 2) NOT NULL CHECK (line_total >= 0),
    overall_discount_share  NUMERIC(19, 2) NOT NULL DEFAULT 0,
    unit_cost_base          NUMERIC(19, 4) NOT NULL DEFAULT 0,
    line_cost_base          NUMERIC(19, 4) NOT NULL DEFAULT 0,
    returned_quantity       NUMERIC(14, 3) NOT NULL DEFAULT 0 CHECK (returned_quantity >= 0 AND returned_quantity <= quantity),
    UNIQUE (sale_id, line_no)
);
CREATE INDEX ix_sale_items_product ON sale_items (product_id);

CREATE TABLE customer_payments (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_no          VARCHAR(30)    NOT NULL UNIQUE,
    client_request_id   UUID           UNIQUE,
    customer_id         BIGINT         NOT NULL REFERENCES customers (id),
    payment_kind        VARCHAR(15)    NOT NULL CHECK (payment_kind IN ('AT_SALE', 'COLLECTION')),
    payment_date        TIMESTAMPTZ    NOT NULL,
    business_date       DATE           NOT NULL,
    currency_code       CHAR(3)        NOT NULL REFERENCES currencies (code),
    amount              NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    exchange_rate       NUMERIC(19, 6) NOT NULL CHECK (exchange_rate > 0),
    cash_account_id     BIGINT         NOT NULL REFERENCES cash_accounts (id),
    unallocated_amount  NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (unallocated_amount >= 0),
    status              VARCHAR(15)    NOT NULL DEFAULT 'POSTED' CHECK (status IN ('POSTED', 'REVERSED')),
    notes               VARCHAR(300),
    reversed_at         TIMESTAMPTZ,
    reversed_by         BIGINT         REFERENCES users (id),
    reversal_reason     VARCHAR(300),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by          BIGINT         REFERENCES users (id),
    CHECK (status <> 'REVERSED' OR reversal_reason IS NOT NULL)
);
CREATE INDEX ix_customer_payments_customer ON customer_payments (customer_id, business_date);
CREATE INDEX ix_customer_payments_date ON customer_payments (business_date);

CREATE TABLE customer_payment_allocations (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_id      BIGINT         NOT NULL REFERENCES customer_payments (id),
    sale_id         BIGINT         NOT NULL REFERENCES sales (id),
    applied_amount  NUMERIC(19, 2) NOT NULL CHECK (applied_amount > 0),
    paid_amount     NUMERIC(19, 2) NOT NULL CHECK (paid_amount > 0),
    cross_rate      NUMERIC(19, 6) NOT NULL CHECK (cross_rate > 0),
    released        BOOLEAN        NOT NULL DEFAULT FALSE,
    UNIQUE (payment_id, sale_id)
);
CREATE INDEX ix_customer_alloc_sale ON customer_payment_allocations (sale_id);
