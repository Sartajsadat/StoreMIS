import { useEffect, useRef } from 'react';
import { useAuth } from './AuthContext';
import { usePreferences } from './PreferencesContext';
import { preferenceService } from '../services/preferenceService';
import { tokenStore } from '../services/tokenStore';
import { API_BASE } from '../services/apiClient';

/**
 * Saves language / theme / sidebar changes to the signed-in user's account.
 * Changes are sent after a short pause, and any unsent change is flushed when the page is closed or reloaded.
 */
export function PreferencesSync() {
  const { isAuthenticated } = useAuth();
  const { language, themeMode, sidebarCollapsed, userRevision, hasPendingSync, markSynced } = usePreferences();
  const lastSent = useRef(0);
  const latest = useRef(null);
  latest.current = { language, theme: themeMode, sidebarCollapsed };

  useEffect(() => {
    if (!isAuthenticated || userRevision === 0 || userRevision === lastSent.current || !hasPendingSync()) return undefined;
    const revision = userRevision;
    const timer = setTimeout(() => {
      preferenceService
        .update(latest.current)
        .then(() => {
          lastSent.current = revision;
          markSynced();
        })
        .catch(() => {
          /* kept as pending locally; retried on the next change or sign-in */
        });
    }, 250);
    return () => clearTimeout(timer);
  }, [isAuthenticated, userRevision, language, themeMode, sidebarCollapsed, hasPendingSync, markSynced]);

  useEffect(() => {
    if (!isAuthenticated) return undefined;
    const flush = () => {
      const token = tokenStore.get();
      if (!hasPendingSync() || !token) return;
      // keepalive lets the request finish after the page has gone away.
      fetch(`${API_BASE}/me/preferences`, {
        method: 'PUT',
        keepalive: true,
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify(latest.current),
      }).catch(() => {});
    };
    window.addEventListener('pagehide', flush);
    return () => window.removeEventListener('pagehide', flush);
  }, [isAuthenticated, hasPendingSync]);

  return null;
}
