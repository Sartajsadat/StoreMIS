-- Business settings (single row), document numbering and exchange rate history.

CREATE TABLE business_settings (
    id                     INT          PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    business_name          VARCHAR(150) NOT NULL,
    logo_path              VARCHAR(255),
    address                VARCHAR(300),
    phone                  VARCHAR(50),
    email                  VARCHAR(150),
    base_currency_code     CHAR(3)      NOT NULL REFERENCES currencies (code),
    default_currency_code  CHAR(3)      NOT NULL REFERENCES currencies (code),
    default_language       VARCHAR(5)   NOT NULL DEFAULT 'en' CHECK (default_language IN ('en', 'fa', 'ps')),
    default_theme          VARCHAR(10)  NOT NULL DEFAULT 'light' CHECK (default_theme IN ('light', 'dark')),
    calendar_display       VARCHAR(15)  NOT NULL DEFAULT 'GREGORIAN' CHECK (calendar_display IN ('GREGORIAN', 'SOLAR_HIJRI')),
    digit_style            VARCHAR(10)  NOT NULL DEFAULT 'LATIN' CHECK (digit_style IN ('LATIN', 'PERSIAN')),
    allow_negative_stock   BOOLEAN      NOT NULL DEFAULT FALSE,
    allow_negative_cash    BOOLEAN      NOT NULL DEFAULT FALSE,
    block_sale_below_cost  BOOLEAN      NOT NULL DEFAULT FALSE,
    receipt_format         VARCHAR(15)  NOT NULL DEFAULT 'THERMAL_80' CHECK (receipt_format IN ('THERMAL_80', 'THERMAL_58', 'A4')),
    invoice_header_note    VARCHAR(500),
    invoice_footer_note    VARCHAR(500),
    show_logo_on_receipt   BOOLEAN      NOT NULL DEFAULT TRUE,
    timezone               VARCHAR(40)  NOT NULL DEFAULT 'Asia/Kabul',
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by             BIGINT       REFERENCES users (id),
    updated_at             TIMESTAMPTZ,
    updated_by             BIGINT       REFERENCES users (id),
    version                INT          NOT NULL DEFAULT 0
);

CREATE TABLE document_sequences (
    doc_type      VARCHAR(30) PRIMARY KEY,
    prefix        VARCHAR(10) NOT NULL,
    yearly_reset  BOOLEAN     NOT NULL DEFAULT TRUE,
    current_year  INT         NOT NULL,
    next_number   BIGINT      NOT NULL DEFAULT 1 CHECK (next_number > 0),
    padding       SMALLINT    NOT NULL DEFAULT 6 CHECK (padding BETWEEN 3 AND 10)
);

CREATE TABLE exchange_rates (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    currency_code  CHAR(3)        NOT NULL REFERENCES currencies (code),
    rate           NUMERIC(19, 6) NOT NULL CHECK (rate > 0),
    effective_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    notes          VARCHAR(200),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by     BIGINT         REFERENCES users (id)
);
CREATE INDEX ix_exchange_rates_current ON exchange_rates (currency_code, effective_at DESC);
