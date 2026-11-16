import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import AppLayout from '../layouts/AppLayout';
import AuthLayout from '../layouts/AuthLayout';
import ProtectedRoute from './ProtectedRoute';
import RequirePermission from './RequirePermission';
import { flattenNav } from '../layouts/navConfig';
import PageLoader from '../components/feedback/PageLoader';
import LoginPage from '../modules/auth/LoginPage';
import ChangePasswordPage from '../modules/auth/ChangePasswordPage';
import ComingSoonPage from '../modules/system/ComingSoonPage';
import NotFoundPage from '../modules/system/NotFoundPage';
import AppearancePage from '../modules/settings/AppearancePage';
import BusinessSettingsPage from '../modules/settings/BusinessSettingsPage';
import UsersPage from '../modules/users/UsersPage';
import ActivityLogsPage from '../modules/audit/ActivityLogsPage';
import ProductListPage from '../modules/products/ProductListPage';
import CategoriesPage from '../modules/products/CategoriesPage';
import UnitsPage from '../modules/products/UnitsPage';
import StockOverviewPage from '../modules/inventory/StockOverviewPage';
import StockMovementsPage from '../modules/inventory/StockMovementsPage';
import StockAdjustmentsPage from '../modules/inventory/StockAdjustmentsPage';
import LowStockPage from '../modules/inventory/LowStockPage';
import SuppliersPage from '../modules/suppliers/SuppliersPage';
import SupplierDetailPage from '../modules/suppliers/SupplierDetailPage';
import SupplierPaymentsPage from '../modules/suppliers/SupplierPaymentsPage';
import PurchaseFormPage from '../modules/purchases/PurchaseFormPage';
import PurchaseHistoryPage from '../modules/purchases/PurchaseHistoryPage';
import PurchaseDetailPage from '../modules/purchases/PurchaseDetailPage';
import CashAccountsPage from '../modules/finance/CashAccountsPage';
import CashTransactionsPage from '../modules/finance/CashTransactionsPage';
import CurrencySettingsPage from '../modules/settings/CurrencySettingsPage';
import CustomersPage from '../modules/customers/CustomersPage';
import CustomerDetailPage from '../modules/customers/CustomerDetailPage';
import CustomerPaymentsPage from '../modules/customers/CustomerPaymentsPage';
import PosPage from '../modules/sales/PosPage';
import SalesHistoryPage from '../modules/sales/SalesHistoryPage';
import SaleDetailPage from '../modules/sales/SaleDetailPage';
import ReceiptPage from '../modules/print/ReceiptPage';
import InvoiceSettingsPage from '../modules/settings/InvoiceSettingsPage';
import ExpensesPage from '../modules/finance/ExpensesPage';
import CashClosingPage from '../modules/finance/CashClosingPage';
import { PurchaseReturnsPage, SalesReturnsPage } from '../modules/returns/ReturnsPage';
import { EXTRA_PAGES } from './extraPages';

const UiKitPage = lazy(() => import('../modules/dev/UiKitPage'));

// Screens that are implemented. Every other menu entry shows a "coming in Phase N" page until its phase ships.
const PAGES = {
  appearance: AppearancePage,
  businessSettings: BusinessSettingsPage,
  users: UsersPage,
  activityLogs: ActivityLogsPage,
  productList: ProductListPage,
  categories: CategoriesPage,
  units: UnitsPage,
  stockOverview: StockOverviewPage,
  stockMovements: StockMovementsPage,
  stockAdjustments: StockAdjustmentsPage,
  lowStock: LowStockPage,
  supplierList: SuppliersPage,
  supplierPayments: SupplierPaymentsPage,
  newPurchase: PurchaseFormPage,
  purchaseHistory: PurchaseHistoryPage,
  cashAccounts: CashAccountsPage,
  cashTransactions: CashTransactionsPage,
  currencySettings: CurrencySettingsPage,
  supplierDetail: SupplierDetailPage,
  purchaseDetail: PurchaseDetailPage,
  purchaseEdit: PurchaseFormPage,
  customerList: CustomersPage,
  customerDetail: CustomerDetailPage,
  customerPayments: CustomerPaymentsPage,
  newSale: PosPage,
  salesHistory: SalesHistoryPage,
  saleDetail: SaleDetailPage,
  invoiceSettings: InvoiceSettingsPage,
  salesReturns: SalesReturnsPage,
  purchaseReturns: PurchaseReturnsPage,
  expenses: ExpensesPage,
  dailyCashClosing: CashClosingPage,
};

export default function AppRoutes() {
  const leaves = flattenNav();
  return (
    <Suspense fallback={<PageLoader />}>
      <Routes>
        <Route element={<AuthLayout />}>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<ProtectedRoute allowPasswordChange />}>
            <Route path="/change-password" element={<ChangePasswordPage />} />
          </Route>
        </Route>

        <Route element={<ProtectedRoute />}>
          <Route path="/print/sales/:id" element={<ReceiptPage />} />
          <Route element={<AppLayout />}>
            {leaves.map((item) => {
              const Page = PAGES[item.key];
              return (
                <Route
                  key={item.key}
                  path={item.path}
                  element={<RequirePermission access={item.access}>{Page ? <Page /> : <ComingSoonPage item={item} />}</RequirePermission>}
                />
              );
            })}
            {EXTRA_PAGES.filter((p) => PAGES[p.key]).map((p) => {
              const Page = PAGES[p.key];
              return <Route key={p.key} path={p.path} element={<RequirePermission access={p.access}><Page key={p.key} /></RequirePermission>} />;
            })}
            {import.meta.env.DEV && <Route path="/dev/ui-kit" element={<UiKitPage />} />}
            <Route path="/dashboard" element={<Navigate to="/" replace />} />
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Route>
      </Routes>
    </Suspense>
  );
}
