import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { Alert, Box, Button, Card, Stack, Typography } from '@mui/material';
import { useAuth } from '../../contexts/AuthContext';
import { useNotify } from '../../contexts/NotificationContext';
import { authService } from '../../services/authService';
import { errorMessage } from '../../services/apiClient';
import FormTextField from '../../components/form/FormTextField';
import { msg } from '../../utils/validation';

export const passwordSchema = z
  .object({
    currentPassword: z.string().min(1, msg('validation.required')),
    newPassword: z
      .string()
      .min(8, msg('validation.passwordWeak'))
      .max(100, msg('validation.maxLength', { max: 100 }))
      .regex(/[A-Za-z]/, msg('validation.passwordWeak'))
      .regex(/\d/, msg('validation.passwordWeak')),
    confirmPassword: z.string().min(1, msg('validation.required')),
  })
  .refine((v) => v.newPassword === v.confirmPassword, { path: ['confirmPassword'], message: msg('validation.passwordMismatch') });

export default function ChangePasswordPage() {
  const { t } = useTranslation();
  const { user, reloadUser } = useAuth();
  const notify = useNotify();
  const navigate = useNavigate();
  const [error, setError] = useState(null);
  const { control, handleSubmit, formState } = useForm({
    resolver: zodResolver(passwordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  });

  const onSubmit = handleSubmit(async (values) => {
    setError(null);
    try {
      await authService.changePassword(values.currentPassword, values.newPassword);
      await reloadUser();
      notify.success(t('auth.passwordChanged'));
      navigate('/', { replace: true });
    } catch (e) {
      setError(errorMessage(t, e));
    }
  });

  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 2 }}>
      <Card component="form" onSubmit={onSubmit} noValidate sx={{ width: '100%', maxWidth: 440, p: { xs: 3, sm: 4 } }}>
        <Typography variant="h5" component="h1">
          {t('auth.changePasswordTitle')}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75, mb: 3 }}>
          {t('auth.changePasswordSubtitle')}
        </Typography>
        {user?.mustChangePassword && (
          <Alert severity="warning" sx={{ mb: 2 }}>
            {t('auth.mustChangePassword')}
          </Alert>
        )}
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        <Stack spacing={2}>
          <FormTextField name="currentPassword" control={control} type="password" label={t('auth.currentPassword')} autoComplete="current-password" ltr />
          <FormTextField name="newPassword" control={control} type="password" label={t('auth.newPassword')} autoComplete="new-password" helperText={t('auth.passwordRules')} ltr />
          <FormTextField name="confirmPassword" control={control} type="password" label={t('auth.confirmPassword')} autoComplete="new-password" ltr />
          <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end', pt: 1 }}>
            {!user?.mustChangePassword && (
              <Button color="inherit" onClick={() => navigate(-1)}>
                {t('common.cancel')}
              </Button>
            )}
            <Button type="submit" variant="contained" loading={formState.isSubmitting}>
              {t('common.save')}
            </Button>
          </Stack>
        </Stack>
      </Card>
    </Box>
  );
}
