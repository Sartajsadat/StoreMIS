-- Suppliers and their ledger, cash accounts and the cash ledger, purchases and supplier payments.

CREATE TABLE suppliers (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            VARCHAR(150) NOT NULL,
    phone           VARCHAR(30),
    address         VARCHAR(300),
    contact_person  VARCHAR(150),
    notes           TEXT,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      BIGINT       REFERENCES users (id),
    updated_at      TIMESTAMPTZ,
    updated_by      BIGINT       REFERENCES users (id),
    version         INT          NOT NULL DEFAULT 0
);
CREATE INDEX ix_suppliers_name_trgm ON suppliers USING gin (name gin_trgm_ops);
CREATE INDEX ix_suppliers_phone ON suppliers (phone);

-- Payable ledger per supplier and currency. credit = we owe more, debit = we owe less.
-- A negative balance means the supplier owes us (advance / credit).
CREATE TABLE supplier_ledger_entries (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    supplier_id    BIGINT         NOT NULL REFERENCES suppliers (id),
    currency_code  CHAR(3)        NOT NULL REFERENCES currencies (code),
    entry_type     VARCHAR(30)    NOT NULL CHECK (entry_type IN ('OPENING_BALANCE', 'PURCHASE', 'PAYMENT', 'PURCHASE_RETURN',
                                                      'PURCHASE_CANCEL', 'PAYMENT_REVERSAL', 'SUPPLIER_REFUND')),
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
CREATE INDEX ix_supplier_ledger ON supplier_ledger_entries (supplier_id, currency_code, id);
CREATE INDEX ix_supplier_ledger_date ON supplier_ledger_entries (business_date);

CREATE TABLE cash_accounts (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name             VARCHAR(100)   NOT NULL,
    currency_code    CHAR(3)        NOT NULL REFERENCES currencies (code),
    account_type     VARCHAR(15)    NOT NULL DEFAULT 'CASH' CHECK (account_type IN ('CASH', 'BANK', 'HAWALA')),
    current_balance  NUMERIC(19, 2) NOT NULL DEFAULT 0,
    is_default       BOOLEAN        NOT NULL DEFAULT FALSE,
    active           BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by       BIGINT         REFERENCES users (id),
    updated_at       TIMESTAMPTZ,
    updated_by       BIGINT         REFERENCES users (id),
    version          INT            NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_cash_accounts_name ON cash_accounts (lower(name));
CREATE UNIQUE INDEX ux_cash_accounts_default ON cash_accounts (currency_code) WHERE is_default;

INSERT INTO cash_accounts (name, currency_code, is_default) VALUES ('AFN Cash', 'AFN', TRUE), ('USD Cash', 'USD', TRUE);

-- Append-only cash ledger.
CREATE TABLE cash_transactions (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    txn_no           VARCHAR(30)    NOT NULL UNIQUE,
    cash_account_id  BIGINT         NOT NULL REFERENCES cash_accounts (id),
    txn_type         VARCHAR(30)    NOT NULL CHECK (txn_type IN ('OPENING_BALANCE', 'SALE_RECEIPT', 'CUSTOMER_COLLECTION', 'PURCHASE_PAYMENT',
                         'SUPPLIER_PAYMENT', 'EXPENSE', 'SALE_REFUND', 'SUPPLIER_REFUND', 'OWNER_DEPOSIT', 'OWNER_WITHDRAWAL', 'TRANSFER_IN',
                         'TRANSFER_OUT', 'EXCHANGE_IN', 'EXCHANGE_OUT', 'CLOSING_DIFFERENCE', 'REVERSAL')),
    direction        VARCHAR(3)     NOT NULL CHECK (direction IN ('IN', 'OUT')),
    amount           NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    balance_after    NUMERIC(19, 2) NOT NULL,
    exchange_rate    NUMERIC(19, 6) NOT NULL CHECK (exchange_rate > 0),
    source_type      VARCHAR(30)    NOT NULL,
    source_id        BIGINT,
    reference_no     VARCHAR(30),
    reversal_of_id   BIGINT         REFERENCES cash_transactions (id),
    business_date    DATE           NOT NULL,
    description      VARCHAR(300),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by       BIGINT         REFERENCES users (id)
);
CREATE INDEX ix_cash_txn_account_date ON cash_transactions (cash_account_id, business_date);
CREATE INDEX ix_cash_txn_source ON cash_transactions (source_type, source_id);

CREATE TABLE purchases (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    purchase_no          VARCHAR(30)    UNIQUE,
    supplier_id          BIGINT         NOT NULL REFERENCES suppliers (id),
    supplier_invoice_no  VARCHAR(50),
    purchase_date        DATE           NOT NULL,
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
    status               VARCHAR(15)    NOT NULL CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED')),
    payment_status       VARCHAR(15)    NOT NULL DEFAULT 'UNPAID' CHECK (payment_status IN ('UNPAID', 'PARTIALLY_PAID', 'PAID')),
    notes                VARCHAR(500),
    confirmed_at         TIMESTAMPTZ,
    confirmed_by         BIGINT         REFERENCES users (id),
    cancelled_at         TIMESTAMPTZ,
    cancelled_by         BIGINT         REFERENCES users (id),
    cancel_reason        VARCHAR(300),
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by           BIGINT         REFERENCES users (id),
    updated_at           TIMESTAMPTZ,
    updated_by           BIGINT         REFERENCES users (id),
    version              INT            NOT NULL DEFAULT 0,
    CHECK (status <> 'CANCELLED' OR cancel_reason IS NOT NULL),
    CHECK (status = 'DRAFT' OR purchase_no IS NOT NULL)
);
CREATE INDEX ix_purchases_supplier ON purchases (supplier_id, purchase_date);
CREATE INDEX ix_purchases_date ON purchases (business_date);
CREATE INDEX ix_purchases_status ON purchases (status, payment_status);
CREATE INDEX ix_purchases_supplier_invoice ON purchases (supplier_id, supplier_invoice_no);

CREATE TABLE purchase_items (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    purchase_id             BIGINT         NOT NULL REFERENCES purchases (id),
    line_no                 SMALLINT       NOT NULL,
    product_id              BIGINT         NOT NULL REFERENCES products (id),
    product_name            VARCHAR(200)   NOT NULL,
    unit_name               VARCHAR(50)    NOT NULL,
    quantity                NUMERIC(14, 3) NOT NULL CHECK (quantity > 0),
    unit_cost               NUMERIC(19, 4) NOT NULL CHECK (unit_cost >= 0),
    discount_amount         NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    line_total              NUMERIC(19, 2) NOT NULL CHECK (line_total >= 0),
    overall_discount_share  NUMERIC(19, 2) NOT NULL DEFAULT 0,
    landed_unit_cost_base   NUMERIC(19, 4) NOT NULL DEFAULT 0,
    returned_quantity       NUMERIC(14, 3) NOT NULL DEFAULT 0 CHECK (returned_quantity >= 0 AND returned_quantity <= quantity),
    UNIQUE (purchase_id, line_no)
);
CREATE INDEX ix_purchase_items_product ON purchase_items (product_id);

CREATE TABLE supplier_payments (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_no          VARCHAR(30)    NOT NULL UNIQUE,
    client_request_id   UUID           UNIQUE,
    supplier_id         BIGINT         NOT NULL REFERENCES suppliers (id),
    payment_kind        VARCHAR(15)    NOT NULL CHECK (payment_kind IN ('AT_PURCHASE', 'PAYMENT')),
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
CREATE INDEX ix_supplier_payments_supplier ON supplier_payments (supplier_id, business_date);

CREATE TABLE supplier_payment_allocations (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_id      BIGINT         NOT NULL REFERENCES supplier_payments (id),
    purchase_id     BIGINT         NOT NULL REFERENCES purchases (id),
    applied_amount  NUMERIC(19, 2) NOT NULL CHECK (applied_amount > 0),
    paid_amount     NUMERIC(19, 2) NOT NULL CHECK (paid_amount > 0),
    cross_rate      NUMERIC(19, 6) NOT NULL CHECK (cross_rate > 0),
    released        BOOLEAN        NOT NULL DEFAULT FALSE,
    UNIQUE (payment_id, purchase_id)
);
CREATE INDEX ix_supplier_alloc_purchase ON supplier_payment_allocations (purchase_id);

-- Seed a default USD rate so USD documents work out of the box; the admin updates it in Currency Settings.
INSERT INTO exchange_rates (currency_code, rate, notes) VALUES ('USD', 70.000000, 'Initial default — update in Currency Settings');
