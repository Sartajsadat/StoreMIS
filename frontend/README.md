# Store MIS — Frontend

React 19 + Vite + MUI 7. Three languages (English, دری, پښتو) with full RTL support, and light/dark themes.

## Commands

```bash
npm install
npm run dev          # http://localhost:5173 — proxies /api to http://localhost:8080
npm run build        # production build in dist/ (served by nginx)
npm run lint         # ESLint, including i18next/no-literal-string (no hardcoded UI text)
npm test             # Vitest unit tests
npm run i18n:check   # en/fa/ps must contain exactly the same keys
```

In development, the component gallery is at `/dev/ui-kit` (sample data, not included in production builds).

## Conventions

- **Text:** every visible string comes from `src/i18n/locales/{en,fa,ps}.json`. Backend errors arrive as codes
  (`errors.<CODE>`), and form validation messages are i18n keys (`utils/validation.js`).
- **Direction:** `PreferencesContext` sets `<html dir/lang>`. `AppThemeProvider` switches the Emotion cache
  (`stylis-plugin-rtl` mirrors all CSS) and the MUI theme direction. Use `<Ltr>` for numbers, codes, invoice numbers and phones.
  Use `<DirectionalIcon>` for arrows and chevrons.
- **Money:** strings from the API, `decimal.js` for any arithmetic (`utils/money.js`), `<MoneyText>` for display.
  Currencies are never summed together.
- **Navigation:** `layouts/navConfig.js` is the single source for the sidebar, routes, permissions, titles and breadcrumbs.
- **Fonts** are self-hosted (`@fontsource`), so the app needs no internet access.
