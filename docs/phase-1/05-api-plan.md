# 05 — REST API Plan

Base path: **`/api/v1`**. JSON throughout. Auth: `Authorization: Bearer <accessToken>`, except
`/auth/login` and `/auth/refresh`. Each endpoint below is implemented in the module of the same name.
The frontend `services/*.js` files will mirror this table one-to-one.

## 1. Conventions

**Success envelope**

```json
{ "success": true, "data": { }, "message": null, "timestamp": "2026-11-15T10:20:00+04:30" }
```

**Paged data**: `GET …?page=0&size=20&sort=createdAt,desc&q=…&from=2026-11-01&to=2026-11-15`

```json
{ "content": [], "page": 0, "size": 20, "totalElements": 134, "totalPages": 7 }
```

**Error envelope**: the code is translated on the frontend (`errors.STOCK_INSUFFICIENT` in en/fa/ps)

```json
{
  "success": false,
  "error": {
    "code": "STOCK_INSUFFICIENT",
    "params": { "product": "LED TV 43\"", "available": 2 },
    "fieldErrors": [ { "field": "items[0].quantity", "code": "MIN", "params": { "min": 0.001 } } ]
  },
  "timestamp": "…"
}
```

HTTP status: 400 validation, 401 unauthenticated, 403 forbidden, 404 not found, 409 conflict
(optimistic lock, duplicate, day closed, insufficient stock/cash), 422 business rule.

Idempotent posting endpoints accept the header `Idempotency-Key: <uuid>`, stored as `client_request_id`.

Role notation: **A** = Admin, **E** = Entry User baseline, **E:perm** = Entry User with that permission.

## 2. Endpoints

### auth
| Method | Path | Role | Purpose |
|---|---|---|---|
| POST | `/auth/login` | public | username/password → access + refresh token, user, preferences |
| POST | `/auth/refresh` | public (refresh token) | rotate tokens |
| POST | `/auth/logout` | A, E | revoke refresh token |
| GET | `/auth/me` | A, E | current user, role, permissions, preferences |
| POST | `/auth/change-password` | A, E | own password |

### users & preferences
| Method | Path | Role |
|---|---|---|
| GET / POST | `/users` | A |
| GET / PUT | `/users/{id}` | A |
| PATCH | `/users/{id}/status` `{active}` | A |
| POST | `/users/{id}/reset-password` | A |
| PUT | `/users/{id}/permissions` `{codes:[…]}` | A |
| GET | `/permissions` | A |
| GET / PUT | `/me/preferences` `{language, theme, sidebarCollapsed}` | A, E |

### audit
| GET | `/audit-logs?userId&module&action&from&to&q` | A |
|---|---|---|

### settings & currency
| Method | Path | Role |
|---|---|---|
| GET | `/settings/public` (name, logo, default language/theme; used by the login screen) | public |
| GET / PUT | `/settings/business` | A (GET also E, read-only subset) |
| POST | `/settings/business/logo` (multipart) | A |
| GET / PUT | `/settings/invoice` (receipt format, notes, sequences/prefixes) | A |
| GET | `/currencies` | A, E |
| GET | `/exchange-rates/current` | A, E |
| GET / POST | `/exchange-rates` (history / new rate) | A |

### catalog & products
| Method | Path | Role |
|---|---|---|
| GET | `/categories` `?active` | A, E |
| POST / PUT | `/categories`, `/categories/{id}` | A |
| PATCH | `/categories/{id}/status` | A |
| GET / POST / PUT / PATCH status | `/units…` (same pattern) | A (GET: E) |
| GET | `/products?q&categoryId&active&lowStock` | A, E |
| GET | `/products/search?q=` (POS fast search: id, name, code, unit, qty on hand, last prices) | A, E |
| GET | `/products/{id}` | A, E |
| POST / PUT | `/products`, `/products/{id}` | A |
| PATCH | `/products/{id}/status` | A |
| POST / DELETE | `/products/{id}/image` | A |
| GET | `/products/{id}/price-hints?customerId=` | A, E |

### inventory
| Method | Path | Role |
|---|---|---|
| GET | `/inventory/stock?q&categoryId&status=LOW\|OUT\|OK` | A, E (cost fields only with `VIEW_COST_PRICES`) |
| GET | `/inventory/stock/{productId}` | A, E |
| GET | `/inventory/movements?productId&type&from&to` | A, E:ADJUST_STOCK |
| GET | `/inventory/adjustments` | A, E:ADJUST_STOCK |
| POST | `/inventory/adjustments` `{productId, type, quantity, unitCost?, reason}` | A, E:ADJUST_STOCK (type `OPENING`: A) |
| GET | `/inventory/low-stock` | A, E |
| GET | `/inventory/integrity-check` | A |

### customers
| Method | Path | Role |
|---|---|---|
| GET / POST | `/customers?q&type&active&withBalance` | A, E |
| GET / PUT | `/customers/{id}` | A, E |
| PATCH | `/customers/{id}/status` | A |
| GET | `/customers/{id}/summary` (total purchases, balances per currency, credit) | A, E |
| GET | `/customers/{id}/open-invoices` | A, E |
| GET | `/customers/{id}/sales` | A, E |
| GET | `/customers/{id}/payments` | A, E |
| GET | `/customers/{id}/statement?currency&from&to` (+ `/pdf`) | A, E |
| POST | `/customers/{id}/opening-balance` | A |

### suppliers
Same as customers under `/suppliers` (open-purchases, purchases, payments, statement, opening-balance).
Write access is A or E:RECORD_PURCHASES. Read access is A, E:RECORD_PURCHASES or E:PAY_SUPPLIERS.

### sales
| Method | Path | Role |
|---|---|---|
| POST | `/sales/confirm` (POS one-step: items + payments) | A, E |
| POST | `/sales/drafts` | A, E |
| GET | `/sales/drafts` (own held carts) | A, E |
| PUT / DELETE | `/sales/drafts/{id}` | A, E (owner) |
| POST | `/sales/drafts/{id}/confirm` | A, E (owner) |
| GET | `/sales?q&customerId&status&paymentStatus&saleType&currency&userId&from&to` | A, E (own only unless `VIEW_ALL_SALES`) |
| GET | `/sales/{id}` | A, E |
| GET | `/sales/{id}/receipt` (print model: business info + lines + payments) | A, E |
| POST | `/sales/{id}/cancel` `{reason, paymentHandling: REFUND\|CREDIT, refundAccountId?}` | A |

### purchases
| Method | Path | Role |
|---|---|---|
| POST / GET | `/purchases` (create draft / list) | A, E:RECORD_PURCHASES |
| GET / PUT / DELETE | `/purchases/{id}` (PUT/DELETE drafts only) | A, E:RECORD_PURCHASES |
| POST | `/purchases/{id}/confirm` `{amountPaid, cashAccountId}` | A, E:RECORD_PURCHASES |
| POST | `/purchases/{id}/cancel` `{reason, paymentHandling}` | A |

### payments
| Method | Path | Role |
|---|---|---|
| GET / POST | `/customer-payments` `{customerId, amount, currency, rate, cashAccountId, allocations?: [{saleId, amount}], autoAllocate}` | A, E:RECEIVE_CUSTOMER_PAYMENTS |
| GET | `/customer-payments/{id}` (+ `/receipt`) | A, E:RECEIVE_CUSTOMER_PAYMENTS |
| POST | `/customer-payments/{id}/reverse` `{reason}` | A |
| POST | `/customer-payments/apply-credit` `{customerId, saleId, amount}` | A, E:RECEIVE_CUSTOMER_PAYMENTS |
| GET / POST | `/supplier-payments` | A, E:PAY_SUPPLIERS |
| GET | `/supplier-payments/{id}` | A, E:PAY_SUPPLIERS |
| POST | `/supplier-payments/{id}/reverse` | A |

### returns
| Method | Path | Role |
|---|---|---|
| GET | `/sales/{id}/returnable-items` | A, E:PROCESS_SALES_RETURNS |
| GET / POST | `/sales-returns` | A, E:PROCESS_SALES_RETURNS |
| GET | `/sales-returns/{id}` | A, E:PROCESS_SALES_RETURNS |
| GET | `/purchases/{id}/returnable-items` | A |
| GET / POST | `/purchase-returns` | A |
| GET | `/purchase-returns/{id}` | A |

### cash & finance
| Method | Path | Role |
|---|---|---|
| GET | `/cash-accounts` | A (E: names only, for payment pickers) |
| POST / PUT | `/cash-accounts`, `/cash-accounts/{id}` | A |
| POST | `/cash-accounts/{id}/opening-balance` | A |
| GET | `/cash-transactions?accountId&type&from&to&userId` | A |
| POST | `/cash-transactions/deposit` / `/withdrawal` | A |
| POST / GET | `/cash-transfers` (`type: TRANSFER\|EXCHANGE`) | A |
| GET | `/cash-closings/preview?date=` | A |
| POST / GET | `/cash-closings` | A |
| POST | `/cash-closings/{id}/reopen` `{reason}` | A |

### expenses
| Method | Path | Role |
|---|---|---|
| GET | `/expense-categories` | A, E:RECORD_EXPENSES |
| POST / PUT / PATCH status | `/expense-categories…` | A |
| GET / POST | `/expenses` | A, E:RECORD_EXPENSES (E sees own) |
| GET | `/expenses/{id}` | A, E:RECORD_EXPENSES |
| POST | `/expenses/{id}/attachment` | A, E:RECORD_EXPENSES |
| POST | `/expenses/{id}/void` `{reason}` | A |

### dashboard
| GET | `/dashboard/summary?range=TODAY\|WEEK\|MONTH\|YEAR\|CUSTOM&from&to` (KPIs, per currency) | A |
|---|---|---|
| GET | `/dashboard/charts?…` (daily/monthly sales, retail vs wholesale, purchases, expenses, top products) | A |
| GET | `/dashboard/recent` (sales, purchases, payments, activities, low stock) | A |
| GET | `/dashboard/my-day` | E |

### reports
All reports use `GET /reports/{key}?from&to&q&page&size&…` and `GET /reports/{key}/export?format=pdf|xlsx&lang=fa`. Role: **A**.

| key | Report |
|---|---|
| `sales-daily`, `sales-monthly`, `sales-yearly` | 1–3 |
| `sales-by-type` | 4 Retail vs Wholesale |
| `purchases` | 5 |
| `suppliers` | 6 |
| `inventory` | 7 (qty, WAC, value) |
| `stock-movements` | 8 |
| `low-stock` | 9 |
| `damaged-stock` | 10 |
| `customer-balances` | 11 |
| `supplier-balances` | 12 |
| `customer-payments` | 13 |
| `expenses` | 14 |
| `cash-transactions` | 15 |
| `profit-loss` | 16 |
| `best-selling` | 17 |
| `slow-moving?days=90` | 18 |
| `user-activity` | 19 |

### files
| GET | `/files/{type}/{name}` (product images, logo; expense attachments need A or the recorder) | A, E |
|---|---|---|

Swagger UI: `/api/swagger-ui.html` (enabled in dev; Admin-only in prod).
