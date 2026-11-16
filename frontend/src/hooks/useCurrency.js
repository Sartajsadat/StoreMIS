import { useQuery } from '@tanstack/react-query';
import { currencyService } from '../services/currencyService';
import { cashService } from '../services/cashService';
import { useSettings } from '../contexts/SettingsContext';

/** Current exchange rates: { baseCurrency, rates: { USD: "70.000000" } }. */
export function useCurrentRates() {
  return useQuery({ queryKey: ['exchange-rates', 'current'], queryFn: currencyService.currentRates, staleTime: 60 * 1000 });
}

/** Active currencies from settings, base first. */
export function useCurrencies() {
  const { settings } = useSettings();
  const codes = (settings.currencies ?? []).map((c) => (typeof c === 'string' ? c : c.code));
  return [settings.baseCurrency, ...codes.filter((c) => c !== settings.baseCurrency)].filter(Boolean);
}

export function useCashAccounts() {
  return useQuery({ queryKey: ['cash-accounts'], queryFn: cashService.accounts, staleTime: 30 * 1000 });
}
