import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { authService } from '../services/authService';
import { tokenStore } from '../services/tokenStore';
import { canAccess, hasPermission, isAdmin } from '../utils/permissions';
import { usePreferences } from './PreferencesContext';
import { useNotify } from './NotificationContext';

const AuthContext = createContext(null);

/**
 * status: 'loading' (restoring session) | 'authenticated' | 'anonymous'
 * user:   { id, username, fullName, role, permissions[], mustChangePassword, preferences }
 */
export function AuthProvider({ children }) {
  const [status, setStatus] = useState('loading');
  const [user, setUser] = useState(null);
  const { applyServerPreferences } = usePreferences();
  const queryClient = useQueryClient();
  const notify = useNotify();
  const { t } = useTranslation();

  const signIn = useCallback(
    (u) => {
      setUser(u);
      setStatus(u ? 'authenticated' : 'anonymous');
      if (u?.preferences) applyServerPreferences(u.preferences);
    },
    [applyServerPreferences],
  );

  useEffect(() => {
    let cancelled = false;
    authService.restore().then((u) => {
      if (!cancelled) signIn(u);
    });
    return () => {
      cancelled = true;
    };
  }, [signIn]);

  useEffect(
    () =>
      tokenStore.onSessionExpired(() => {
        setUser(null);
        setStatus('anonymous');
        queryClient.clear();
        notify.warning(t('auth.sessionExpired'));
      }),
    [queryClient, notify, t],
  );

  const login = useCallback(
    async (username, password) => {
      const data = await authService.login(username, password);
      signIn(data.user);
      return data.user;
    },
    [signIn],
  );

  const logout = useCallback(async () => {
    try {
      await authService.logout();
    } finally {
      setUser(null);
      setStatus('anonymous');
      queryClient.clear();
    }
  }, [queryClient]);

  const reloadUser = useCallback(async () => {
    const u = await authService.me();
    setUser(u);
    return u;
  }, []);

  const value = useMemo(
    () => ({
      status,
      user,
      isAuthenticated: status === 'authenticated',
      isAdmin: isAdmin(user),
      login,
      logout,
      reloadUser,
      hasPermission: (code) => hasPermission(user, code),
      canAccess: (access) => canAccess(user, access),
    }),
    [status, user, login, logout, reloadUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
