import { matchPath } from 'react-router-dom';
import DashboardOutlined from '@mui/icons-material/DashboardOutlined';
import PointOfSaleOutlined from '@mui/icons-material/PointOfSaleOutlined';
import Inventory2Outlined from '@mui/icons-material/Inventory2Outlined';
import WarehouseOutlined from '@mui/icons-material/WarehouseOutlined';
import ShoppingCartOutlined from '@mui/icons-material/ShoppingCartOutlined';
import PeopleAltOutlined from '@mui/icons-material/PeopleAltOutlined';
import LocalShippingOutlined from '@mui/icons-material/LocalShippingOutlined';
import AccountBalanceWalletOutlined from '@mui/icons-material/AccountBalanceWalletOutlined';
import AssessmentOutlined from '@mui/icons-material/AssessmentOutlined';
import ManageAccountsOutlined from '@mui/icons-material/ManageAccountsOutlined';
import SettingsOutlined from '@mui/icons-material/SettingsOutlined';
import { PERMISSIONS as P } from '../utils/permissions';

/**
 * Single source of truth for the sidebar, routes, page titles and breadcrumbs.
 * - labelKey: i18n key (nav.*)
 * - access:   see utils/permissions.canAccess
 * - phase:    development phase that delivers the screen (until then a "coming soon" page is shown)
 */
export const NAV = [
  { key: 'dashboard', labelKey: 'nav.dashboard', icon: DashboardOutlined, path: '/', phase: 8 },
  {
    key: 'sales',
    labelKey: 'nav.sales',
    icon: PointOfSaleOutlined,
    children: [
      { key: 'newSale', labelKey: 'nav.newSale', path: '/sales/new', phase: 6 },
      { key: 'salesHistory', labelKey: 'nav.salesHistory', path: '/sales', phase: 6 },
      { key: 'salesReturns', labelKey: 'nav.salesReturns', path: '/sales/returns', phase: 7, access: { anyOf: [P.PROCESS_SALES_RETURNS] } },
    ],
  },
  {
    key: 'products',
    labelKey: 'nav.products',
    icon: Inventory2Outlined,
    children: [
      { key: 'productList', labelKey: 'nav.productList', path: '/products', phase: 4 },
      { key: 'categories', labelKey: 'nav.categories', path: '/products/categories', phase: 4, access: { adminOnly: true } },
      { key: 'units', labelKey: 'nav.units', path: '/products/units', phase: 4, access: { adminOnly: true } },
    ],
  },
  {
    key: 'inventory',
    labelKey: 'nav.inventory',
    icon: WarehouseOutlined,
    children: [
      { key: 'stockOverview', labelKey: 'nav.stockOverview', path: '/inventory', phase: 4 },
      { key: 'stockMovements', labelKey: 'nav.stockMovements', path: '/inventory/movements', phase: 4, access: { anyOf: [P.ADJUST_STOCK] } },
      { key: 'stockAdjustments', labelKey: 'nav.stockAdjustments', path: '/inventory/adjustments', phase: 4, access: { anyOf: [P.ADJUST_STOCK] } },
      { key: 'lowStock', labelKey: 'nav.lowStock', path: '/inventory/low-stock', phase: 4 },
    ],
  },
  {
    key: 'purchases',
    labelKey: 'nav.purchases',
    icon: ShoppingCartOutlined,
    children: [
      { key: 'newPurchase', labelKey: 'nav.newPurchase', path: '/purchases/new', phase: 5, access: { anyOf: [P.RECORD_PURCHASES] } },
      { key: 'purchaseHistory', labelKey: 'nav.purchaseHistory', path: '/purchases', phase: 5, access: { anyOf: [P.RECORD_PURCHASES] } },
      { key: 'purchaseReturns', labelKey: 'nav.purchaseReturns', path: '/purchases/returns', phase: 7, access: { adminOnly: true } },
    ],
  },
  {
    key: 'customers',
    labelKey: 'nav.customers',
    icon: PeopleAltOutlined,
    children: [
      { key: 'customerList', labelKey: 'nav.customerList', path: '/customers', phase: 6 },
      { key: 'customerPayments', labelKey: 'nav.customerPayments', path: '/customers/payments', phase: 6, access: { anyOf: [P.RECEIVE_CUSTOMER_PAYMENTS] } },
    ],
  },
  {
    key: 'suppliers',
    labelKey: 'nav.suppliers',
    icon: LocalShippingOutlined,
    children: [
      { key: 'supplierList', labelKey: 'nav.supplierList', path: '/suppliers', phase: 5, access: { anyOf: [P.RECORD_PURCHASES, P.PAY_SUPPLIERS] } },
      { key: 'supplierPayments', labelKey: 'nav.supplierPayments', path: '/suppliers/payments', phase: 5, access: { anyOf: [P.PAY_SUPPLIERS] } },
    ],
  },
  {
    key: 'finance',
    labelKey: 'nav.finance',
    icon: AccountBalanceWalletOutlined,
    children: [
      { key: 'cashAccounts', labelKey: 'nav.cashAccounts', path: '/finance/cash-accounts', phase: 5, access: { adminOnly: true } },
      { key: 'cashTransactions', labelKey: 'nav.cashTransactions', path: '/finance/cash-transactions', phase: 5, access: { adminOnly: true } },
      { key: 'expenses', labelKey: 'nav.expenses', path: '/finance/expenses', phase: 7, access: { anyOf: [P.RECORD_EXPENSES] } },
      { key: 'dailyCashClosing', labelKey: 'nav.dailyCashClosing', path: '/finance/cash-closing', phase: 7, access: { adminOnly: true } },
    ],
  },
  {
    key: 'reports',
    labelKey: 'nav.reports',
    icon: AssessmentOutlined,
    access: { adminOnly: true },
    children: [
      { key: 'salesReports', labelKey: 'nav.salesReports', path: '/reports/sales', phase: 8 },
      { key: 'purchaseReports', labelKey: 'nav.purchaseReports', path: '/reports/purchases', phase: 8 },
      { key: 'inventoryReports', labelKey: 'nav.inventoryReports', path: '/reports/inventory', phase: 8 },
      { key: 'profitLoss', labelKey: 'nav.profitLoss', path: '/reports/profit-loss', phase: 8 },
      { key: 'customerBalances', labelKey: 'nav.customerBalances', path: '/reports/customer-balances', phase: 8 },
      { key: 'supplierBalances', labelKey: 'nav.supplierBalances', path: '/reports/supplier-balances', phase: 8 },
      { key: 'cashReports', labelKey: 'nav.cashReports', path: '/reports/cash', phase: 8 },
    ],
  },
  {
    key: 'userManagement',
    labelKey: 'nav.userManagement',
    icon: ManageAccountsOutlined,
    access: { adminOnly: true },
    children: [
      { key: 'users', labelKey: 'nav.users', path: '/users', phase: 3 },
      { key: 'activityLogs', labelKey: 'nav.activityLogs', path: '/users/activity-logs', phase: 3 },
    ],
  },
  {
    key: 'settings',
    labelKey: 'nav.settings',
    icon: SettingsOutlined,
    children: [
      { key: 'businessSettings', labelKey: 'nav.businessSettings', path: '/settings/business', phase: 3, access: { adminOnly: true } },
      { key: 'currencySettings', labelKey: 'nav.currencySettings', path: '/settings/currency', phase: 5, access: { adminOnly: true } },
      { key: 'invoiceSettings', labelKey: 'nav.invoiceSettings', path: '/settings/invoice', phase: 6, access: { adminOnly: true } },
      { key: 'appearance', labelKey: 'nav.appearance', path: '/settings/appearance' },
    ],
  },
];

/** Items the user can see; a group's own access applies to all its children. Empty groups are dropped. */
export function filterNav(nav, canAccess) {
  return nav
    .filter((item) => canAccess(item.access))
    .map((item) => (item.children ? { ...item, children: item.children.filter((c) => canAccess(c.access)) } : item))
    .filter((item) => !item.children || item.children.length > 0);
}

/** All leaf items with their parent group, for routing. */
export function flattenNav(nav = NAV) {
  return nav.flatMap((item) =>
    item.children ? item.children.map((c) => ({ ...c, group: item, access: c.access ?? item.access })) : [{ ...item, group: null }],
  );
}

/**
 * Finds the page for a URL (used for title + breadcrumbs). Menu items match exactly; extra pages may use
 * route patterns such as /purchases/:id.
 */
export function findNavTrail(pathname, extra = []) {
  const leaf = flattenNav().find((i) => i.path === pathname) ?? extra.find((i) => matchPath({ path: i.path, end: true }, pathname));
  return leaf ? { item: leaf, group: leaf.group } : null;
}
