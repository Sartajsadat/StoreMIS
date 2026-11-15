# Store MIS — Retail & Wholesale Store Management Information System

A LAN-hosted, multilingual (English / دری / پښتو) store management system for retail and wholesale
shops in Afghanistan: POS, inventory (weighted average cost), purchases, customers and suppliers with
partial payments, AFN/USD cash management, expenses, returns, reports, P&L and audit logs.

**Stack:** Java 21 · Spring Boot 3.5 · Spring Security + JWT · JPA/Hibernate · Flyway · PostgreSQL 16 ·
React 19 + Vite · MUI · i18next · Recharts · Docker Compose · nginx.

## Status

| Phase | Scope | Status |
|---|---|---|
| 1 | Requirements & architecture | 📝 awaiting approval |
| 2 | UI/UX shell (layout, i18n, RTL, themes, shared components) | ⏳ |
| 3 | Backend foundation (auth, users, security, audit) | ⏳ |
| 4 | Products & inventory | ⏳ |
| 5 | Purchasing | ⏳ |
| 6 | Sales & POS | ⏳ |
| 7 | Finance & returns | ⏳ |
| 8 | Dashboard & reports | ⏳ |
| 9 | Testing & deployment | ⏳ |

## Design documents (Phase 1)

1. [System architecture](docs/phase-1/01-architecture.md): stack, topology, key decisions, open questions
2. [Modules & roles](docs/phase-1/02-modules-and-roles.md)
3. [Database design & ER diagrams](docs/phase-1/03-database-design.md)
4. [Business workflows & posting rules](docs/phase-1/04-business-workflows.md)
5. [REST API plan](docs/phase-1/05-api-plan.md)
6. [Project folder structure](docs/phase-1/06-project-structure.md)
