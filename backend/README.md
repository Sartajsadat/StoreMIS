# Store MIS — Backend

Java 21 · Spring Boot 3.5 · Spring Security (JWT) · JPA/Hibernate · Flyway · PostgreSQL 16.

## Run locally

```bash
docker compose -f ../deploy/docker-compose.dev.yml up -d   # PostgreSQL on :5432 (storemis/storemis)
mvn spring-boot:run                                       # API on http://localhost:8080 (profile "dev")
```

On first start with an empty database, Flyway creates the schema and an administrator is created:
username `admin`, temporary password `Admin12345` (dev profile only). The password must be changed at first sign-in.
In production, set `STOREMIS_ADMIN_PASSWORD`.

Swagger UI (dev): http://localhost:8080/api/swagger-ui.html

## Tests

```bash
mvn test
```

Integration tests start PostgreSQL 16 in Docker (Testcontainers). Without Docker, point them at an existing
empty database with `STOREMIS_TEST_JDBC_URL`, `STOREMIS_TEST_JDBC_USER` and `STOREMIS_TEST_JDBC_PASSWORD`.

## Configuration (environment variables)

| Variable | Default | Purpose |
|---|---|---|
| `STOREMIS_DB_URL` / `_USER` / `_PASSWORD` | `jdbc:postgresql://localhost:5432/storemis`, `storemis`, `storemis` | Database |
| `STOREMIS_JWT_SECRET` | dev value | **Required in production**: at least 32 random bytes |
| `STOREMIS_ACCESS_TTL` / `STOREMIS_REFRESH_TTL` | `30m` / `12h` | Token lifetimes |
| `STOREMIS_COOKIE_SECURE` | `false` | `true` only when served over HTTPS |
| `STOREMIS_ADMIN_USERNAME` / `_PASSWORD` / `_FULLNAME` | `admin` / — / `Administrator` | First admin (only when no users exist) |
| `STOREMIS_FILES_DIR` | `./data/files` | Uploaded logo, images, attachments |

## Structure

Package by module (`af.storemis.<module>`), each with `controller → service → repository → entity` and `dto`.
Controllers never touch repositories, and money/quantity fields are never `float`/`double`. Both rules are
enforced by `ArchitectureTest`. Errors are returned as language-neutral codes (`ErrorCode`) that the frontend
translates.

Implemented so far:
- Phase 3: `auth`, `user`, `audit`, `settings`, `files`, plus `common` and `config`
- Phase 4: `numbering` (gap-free document numbers), `catalog` (categories, units), `product`, and `inventory`.
  All stock changes go through `InventoryPostingService`. It locks the product's stock row, appends a
  `stock_movements` ledger row and updates the cached balance and weighted average cost in the same
  transaction. Sales and purchases will reuse it.

- Phase 5: `currency` (exchange rates; every document keeps its own rate), `cash` (`CashPostingService` locks
  the account, blocks negative cash and appends to the `cash_transactions` ledger), `supplier` (per-currency
  payable ledger, statements, opening balances), `purchase` (drafts, confirm, cancel; `PurchaseCalculator`
  spreads the overall discount into landed cost) and `payment` (supplier payments with oldest-first allocation,
  advances, cross-currency payments, reversal).

`mvn spring-boot:run` starts the API with a 512 MB memory limit (set in `pom.xml`), so it runs on small store PCs.
