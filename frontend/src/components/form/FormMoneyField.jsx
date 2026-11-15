import { Controller } from 'react-hook-form';
import { InputAdornment, TextField } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { translateValidation } from '../../utils/validation';

// Accepts Latin and Persian/Arabic-Indic digits and normalises them, so users can type with any keyboard layout.
const DIGIT_MAP = { '۰': '0', '۱': '1', '۲': '2', '۳': '3', '۴': '4', '۵': '5', '۶': '6', '۷': '7', '۸': '8', '۹': '9', '٠': '0', '١': '1', '٢': '2', '٣': '3', '٤': '4', '٥': '5', '٦': '6', '٧': '7', '٨': '8', '٩': '9', '٫': '.', '،': '' };
export const normalizeDecimalInput = (raw, maxDecimals) => {
  let s = String(raw ?? '')
    .split('')
    .map((ch) => (ch in DIGIT_MAP ? DIGIT_MAP[ch] : ch))
    .join('')
    .replace(/,/g, '')
    .replace(/[^\d.]/g, '');
  const firstDot = s.indexOf('.');
  if (firstDot !== -1) s = s.slice(0, firstDot + 1) + s.slice(firstDot + 1).replace(/\./g, '');
  if (maxDecimals !== undefined && firstDot !== -1) s = s.slice(0, firstDot + 1 + maxDecimals);
  if (maxDecimals === 0) s = s.replace('.', '');
  return s;
};

/**
 * Decimal input for money or quantities. The value is kept as a string (never a float) and sent to the API as such.
 * `currency` shows the code as an adornment; `decimals` limits the fraction digits (2 for money, 3 for quantities).
 */
export default function FormMoneyField({ name, control, label, currency, decimals = 2, helperText, ...props }) {
  const { t } = useTranslation();
  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => (
        <TextField
          {...field}
          value={field.value ?? ''}
          onChange={(e) => field.onChange(normalizeDecimalInput(e.target.value, decimals))}
          label={label}
          error={Boolean(fieldState.error)}
          helperText={fieldState.error ? translateValidation(t, fieldState.error.message) : helperText}
          slotProps={{
            htmlInput: { inputMode: 'decimal', dir: 'ltr', style: { textAlign: 'end', fontVariantNumeric: 'tabular-nums' } },
            input: currency ? { endAdornment: <InputAdornment position="end">{currency}</InputAdornment> } : undefined,
          }}
          {...props}
        />
      )}
    />
  );
}
