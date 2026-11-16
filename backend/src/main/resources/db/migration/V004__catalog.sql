-- Categories, units, products and the cached stock balance per product.

CREATE TABLE categories (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    description  VARCHAR(300),
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   BIGINT       REFERENCES users (id),
    updated_at   TIMESTAMPTZ,
    updated_by   BIGINT       REFERENCES users (id),
    version      INT          NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_categories_name ON categories (lower(name));

-- System units carry a code (translated by the UI); units added by the admin have code NULL.
CREATE TABLE units (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code           VARCHAR(30)  UNIQUE,
    name           VARCHAR(50)  NOT NULL,
    symbol         VARCHAR(10),
    allow_decimal  BOOLEAN      NOT NULL DEFAULT FALSE,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     BIGINT       REFERENCES users (id),
    updated_at     TIMESTAMPTZ,
    updated_by     BIGINT       REFERENCES users (id),
    version        INT          NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_units_name ON units (lower(name));

INSERT INTO units (code, name, symbol, allow_decimal) VALUES
    ('PIECE',    'Piece',    'pcs',  FALSE),
    ('BOX',      'Box',      'box',  FALSE),
    ('PACKET',   'Packet',   'pkt',  FALSE),
    ('METER',    'Meter',    'm',    TRUE),
    ('KILOGRAM', 'Kilogram', 'kg',   TRUE),
    ('LITER',    'Liter',    'L',    TRUE),
    ('SET',      'Set',      'set',  FALSE),
    ('ROLL',     'Roll',     'roll', FALSE);

-- No selling price: it is entered for every sale line.
CREATE TABLE products (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name             VARCHAR(200)   NOT NULL,
    code             VARCHAR(60),
    category_id      BIGINT         NOT NULL REFERENCES categories (id),
    unit_id          BIGINT         NOT NULL REFERENCES units (id),
    brand            VARCHAR(100),
    description      TEXT,
    min_stock_level  NUMERIC(14, 3) CHECK (min_stock_level >= 0),
    image_path       VARCHAR(255),
    active           BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by       BIGINT         REFERENCES users (id),
    updated_at       TIMESTAMPTZ,
    updated_by       BIGINT         REFERENCES users (id),
    version          INT            NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_products_code ON products (lower(code)) WHERE code IS NOT NULL;
CREATE INDEX ix_products_category ON products (category_id);
CREATE INDEX ix_products_name_trgm ON products USING gin (name gin_trgm_ops);
CREATE INDEX ix_products_code_trgm ON products USING gin (code gin_trgm_ops);

-- Cached balance, always updated in the same transaction as stock_movements and locked FOR UPDATE.
-- It can be rebuilt from the ledger at any time (see the integrity check).
CREATE TABLE product_stock (
    product_id        BIGINT         PRIMARY KEY REFERENCES products (id),
    quantity_on_hand  NUMERIC(14, 3) NOT NULL DEFAULT 0,
    avg_unit_cost     NUMERIC(19, 4) NOT NULL DEFAULT 0 CHECK (avg_unit_cost >= 0),
    inventory_value   NUMERIC(19, 4) NOT NULL DEFAULT 0,
    last_movement_at  TIMESTAMPTZ,
    version           INT            NOT NULL DEFAULT 0
);
