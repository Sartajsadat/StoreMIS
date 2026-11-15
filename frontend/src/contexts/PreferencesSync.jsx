import { useEffect, useRef } from 'react';
import { useAuth } from './AuthContext';
import { usePreferences } from './PreferencesContext';
import { preferenceService } from '../services/preferenceService';

/** Saves language / theme / sidebar changes to the signed-in user's account (debounced). */
export function PreferencesSync() {
  const { isAuthenticated } = useAuth();
  const { language, themeMode, sidebarCollapsed, userRevision } = usePreferences();
  const lastSent = useRef(0);

  useEffect(() => {
    if (!isAuthenticated || userRevision === 0 || userRevision === lastSent.current) return undefined;
    const timer = setTimeout(() => {
      lastSent.current = userRevision;
      preferenceService.update({ language, theme: themeMode, sidebarCollapsed }).catch(() => {
        /* preferences are still kept locally; the next change retries */
      });
    }, 600);
    return () => clearTimeout(timer);
  }, [isAuthenticated, userRevision, language, themeMode, sidebarCollapsed]);

  return null;
}
