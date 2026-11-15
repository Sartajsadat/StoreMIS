import { Controller } from 'react-hook-form';
import { MenuItem, TextField } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { translateValidation } from '../../utils/validation';

/** options: [{ value, label }] with already-translated labels. */
export default function FormSelect({ name, control, label, options = [], helperText, emptyOption, ...props }) {
  const { t } = useTranslation();
  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => (
        <TextField
          select
          {...field}
          value={field.value ?? ''}
          label={label}
          error={Boolean(fieldState.error)}
          helperText={fieldState.error ? translateValidation(t, fieldState.error.message) : helperText}
          {...props}
        >
          {emptyOption && (
            <MenuItem value="">
              <em>{t('common.selectPlaceholder')}</em>
            </MenuItem>
          )}
          {options.map((o) => (
            <MenuItem key={o.value} value={o.value}>
              {o.label}
            </MenuItem>
          ))}
        </TextField>
      )}
    />
  );
}
