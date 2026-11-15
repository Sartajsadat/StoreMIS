-- Store MIS — reference data, users, authentication and audit.
-- Conventions: docs/phase-1/03-database-design.md

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE currencies (
    code            CHAR(3)      PRIMARY KEY,
    name            VARCHAR(50)  NOT NULL,
    symbol          VARCHAR(5)   NOT NULL,
    decimal_places  SMALLINT     NOT NULL DEFAULT 2 CHECK (decimal_places BETWEEN 0 AND 4),
    active          BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE roles (
    id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code  VARCHAR(30)  NOT NULL UNIQUE,
    name  VARCHAR(100) NOT NULL
);

CREATE TABLE permissions (
    code         VARCHAR(50)  PRIMARY KEY,
    description  VARCHAR(200) NOT NULL,
    default_for_entry_user BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE users (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username               VARCHAR(50)  NOT NULL,
    password_hash          VARCHAR(100) NOT NULL,
    full_name              VARCHAR(150) NOT NULL,
    phone                  VARCHAR(30),
    role_id                BIGINT       NOT NULL REFERENCES roles (id),
    active                 BOOLEAN      NOT NULL DEFAULT TRUE,
    must_change_password   BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_attempts  INT          NOT NULL DEFAULT 0,
    locked_until           TIMESTAMPTZ,
    last_login_at          TIMESTAMPTZ,
    password_changed_at    TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by             BIGINT       REFERENCES users (id),
    updated_at             TIMESTAMPTZ,
    updated_by             BIGINT       REFERENCES users (id),
    version                INT          NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_users_username ON users (lower(username));
CREATE INDEX ix_users_role ON users (role_id);

CREATE TABLE user_permissions (
    user_id          BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    permission_code  VARCHAR(50) NOT NULL REFERENCES permissions (code),
    granted_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, permission_code)
);

CREATE TABLE user_preferences (
    user_id            BIGINT      PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    language           VARCHAR(5)  NOT NULL DEFAULT 'en' CHECK (language IN ('en', 'fa', 'ps')),
    theme              VARCHAR(10) NOT NULL DEFAULT 'light' CHECK (theme IN ('light', 'dark')),
    sidebar_collapsed  BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at         TIMESTAMPTZ
);

CREATE TABLE refresh_tokens (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash      VARCHAR(64)  NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ  NOT NULL,
    revoked_at      TIMESTAMPTZ,
    replaced_by_id  BIGINT       REFERENCES refresh_tokens (id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(255)
);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);

-- Append-only activity log. The production database role is not granted UPDATE/DELETE on it.
CREATE TABLE audit_logs (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    user_id      BIGINT       REFERENCES users (id),
    username     VARCHAR(50),
    action       VARCHAR(40)  NOT NULL,
    module       VARCHAR(30)  NOT NULL,
    entity_type  VARCHAR(40),
    entity_id    BIGINT,
    reference    VARCHAR(50),
    description  VARCHAR(500),
    details      JSONB,
    ip_address   VARCHAR(45)
);
CREATE INDEX ix_audit_occurred ON audit_logs (occurred_at DESC);
CREATE INDEX ix_audit_user ON audit_logs (user_id, occurred_at DESC);
CREATE INDEX ix_audit_module ON audit_logs (module, occurred_at DESC);
CREATE INDEX ix_audit_entity ON audit_logs (entity_type, entity_id);
