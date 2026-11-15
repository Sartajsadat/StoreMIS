import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import i18n, { DEFAULT_LANGUAGE, directionOf, isSupportedLanguage, LANGUAGES } from '../i18n';
import { storage } from '../utils/storage';

const PreferencesContext = createContext(null);

const KEYS = { language: 'storemis.language', theme: 'storemis.theme', sidebar: 'storemis.sidebarCollapsed', pending: 'storemis.prefsPending' };
const THEMES = ['light', 'dark'];

const initialTheme = () => {
  const stored = storage.get(KEYS.theme);
  if (THEMES.includes(stored)) return stored;
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
};

/**
 * Language, colour theme and sidebar state for the current browser.
 * Values are kept in localStorage for an instant first paint, and synced with the user's
 * server-side preferences once signed in (see PreferencesSync).
 * `userRevision` increases only on changes made by the user, so server-applied values are never echoed back.
 *
 * A local change is marked "pending" (for the signed-in user, or "anon" on the login page) until the server
 * confirms it. While pending, the local value is newer than the server copy and wins when the session is restored.
 */
export function PreferencesProvider({ children }) {
  const [language, setLanguageState] = useState(() => {
    const stored = storage.get(KEYS.language);
    return isSupportedLanguage(stored) ? stored : DEFAULT_LANGUAGE;
  });
  const [hasStoredLanguage] = useState(() => isSupportedLanguage(storage.get(KEYS.language)));
  const [themeMode, setThemeState] = useState(initialTheme);
  const [sidebarCollapsed, setSidebarState] = useState(() => storage.get(KEYS.sidebar) === 'true');
  const [userRevision, setUserRevision] = useState(0);

  const direction = directionOf(language);

  useEffect(() => {
    if (i18n.language !== language) i18n.changeLanguage(language);
    const meta = LANGUAGES.find((l) => l.code === language);
    document.documentElement.setAttribute('lang', meta?.htmlLang ?? language);
    document.documentElement.setAttribute('dir', direction);
    storage.set(KEYS.language, language);
  }, [language, direction]);

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', themeMode);
    storage.set(KEYS.theme, themeMode);
  }, [themeMode]);

  useEffect(() => storage.set(KEYS.sidebar, String(sidebarCollapsed)), [sidebarCollapsed]);

  const ownerRef = useRef(null);

  const bump = () => {
    storage.set(KEYS.pending, ownerRef.current ? String(ownerRef.current) : 'anon');
    setUserRevision((r) => r + 1);
  };

  /** Called by AuthContext so pending changes are attributed to the right account. */
  const setOwner = useCallback((userId) => {
    ownerRef.current = userId ?? null;
  }, []);

  const hasPendingSync = useCallback(() => Boolean(storage.get(KEYS.pending)), []);
  const markSynced = useCallback(() => storage.remove(KEYS.pending), []);

  const setLanguage = useCallback((code) => {
    if (!isSupportedLanguage(code)) return;
    setLanguageState(code);
    bump();
  }, []);
  const setThemeMode = useCallback((mode) => {
    if (!THEMES.includes(mode)) return;
    setThemeState(mode);
    bump();
  }, []);
  const toggleTheme = useCallback(() => {
    setThemeState((m) => (m === 'light' ? 'dark' : 'light'));
    bump();
  }, []);
  const setSidebarCollapsed = useCallback((value) => {
    setSidebarState((prev) => (typeof value === 'function' ? value(prev) : value));
    bump();
  }, []);

  /**
   * Applies preferences from the server after sign-in or session restore — unless this browser holds a newer,
   * not-yet-saved choice for the same user (or one made on the login page), which is then pushed to the server instead.
   */
  const applyServerPreferences = useCallback((prefs, userId) => {
    const pending = storage.get(KEYS.pending);
    if (pending && (pending === 'anon' || pending === String(userId))) {
      storage.set(KEYS.pending, String(userId));
      setUserRevision((r) => r + 1);
      return;
    }
    storage.remove(KEYS.pending);
    if (!prefs) return;
    if (isSupportedLanguage(prefs.language)) setLanguageState(prefs.language);
    if (THEMES.includes(prefs.theme)) setThemeState(prefs.theme);
    if (typeof prefs.sidebarCollapsed === 'boolean') setSidebarState(prefs.sidebarCollapsed);
  }, []);

  /** Business defaults apply only on a browser that has never chosen a language. */
  const applyBusinessDefaults = useCallback(
    (settings) => {
      if (!hasStoredLanguage && isSupportedLanguage(settings?.defaultLanguage)) setLanguageState(settings.defaultLanguage);
    },
    [hasStoredLanguage],
  );

  const value = useMemo(
    () => ({
      language,
      direction,
      isRtl: direction === 'rtl',
      themeMode,
      sidebarCollapsed,
      userRevision,
      setLanguage,
      setThemeMode,
      toggleTheme,
      setSidebarCollapsed,
      applyServerPreferences,
      applyBusinessDefaults,
      setOwner,
      hasPendingSync,
      markSynced,
    }),
    [language, direction, themeMode, sidebarCollapsed, userRevision, setLanguage, setThemeMode, toggleTheme, setSidebarCollapsed, applyServerPreferences, applyBusinessDefaults, setOwner, hasPendingSync, markSynced],
  );

  return <PreferencesContext.Provider value={value}>{children}</PreferencesContext.Provider>;
}

export function usePreferences() {
  const ctx = useContext(PreferencesContext);
  if (!ctx) throw new Error('usePreferences must be used inside PreferencesProvider');
  return ctx;
}
