import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { Alert, Stack, Typography } from '@mui/material';
import FormDialog from '../../components/form/FormDialog';
import FormTextField from '../../components/form/FormTextField';
import { msg } from '../../utils/validation';
import { errorMessage } from '../../services/apiClient';

const schema = z
  .object({
    newPassword: z.string().min(8, msg('validation.passwordWeak')).regex(/[A-Za-z]/, msg('validation.passwordWeak')).regex(/\d/, msg('validation.passwordWeak')),
    confirmPassword: z.string().min(1, msg('validation.required')),
  })
  .refine((v) => v.newPassword === v.confirmPassword, { path: ['confirmPassword'], message: msg('validation.passwordMismatch') });

export default function ResetPasswordDialog({ open, user, onClose, onSubmit }) {
  const { t } = useTranslation();
  const [error, setError] = useState(null);
  const { control, handleSubmit, reset, formState } = useForm({ resolver: zodResolver(schema), defaultValues: { newPassword: '', confirmPassword: '' } });

  useEffect(() => {
    if (open) {
      reset({ newPassword: '', confirmPassword: '' });
      setError(null);
    }
  }, [open, reset]);

  const submit = handleSubmit(async ({ newPassword }) => {
    try {
      await onSubmit(newPassword);
    } catch (e) {
      setError(errorMessage(t, e));
    }
  });

  return (
    <FormDialog open={open} title={t('users.resetTitle', { name: user?.fullName ?? '' })} onClose={onClose} onSubmit={submit} submitting={formState.isSubmitting} submitLabel={t('users.resetPassword')}>
      <Stack spacing={2} sx={{ pt: 1 }}>
        <Typography variant="body2" color="text.secondary">
          {t('users.resetHint')}
        </Typography>
        {error && <Alert severity="error">{error}</Alert>}
        <FormTextField name="newPassword" control={control} label={t('auth.newPassword')} helperText={t('auth.passwordRules')} autoComplete="new-password" ltr />
        <FormTextField name="confirmPassword" control={control} label={t('auth.confirmPassword')} autoComplete="new-password" ltr />
      </Stack>
    </FormDialog>
  );
}
