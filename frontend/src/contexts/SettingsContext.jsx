import { createContext, useContext, useEffect, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { settingsService } from '../services/settingsService';
import { usePreferences } from './PreferencesContext';

const SettingsContext = createContext(null);

const FALLBACK = { businessName: null, logoUrl: null, defaultLanguage: 'en', defaultTheme: 'light', currencies: ['AFN', 'USD'], baseCurrency: 'AFN' };

/** Public business settings (name, logo, defaults). The app keeps working with fallbacks if the server is unreachable. */
export function SettingsProvider({ children }) {
  const { applyBusinessDefaults } = usePreferences();
  const query = useQuery({
    queryKey: ['settings', 'public'],
    queryFn: settingsService.getPublic,
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });

  useEffect(() => {
    if (query.data) applyBusinessDefaults(query.data);
  }, [query.data, applyBusinessDefaults]);

  const value = useMemo(
    () => ({ settings: { ...FALLBACK, ...(query.data ?? {}) }, isLoading: query.isLoading, refetch: query.refetch }),
    [query.data, query.isLoading, query.refetch],
  );
  return <SettingsContext.Provider value={value}>{children}</SettingsContext.Provider>;
}

export function useSettings() {
  const ctx = useContext(SettingsContext);
  if (!ctx) throw new Error('useSettings must be used inside SettingsProvider');
  return ctx;
}
