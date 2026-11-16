# Store MIS — Retail & Wholesale Store Management Information System

A LAN-hosted, multilingual (English / دری / پښتو) store management system for retail and wholesale
shops in Afghanistan: POS, inventory (weighted average cost), purchases, customers and suppliers with
partial payments, AFN/USD cash management, expenses, returns, reports, P&L and audit logs.

**Stack:** Java 21 · Spring Boot 3.5 · Spring Security + JWT · JPA/Hibernate · Flyway · PostgreSQL 16 ·
React 19 + Vite · MUI · i18next · Recharts · Docker Compose · nginx.

## Status

| Phase | Scope | Status |
|---|---|---|
| 1 | Requirements & architecture | ✅ done (owner review pending) |
| 2 | UI/UX shell (layout, i18n, RTL, themes, shared components) | ✅ done — see [frontend/](frontend/README.md) |
| 3 | Backend foundation (auth, users, security, audit) | ✅ done — see [backend/](backend/README.md) |
| 4 | Products & inventory | ✅ done |
| 5 | Purchasing (+ cash accounts, exchange rates) | ✅ done |
| 6 | Sales & POS (+ customers, receipts, customer payments) | ✅ done |
| 7 | Finance & returns (expenses, transfers, exchange, daily closing, sales/purchase returns) | ✅ done |
| 8 | Dashboard & reports (17 reports, P&L, Excel / PDF / print, My Day) | ✅ done |
| 9 | Testing & deployment (Docker stack, backups, guides, CI) | ✅ done |

## Install on the store server (production)

```bash
git clone https://github.com/Sartajsadat/StoreMIS.git
cd StoreMIS
bash deploy/scripts/install.sh        # Windows: powershell -ExecutionPolicy Bypass -File deploy\scripts\install.ps1
```

Then open `http://<server-ip>/` on the shop PCs. Full instructions, backups, restore and updates:
[deployment guide](docs/phase-9/deployment-guide.md). Before going live, run the
[acceptance checklist](docs/phase-9/acceptance-checklist.md).

## Quick start (development)

```bash
docker compose -f deploy/docker-compose.dev.yml up -d     # PostgreSQL 16
cd backend && mvn spring-boot:run                          # API on :8080
cd frontend && npm install && npm run dev                  # UI on http://localhost:5173
```

Sign in as `admin` / `Admin12345` (development only). You will be asked to choose a new password.

## Design documents (Phase 1)

1. [System architecture](docs/phase-1/01-architecture.md): stack, topology, key decisions, open questions
2. [Modules & roles](docs/phase-1/02-modules-and-roles.md)
3. [Database design & ER diagrams](docs/phase-1/03-database-design.md)
4. [Business workflows & posting rules](docs/phase-1/04-business-workflows.md)
5. [REST API plan](docs/phase-1/05-api-plan.md)
6. [Project folder structure](docs/phase-1/06-project-structure.md)

## Operations (Phase 9)

- [Deployment and operations guide](docs/phase-9/deployment-guide.md)
- [Acceptance test checklist](docs/phase-9/acceptance-checklist.md)
