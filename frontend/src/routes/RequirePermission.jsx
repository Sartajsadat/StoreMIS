import { useAuth } from '../contexts/AuthContext';
import ForbiddenPage from '../modules/system/ForbiddenPage';

/** Client-side guard only; the backend enforces the same rules on every API call. */
export default function RequirePermission({ access, children }) {
  const { canAccess } = useAuth();
  return canAccess(access) ? children : <ForbiddenPage />;
}
