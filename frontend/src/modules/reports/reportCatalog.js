import dayjs from 'dayjs';

/**
 * Which reports each menu entry shows (as tabs) and how each report is filtered.
 * - filters: extra filter controls next to the search box
 * - paged:   false for summaries that always return every row
 * - noDates: the report is a snapshot of today (stock, balances)
 * - chart:   optional chart above the table
 */
export const REPORTS = {
  'sales-daily': { filters: [], paged: false, preset: 'MONTH', chart: 'trend', noSearch: true },
  'sales-monthly': { filters: [], paged: false, preset: 'YEAR', chart: 'trend', noSearch: true },
  'sales-yearly': {
    filters: [],
    paged: false,
    preset: 'CUSTOM',
    custom: () => ({ from: dayjs().subtract(4, 'year').startOf('year').format('YYYY-MM-DD'), to: dayjs().format('YYYY-MM-DD') }),
    chart: 'bars',
    noSearch: true,
  },
  'sales-by-type': { filters: [], paged: false, preset: 'MONTH', chart: 'type', noSearch: true },
  'best-selling': { filters: ['category', 'bestSort'], preset: 'MONTH', chart: 'top' },
  'customer-payments': { filters: ['currency', 'account', 'paymentStatus'], preset: 'MONTH' },
  purchases: { filters: ['currency', 'purchasePaymentStatus'], preset: 'MONTH' },
  suppliers: { filters: [], paged: false, preset: 'MONTH' },
  inventory: { filters: ['category', 'stockStatus'], noDates: true },
  'stock-movements': { filters: ['category', 'movementType'], preset: 'MONTH' },
  'low-stock': { filters: ['category'], noDates: true },
  'damaged-stock': { filters: [], preset: 'MONTH' },
  'customer-balances': { filters: ['customerType', 'nonZero'], noDates: true, defaults: { nonZero: true } },
  'supplier-balances': { filters: ['nonZero'], noDates: true, defaults: { nonZero: true } },
  expenses: { filters: ['expenseCategory', 'account', 'expenseStatus'], preset: 'MONTH', chart: 'byCategory' },
  'cash-transactions': { filters: ['account', 'cashType'], preset: 'MONTH', accounts: true },
};

/** Menu entry → its reports. */
export const REPORT_GROUPS = {
  salesReports: ['sales-daily', 'sales-monthly', 'sales-yearly', 'sales-by-type', 'best-selling', 'customer-payments'],
  purchaseReports: ['purchases', 'suppliers'],
  inventoryReports: ['inventory', 'stock-movements', 'low-stock', 'damaged-stock'],
  customerBalances: ['customer-balances'],
  supplierBalances: ['supplier-balances'],
  cashReports: ['cash-transactions', 'expenses'],
};
