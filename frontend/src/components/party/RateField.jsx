import { InputAdornment, TextField } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { normalizeDecimalInput } from '../form/FormMoneyField';
import { useSettings } from '../../contexts/SettingsContext';

/** Exchange-rate input ("1 USD = ? AFN"). Hidden for the base currency, whose rate is always 1. */
export default function RateField({ currency, value, onChange, label, helperText, error }) {
  const { t } = useTranslation();
  const { settings } = useSettings();
  if (!currency || currency === settings.baseCurrency) return null;
  return (
    <TextField
      label={label ?? t('purchases.exchangeRate')}
      value={value ?? ''}
      onChange={(e) => onChange(normalizeDecimalInput(e.target.value, 6))}
      error={error}
      helperText={helperText ?? t('purchases.rateHint', { currency, base: settings.baseCurrency })}
      slotProps={{
        htmlInput: { inputMode: 'decimal', dir: 'ltr', style: { textAlign: 'end' } },
        input: { startAdornment: <InputAdornment position="start">{`1 ${currency} =`}</InputAdornment>, endAdornment: <InputAdornment position="end">{settings.baseCurrency}</InputAdornment> },
      }}
    />
  );
}
