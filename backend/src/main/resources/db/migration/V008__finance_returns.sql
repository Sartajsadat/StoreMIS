-- Phase 7: cash transfers and exchanges, expenses, daily cash closing, sales and purchase returns.

-- Part of the returned value that was paid back (cash refund or credit) instead of reducing what is still owed.
-- balance_due = grand_total − returned_amount − amount_paid + return_settled_amount
ALTER TABLE sales ADD COLUMN return_settled_amount NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (return_settled_amount >= 0);
ALTER TABLE purchases ADD COLUMN return_settled_amount NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (return_settled_amount >= 0);

-- Same-currency transfers and currency exchanges between cash accounts. Each posts two cash legs.
CREATE TABLE cash_transfers (
    id                 BIGSERIAL PRIMARY KEY,
    transfer_no        VARCHAR(30)    NOT NULL UNIQUE,
    client_request_id  UUID           UNIQUE,
    transfer_type      VARCHAR(15)    NOT NULL CHECK (transfer_type IN ('TRANSFER', 'EXCHANGE')),
    from_account_id    BIGINT         NOT NULL REFERENCES cash_accounts (id),
    to_account_id      BIGINT         NOT NULL REFERENCES cash_accounts (id),
    from_amount        NUMERIC(19, 2) NOT NULL CHECK (from_amount > 0),
    to_amount          NUMERIC(19, 2) NOT NULL CHECK (to_amount > 0),
    exchange_rate      NUMERIC(19, 6) NOT NULL CHECK (exchange_rate > 0),
    business_date      DATE           NOT NULL,
    notes              VARCHAR(300),
    created_at         TIMESTAMPTZ    NOT NULL,
    created_by         BIGINT         REFERENCES users (id),
    CHECK (from_account_id <> to_account_id),
    CHECK (transfer_type = 'EXCHANGE' OR (from_amount = to_amount AND exchange_rate = 1))
);
CREATE INDEX ix_cash_transfers_date ON cash_transfers (business_date);

CREATE TABLE expense_categories (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(30)  UNIQUE,
    name        VARCHAR(100) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  BIGINT       REFERENCES users (id),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  BIGINT       REFERENCES users (id),
    version     INTEGER      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_expense_categories_name ON expense_categories (lower(name));

-- Seeded categories carry a code; the UI shows a translated name for them (expenses.categoryCodes.<CODE>).
INSERT INTO expense_categories (code, name) VALUES
    ('RENT', 'Rent'), ('ELECTRICITY', 'Electricity'), ('TRANSPORT', 'Transport'), ('SALARY', 'Salary'),
    ('MAINTENANCE', 'Maintenance'), ('INTERNET', 'Internet'), ('OTHER', 'Other');

CREATE TABLE expenses (
    id                 BIGSERIAL PRIMARY KEY,
    expense_no         VARCHAR(30)    NOT NULL UNIQUE,
    client_request_id  UUID           UNIQUE,
    category_id        BIGINT         NOT NULL REFERENCES expense_categories (id),
    expense_date       DATE           NOT NULL,
    business_date      DATE           NOT NULL,
    description        VARCHAR(300)   NOT NULL,
    amount             NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency_code      CHAR(3)        NOT NULL REFERENCES currencies (code),
    exchange_rate      NUMERIC(19, 6) NOT NULL CHECK (exchange_rate > 0),
    cash_account_id    BIGINT         NOT NULL REFERENCES cash_accounts (id),
    attachment_path    VARCHAR(255),
    status             VARCHAR(10)    NOT NULL DEFAULT 'POSTED' CHECK (status IN ('POSTED', 'VOIDED')),
    void_reason        VARCHAR(300),
    voided_at          TIMESTAMPTZ,
    voided_by          BIGINT         REFERENCES users (id),
    created_at         TIMESTAMPTZ    NOT NULL,
    created_by         BIGINT         REFERENCES users (id),
    CHECK (status <> 'VOIDED' OR void_reason IS NOT NULL)
);
CREATE INDEX ix_expenses_date ON expenses (business_date);
CREATE INDEX ix_expenses_category ON expenses (category_id);

-- One row per cash account and business date. Reopened rows stay for the history; only one CLOSED row per day.
CREATE TABLE cash_closings (
    id                BIGSERIAL PRIMARY KEY,
    business_date     DATE           NOT NULL,
    cash_account_id   BIGINT         NOT NULL REFERENCES cash_accounts (id),
    opening_balance   NUMERIC(19, 2) NOT NULL,
    total_in          NUMERIC(19, 2) NOT NULL,
    total_out         NUMERIC(19, 2) NOT NULL,
    expected_balance  NUMERIC(19, 2) NOT NULL,
    counted_balance   NUMERIC(19, 2) NOT NULL,
    difference        NUMERIC(19, 2) NOT NULL,
    difference_txn_id BIGINT         REFERENCES cash_transactions (id),
    status            VARCHAR(10)    NOT NULL DEFAULT 'CLOSED' CHECK (status IN ('CLOSED', 'REOPENED')),
    notes             VARCHAR(300),
    closed_by         BIGINT         REFERENCES users (id),
    closed_at         TIMESTAMPTZ    NOT NULL,
    reopened_by       BIGINT         REFERENCES users (id),
    reopened_at       TIMESTAMPTZ,
    reopen_reason     VARCHAR(300),
    CHECK (difference = counted_balance - expected_balance),
    CHECK (difference = 0 OR notes IS NOT NULL),
    CHECK (status <> 'REOPENED' OR reopen_reason IS NOT NULL)
);
CREATE UNIQUE INDEX ux_cash_closings_closed ON cash_closings (business_date, cash_account_id) WHERE status = 'CLOSED';
CREATE INDEX ix_cash_closings_date ON cash_closings (business_date);

CREATE TABLE sales_returns (
    id                      BIGSERIAL PRIMARY KEY,
    return_no               VARCHAR(30)    NOT NULL UNIQUE,
    client_request_id       UUID           UNIQUE,
    sale_id                 BIGINT         NOT NULL REFERENCES sales (id),
    customer_id             BIGINT         NOT NULL REFERENCES customers (id),
    return_date             TIMESTAMPTZ    NOT NULL,
    business_date           DATE           NOT NULL,
    currency_code           CHAR(3)        NOT NULL REFERENCES currencies (code),
    exchange_rate           NUMERIC(19, 6) NOT NULL CHECK (exchange_rate > 0),
    total_amount            NUMERIC(19, 2) NOT NULL CHECK (total_amount >= 0),
    total_cost_base         NUMERIC(19, 2) NOT NULL DEFAULT 0,
    receivable_reduction    NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (receivable_reduction >= 0),
    cash_refund_amount      NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (cash_refund_amount >= 0),
    refund_cash_account_id  BIGINT         REFERENCES cash_accounts (id),
    credit_amount           NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (credit_amount >= 0),
    reason                  VARCHAR(300)   NOT NULL,
    status                  VARCHAR(15)    NOT NULL DEFAULT 'POSTED' CHECK (status IN ('POSTED')),
    created_at              TIMESTAMPTZ    NOT NULL,
    created_by              BIGINT         REFERENCES users (id),
    CHECK (total_amount = receivable_reduction + cash_refund_amount + credit_amount),
    CHECK (cash_refund_amount = 0 OR refund_cash_account_id IS NOT NULL)
);
CREATE INDEX ix_sales_returns_sale ON sales_returns (sale_id);
CREATE INDEX ix_sales_returns_date ON sales_returns (business_date);

CREATE TABLE sales_return_items (
    id               BIGSERIAL PRIMARY KEY,
    sales_return_id  BIGINT         NOT NULL REFERENCES sales_returns (id),
    sale_item_id     BIGINT         NOT NULL REFERENCES sale_items (id),
    product_id       BIGINT         NOT NULL REFERENCES products (id),
    product_name     VARCHAR(200)   NOT NULL,
    unit_name        VARCHAR(50),
    quantity         NUMERIC(14, 3) NOT NULL CHECK (quantity > 0),
    unit_net_price   NUMERIC(19, 4) NOT NULL CHECK (unit_net_price >= 0),
    line_total       NUMERIC(19, 2) NOT NULL CHECK (line_total >= 0),
    item_condition   VARCHAR(10)    NOT NULL CHECK (item_condition IN ('SELLABLE', 'DAMAGED')),
    unit_cost_base   NUMERIC(19, 4) NOT NULL,
    reason           VARCHAR(300)
);
CREATE INDEX ix_sales_return_items_return ON sales_return_items (sales_return_id);

CREATE TABLE purchase_returns (
    id                      BIGSERIAL PRIMARY KEY,
    return_no               VARCHAR(30)    NOT NULL UNIQUE,
    client_request_id       UUID           UNIQUE,
    purchase_id             BIGINT         NOT NULL REFERENCES purchases (id),
    supplier_id             BIGINT         NOT NULL REFERENCES suppliers (id),
    return_date             TIMESTAMPTZ    NOT NULL,
    business_date           DATE           NOT NULL,
    currency_code           CHAR(3)        NOT NULL REFERENCES currencies (code),
    exchange_rate           NUMERIC(19, 6) NOT NULL CHECK (exchange_rate > 0),
    total_amount            NUMERIC(19, 2) NOT NULL CHECK (total_amount >= 0),
    total_cost_base         NUMERIC(19, 2) NOT NULL DEFAULT 0,
    payable_reduction       NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (payable_reduction >= 0),
    cash_refund_amount      NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (cash_refund_amount >= 0),
    refund_cash_account_id  BIGINT         REFERENCES cash_accounts (id),
    credit_amount           NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (credit_amount >= 0),
    reason                  VARCHAR(300)   NOT NULL,
    status                  VARCHAR(15)    NOT NULL DEFAULT 'POSTED' CHECK (status IN ('POSTED')),
    created_at              TIMESTAMPTZ    NOT NULL,
    created_by              BIGINT         REFERENCES users (id),
    CHECK (total_amount = payable_reduction + cash_refund_amount + credit_amount),
    CHECK (cash_refund_amount = 0 OR refund_cash_account_id IS NOT NULL)
);
CREATE INDEX ix_purchase_returns_purchase ON purchase_returns (purchase_id);
CREATE INDEX ix_purchase_returns_date ON purchase_returns (business_date);

CREATE TABLE purchase_return_items (
    id                   BIGSERIAL PRIMARY KEY,
    purchase_return_id   BIGINT         NOT NULL REFERENCES purchase_returns (id),
    purchase_item_id     BIGINT         NOT NULL REFERENCES purchase_items (id),
    product_id           BIGINT         NOT NULL REFERENCES products (id),
    product_name         VARCHAR(200)   NOT NULL,
    unit_name            VARCHAR(50),
    quantity             NUMERIC(14, 3) NOT NULL CHECK (quantity > 0),
    unit_net_price       NUMERIC(19, 4) NOT NULL CHECK (unit_net_price >= 0),
    line_total           NUMERIC(19, 2) NOT NULL CHECK (line_total >= 0),
    unit_cost_base       NUMERIC(19, 4) NOT NULL,
    reason               VARCHAR(300)
);
CREATE INDEX ix_purchase_return_items_return ON purchase_return_items (purchase_return_id);
