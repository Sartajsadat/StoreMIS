import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import PageLoader from '../components/feedback/PageLoader';

/** Requires a signed-in user. Users with a temporary password are sent to change it first. */
export default function ProtectedRoute({ allowPasswordChange = false }) {
  const { status, user } = useAuth();
  const location = useLocation();
  if (status === 'loading') return <PageLoader fullScreen />;
  if (status !== 'authenticated') return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (user?.mustChangePassword && !allowPasswordChange) return <Navigate to="/change-password" replace />;
  return <Outlet />;
}
