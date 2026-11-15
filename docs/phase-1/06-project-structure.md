# 06 — Project Folder Structure

## 1. Repository layout (monorepo)

```
StoreMIS/
├── backend/                    Spring Boot application (Maven)
├── frontend/                   React + Vite application
├── deploy/
│   ├── docker-compose.yml
│   ├── docker-compose.dev.yml
│   ├── .env.example
│   ├── nginx/
│   │   └── storemis.conf
│   ├── backup/
│   │   ├── Dockerfile
│   │   ├── backup.sh
│   │   └── restore.sh
│   └── scripts/
│       └── install-server.sh   Ubuntu: docker, firewall, static IP notes
├── docs/
│   ├── phase-1/                this design
│   ├── deployment.md           (Phase 9)
│   └── user-guide/             (en / fa / ps)
├── .github/workflows/ci.yml    build + tests (optional; the app itself needs no internet)
└── README.md
```

## 2. Backend: `backend/`

Root package `af.storemis`. **Package by module**, with layers inside each module.

```
backend/
├── pom.xml
├── Dockerfile
└── src/
    ├── main/
    │   ├── java/af/storemis/
    │   │   ├── StoreMisApplication.java
    │   │   ├── common/
    │   │   │   ├── api/            ApiResponse, PageResponse, ApiError, FieldErrorDto
    │   │   │   ├── exception/      BusinessException, ErrorCode (enum), NotFoundException,
    │   │   │   │                   ConflictException, GlobalExceptionHandler
    │   │   │   ├── entity/         BaseEntity (id), AuditableEntity (created/updated/version)
    │   │   │   ├── money/          Money utils, RoundingPolicy, CurrencyCode
    │   │   │   ├── time/           BusinessClock (Asia/Kabul, business_date)
    │   │   │   ├── security/       CurrentUser, SecurityUtils, PermissionCode
    │   │   │   └── validation/     custom constraint annotations
    │   │   ├── config/             SecurityConfig, JwtProperties, OpenApiConfig, JacksonConfig,
    │   │   │                       JpaAuditingConfig, FileStorageProperties
    │   │   ├── auth/
    │   │   │   ├── controller/AuthController.java
    │   │   │   ├── service/AuthService.java, JwtService.java, LoginAttemptService.java
    │   │   │   ├── security/JwtAuthenticationFilter.java, AppUserDetailsService.java
    │   │   │   ├── entity/RefreshToken.java
    │   │   │   ├── repository/RefreshTokenRepository.java
    │   │   │   └── dto/LoginRequest.java, TokenResponse.java, …
    │   │   ├── user/               (entity, repository, dto, mapper, service, controller)
    │   │   ├── audit/
    │   │   ├── settings/
    │   │   ├── currency/
    │   │   ├── numbering/          DocumentNumberService
    │   │   ├── catalog/            categories + units
    │   │   ├── product/
    │   │   ├── inventory/          InventoryPostingService, StockQueryService, AdjustmentService
    │   │   ├── customer/           CustomerService, CustomerLedgerService
    │   │   ├── supplier/           SupplierService, SupplierLedgerService
    │   │   ├── sales/              SalesService, SaleCalculator, ReceiptService
    │   │   ├── purchase/           PurchaseService, PurchaseCalculator
    │   │   ├── payment/            CustomerPaymentService, SupplierPaymentService, AllocationService
    │   │   ├── returns/            SalesReturnService, PurchaseReturnService
    │   │   ├── cash/               CashPostingService, CashTransferService, CashClosingService
    │   │   ├── expense/
    │   │   ├── dashboard/
    │   │   ├── reporting/
    │   │   │   ├── controller/ReportController.java
    │   │   │   ├── service/…ReportService (one per report family)
    │   │   │   ├── query/          native SQL / projections
    │   │   │   └── export/         ExcelExporter (POI), PdfExporter (OpenPDF), ReportLabels (en/fa/ps)
    │   │   └── files/              FileStorageService, FileController
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       ├── application-prod.yml
    │       ├── db/migration/       V001__… → V012__… (Flyway)
    │       ├── fonts/              Vazirmatn-*.ttf (PDF export)
    │       └── i18n/               report-labels_en/fa/ps.properties (PDF/Excel headers only)
    └── test/java/af/storemis/
        ├── ArchitectureTest.java   module boundaries, no float/double money, no logic in controllers
        ├── support/                Testcontainers base, fixtures, test data builders
        ├── inventory/              WAC + stock-consistency tests
        ├── sales/                  POS, partial payment, cancel tests
        ├── purchase/ payment/ returns/ cash/ reporting/   (P&L calculation tests)
        └── security/               role/permission access tests
```

Each feature module has the same internal shape:

```
<module>/
├── controller/   <Module>Controller.java
├── service/      <Module>Service.java (+ impl only if needed)
├── repository/   <Entity>Repository.java
├── entity/       <Entity>.java, enums
├── dto/          <Entity>Request.java, <Entity>Response.java (records)
└── mapper/       <Entity>Mapper.java (MapStruct)
```

## 3. Frontend: `frontend/`

```
frontend/
├── package.json
├── vite.config.js            proxy /api → :8080 in dev
├── index.html
├── Dockerfile                build → nginx image
├── eslint.config.js          incl. i18next/no-literal-string
└── src/
    ├── main.jsx
    ├── App.jsx               providers: Query, Auth, Preferences(Theme+Direction+Lang), Settings, Notify
    ├── assets/
    │   ├── fonts/            Inter, Vazirmatn (self-hosted, @font-face)
    │   └── images/
    ├── i18n/
    │   ├── index.js          i18next init, language detector (localStorage → user pref → default)
    │   ├── en.json
    │   ├── fa.json
    │   └── ps.json           namespaces inside: common, nav, auth, dashboard, products, inventory,
    │                         sales, purchases, customers, suppliers, finance, reports, users,
    │                         settings, validation, errors, enums
    ├── theme/
    │   ├── palette.js        tokens from the spec (light + dark)
    │   ├── createAppTheme.js (mode, direction) → MUI theme
    │   ├── components.js     MUI component overrides (cards, tables, buttons)
    │   └── rtlCache.js       Emotion caches (ltr / rtl via stylis-plugin-rtl)
    ├── contexts/
    │   ├── AuthContext.jsx         user, role, permissions, login/logout, token refresh
    │   ├── PreferencesContext.jsx  language, theme, sidebar; syncs to /me/preferences
    │   ├── SettingsContext.jsx     business info, currencies, current rate
    │   └── NotificationContext.jsx snackbar queue
    ├── services/
    │   ├── apiClient.js      Axios instance, auth header, refresh-on-401, error → i18n code
    │   ├── authService.js
    │   ├── userService.js  productService.js  categoryService.js  unitService.js
    │   ├── inventoryService.js  customerService.js  supplierService.js
    │   ├── salesService.js  purchaseService.js  paymentService.js  returnService.js
    │   ├── cashService.js  expenseService.js  settingsService.js  currencyService.js
    │   └── dashboardService.js  reportService.js  auditService.js
    ├── hooks/
    │   ├── usePermission.js  useDirection.js  useMoneyFormat.js  useDebounce.js
    │   └── useCrudQuery.js   (TanStack Query wrappers)
    ├── utils/
    │   ├── money.js          decimal-safe math (decimal.js); never JS float for totals
    │   ├── format.js         numbers, dates (Gregorian / Solar Hijri), digits
    │   └── constants.js
    ├── components/           shared, reusable
    │   ├── data/             DataTable (MUI DataGrid wrapper), FilterBar, DateRangeFilter, ExportButtons
    │   ├── form/             FormTextField, FormSelect, FormMoneyField, FormQtyField, FormDatePicker,
    │   │                     FormAutocomplete (customer/product), FormDialog
    │   ├── feedback/         ConfirmDialog, ReasonDialog, EmptyState, LoadingOverlay, ErrorBoundary
    │   ├── display/          KpiCard, StatusChip, MoneyText, Ltr, PageHeader, SectionCard
    │   ├── charts/           LineChartCard, BarChartCard, PieChartCard (theme-aware)
    │   └── print/            ReceiptThermal, InvoiceA4, ReportPrintLayout
    ├── layouts/
    │   ├── AppLayout.jsx     sidebar + topbar + outlet
    │   ├── Sidebar.jsx       groups, collapse, icon-only mode, mobile drawer, RTL anchor
    │   ├── Topbar.jsx        toggle, title + breadcrumbs, LanguageMenu, ThemeToggle,
    │   │                     NotificationsMenu, UserMenu
    │   ├── navConfig.js      menu tree with i18n keys, icons, required role/permission
    │   └── AuthLayout.jsx
    ├── routes/
    │   ├── AppRoutes.jsx
    │   ├── ProtectedRoute.jsx       requires login
    │   └── RequirePermission.jsx    role/permission guard
    └── modules/
        ├── auth/        LoginPage.jsx, ChangePasswordPage.jsx
        ├── dashboard/   DashboardPage.jsx, MyDayPage.jsx, widgets/
        ├── products/    ProductListPage, ProductFormDialog, CategoriesPage, UnitsPage
        ├── inventory/   StockOverviewPage, StockMovementsPage, AdjustmentsPage, LowStockPage
        ├── sales/       pos/ (PosPage, ProductSearch, Cart, PaymentPanel, CustomerPicker),
        │                SalesHistoryPage, SaleDetailsPage, SalesReturnsPage, NewSalesReturnPage
        ├── purchases/   PurchaseFormPage, PurchaseHistoryPage, PurchaseDetailsPage,
        │                PurchaseReturnsPage
        ├── customers/   CustomerListPage, CustomerDetailsPage, CustomerPaymentsPage
        ├── suppliers/   SupplierListPage, SupplierDetailsPage, SupplierPaymentsPage
        ├── finance/     CashAccountsPage, CashTransactionsPage, TransfersPage,
        │                DailyClosingPage
        ├── expenses/    ExpensesPage, ExpenseFormDialog
        ├── reports/     ReportsHubPage, ReportPage (generic shell), reportDefinitions.js
        ├── users/       UsersPage, UserFormDialog, ActivityLogsPage
        └── settings/    BusinessSettingsPage, CurrencySettingsPage, InvoiceSettingsPage,
                         AppearancePage, ExpenseCategoriesPage
```

## 4. Naming conventions

| Thing | Convention | Example |
|---|---|---|
| DB tables / columns | snake_case, plural tables | `sale_items.unit_cost_base` |
| Java classes | PascalCase; DTOs end in `Request`/`Response` | `SaleConfirmRequest` |
| JSON fields | camelCase | `balanceDue` |
| REST paths | kebab-case plural nouns, verbs only for actions | `/customer-payments/{id}/reverse` |
| Error codes | UPPER_SNAKE | `DAY_CLOSED` |
| i18n keys | `namespace.section.key` | `sales.pos.amountReceived` |
| React components | PascalCase files, one component per file | `KpiCard.jsx` |
| Services (JS) | camelCase `xxxService.js` | `salesService.confirm()` |
