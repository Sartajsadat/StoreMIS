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

const UiKitPage = lazy(() => import('../modules/dev/UiKitPage'));

// Screens that are implemented. Every other menu entry shows a "coming in Phase N" page until its phase ships.
const PAGES = {
  appearance: AppearancePage,
  businessSettings: BusinessSettingsPage,
  users: UsersPage,
  activityLogs: ActivityLogsPage,
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
            {import.meta.env.DEV && <Route path="/dev/ui-kit" element={<UiKitPage />} />}
            <Route path="/dashboard" element={<Navigate to="/" replace />} />
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Route>
      </Routes>
    </Suspense>
  );
}
