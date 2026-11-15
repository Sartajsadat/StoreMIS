import { Controller } from 'react-hook-form';
import { TextField } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { translateValidation } from '../../utils/validation';

/** TextField bound to react-hook-form; validation messages are i18n keys translated here. */
export default function FormTextField({ name, control, label, helperText, ltr, slotProps, ...props }) {
  const { t } = useTranslation();
  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => (
        <TextField
          {...field}
          value={field.value ?? ''}
          label={label}
          error={Boolean(fieldState.error)}
          helperText={fieldState.error ? translateValidation(t, fieldState.error.message) : helperText}
          slotProps={{ ...slotProps, htmlInput: { ...(ltr && { dir: 'ltr' }), ...slotProps?.htmlInput } }}
          {...props}
        />
      )}
    />
  );
}
