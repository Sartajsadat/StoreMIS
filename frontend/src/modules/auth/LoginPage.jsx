import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { Alert, Box, Button, IconButton, InputAdornment, Stack, Typography } from '@mui/material';
import VisibilityOutlined from '@mui/icons-material/VisibilityOutlined';
import VisibilityOffOutlined from '@mui/icons-material/VisibilityOffOutlined';
import PointOfSaleOutlined from '@mui/icons-material/PointOfSaleOutlined';
import Inventory2Outlined from '@mui/icons-material/Inventory2Outlined';
import AccountBalanceWalletOutlined from '@mui/icons-material/AccountBalanceWalletOutlined';
import WifiOffOutlined from '@mui/icons-material/WifiOffOutlined';
import { useAuth } from '../../contexts/AuthContext';
import { useSettings } from '../../contexts/SettingsContext';
import { errorMessage } from '../../services/apiClient';
import FormTextField from '../../components/form/FormTextField';
import BrandMark from '../../components/display/BrandMark';
import { msg } from '../../utils/validation';

const schema = z.object({
  username: z.string().trim().min(1, msg('validation.required')),
  password: z.string().min(1, msg('validation.required')),
});

const FEATURES = [
  { icon: PointOfSaleOutlined, key: 'nav.sales' },
  { icon: Inventory2Outlined, key: 'nav.inventory' },
  { icon: AccountBalanceWalletOutlined, key: 'nav.finance' },
];

export default function LoginPage() {
  const { t } = useTranslation();
  const { login, status } = useAuth();
  const { settings } = useSettings();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState(null);
  const [showPassword, setShowPassword] = useState(false);
  const { control, handleSubmit, formState } = useForm({ resolver: zodResolver(schema), defaultValues: { username: '', password: '' } });

  if (status === 'authenticated') return <Navigate to={location.state?.from ?? '/'} replace />;

  const onSubmit = handleSubmit(async ({ username, password }) => {
    setError(null);
    try {
      const user = await login(username.trim(), password);
      navigate(user.mustChangePassword ? '/change-password' : location.state?.from ?? '/', { replace: true });
    } catch (e) {
      setError(errorMessage(t, e));
    }
  });

  const name = settings.businessName || t('app.name');

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <Box
        sx={{
          display: { xs: 'none', md: 'flex' },
          flex: '0 0 44%',
          flexDirection: 'column',
          justifyContent: 'space-between',
          p: 6,
          color: '#fff',
          background: 'linear-gradient(160deg, #172B4D 0%, #1E3A8A 60%, #2563EB 100%)',
        }}
      >
        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
          <BrandMark logoUrl={settings.logoUrl} alt={name} size={44} />
          <Typography variant="h6" sx={{ fontWeight: 700 }}>
            {name}
          </Typography>
        </Stack>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 700, lineHeight: 1.3, maxWidth: 440 }}>
            {t('app.fullName')}
          </Typography>
          <Typography sx={{ mt: 1.5, opacity: 0.8 }}>{t('app.tagline')}</Typography>
          <Stack spacing={1.5} sx={{ mt: 4 }}>
            {FEATURES.map(({ icon: Icon, key }) => (
              <Stack key={key} direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
                <Box sx={{ width: 36, height: 36, borderRadius: 2, display: 'grid', placeItems: 'center', bgcolor: 'rgba(255,255,255,0.12)' }}>
                  <Icon fontSize="small" />
                </Box>
                <Typography>{t(key)}</Typography>
              </Stack>
            ))}
          </Stack>
        </Box>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', opacity: 0.75 }}>
          <WifiOffOutlined fontSize="small" />
          <Typography variant="body2">{t('auth.localNetwork')}</Typography>
        </Stack>
      </Box>

      <Box sx={{ flex: 1, display: 'grid', placeItems: 'center', p: { xs: 2, sm: 4 } }}>
        <Box component="form" onSubmit={onSubmit} noValidate sx={{ width: '100%', maxWidth: 400 }}>
          <Box sx={{ display: { xs: 'flex', md: 'none' }, alignItems: 'center', gap: 1.5, mb: 4 }}>
            <BrandMark logoUrl={settings.logoUrl} alt={name} />
            <Typography variant="h6" sx={{ fontWeight: 700 }}>
              {name}
            </Typography>
          </Box>
          <Typography variant="h5" component="h1">
            {t('auth.signInTitle')}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75, mb: 3 }}>
            {t('auth.signInSubtitle')}
          </Typography>

          {location.state?.loggedOut && !error && (
            <Alert severity="info" sx={{ mb: 2 }}>
              {t('auth.loggedOut')}
            </Alert>
          )}
          {error && (
            <Alert severity="error" sx={{ mb: 2 }} role="alert">
              {error}
            </Alert>
          )}

          <Stack spacing={2}>
            <FormTextField name="username" control={control} label={t('auth.username')} autoComplete="username" autoFocus ltr size="medium" />
            <FormTextField
              name="password"
              control={control}
              label={t('auth.password')}
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              size="medium"
              ltr
              slotProps={{
                input: {
                  endAdornment: (
                    <InputAdornment position="end">
                      <IconButton
                        onClick={() => setShowPassword((v) => !v)}
                        edge="end"
                        aria-label={showPassword ? t('auth.hidePassword') : t('auth.showPassword')}
                      >
                        {showPassword ? <VisibilityOffOutlined /> : <VisibilityOutlined />}
                      </IconButton>
                    </InputAdornment>
                  ),
                },
              }}
            />
            <Button type="submit" variant="contained" size="large" loading={formState.isSubmitting} fullWidth>
              {formState.isSubmitting ? t('auth.signingIn') : t('auth.signIn')}
            </Button>
          </Stack>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 3, textAlign: 'center' }}>
            {t('auth.noPublicSignup')}
          </Typography>
        </Box>
      </Box>
    </Box>
  );
}
