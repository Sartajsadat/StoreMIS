import { useEffect, useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { Alert, Box, Button, Grid, Stack, TextField, Typography } from '@mui/material';
import UploadOutlined from '@mui/icons-material/UploadOutlined';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import PageHeader from '../../components/display/PageHeader';
import SectionCard from '../../components/display/SectionCard';
import BrandMark from '../../components/display/BrandMark';
import PageLoader from '../../components/feedback/PageLoader';
import FormTextField from '../../components/form/FormTextField';
import FormSelect from '../../components/form/FormSelect';
import FormSwitch from '../../components/form/FormSwitch';
import { settingsService } from '../../services/settingsService';
import { errorMessage } from '../../services/apiClient';
import { useNotify } from '../../contexts/NotificationContext';
import { LANGUAGES } from '../../i18n';
import { msg } from '../../utils/validation';
import { applyServerFieldErrors } from '../../utils/serverErrors';

const schema = z.object({
  businessName: z.string().trim().min(1, msg('validation.required')).max(150, msg('validation.maxLength', { max: 150 })),
  address: z.string().max(300, msg('validation.maxLength', { max: 300 })).nullable().optional(),
  phone: z.string().max(50, msg('validation.maxLength', { max: 50 })).nullable().optional(),
  email: z.union([z.literal(''), z.string().email(msg('validation.email'))]).nullable().optional(),
  defaultCurrency: z.string().length(3),
  defaultLanguage: z.enum(['en', 'fa', 'ps']),
  defaultTheme: z.enum(['light', 'dark']),
  calendarDisplay: z.enum(['GREGORIAN', 'SOLAR_HIJRI']),
  digitStyle: z.enum(['LATIN', 'PERSIAN']),
  allowNegativeStock: z.boolean(),
  allowNegativeCash: z.boolean(),
  blockSaleBelowCost: z.boolean(),
});

export default function BusinessSettingsPage() {
  const { t } = useTranslation();
  const notify = useNotify();
  const queryClient = useQueryClient();
  const fileInput = useRef(null);
  const [error, setError] = useState(null);
  const [logoBusy, setLogoBusy] = useState(false);
  const settings = useQuery({ queryKey: ['settings', 'business'], queryFn: settingsService.getBusiness });
  const currencies = useQuery({ queryKey: ['currencies'], queryFn: settingsService.currencies, staleTime: Infinity });
  const { control, handleSubmit, reset, setError: setFieldError, formState } = useForm({ resolver: zodResolver(schema) });

  useEffect(() => {
    if (settings.data) reset({ ...settings.data, address: settings.data.address ?? '', phone: settings.data.phone ?? '', email: settings.data.email ?? '' });
  }, [settings.data, reset]);

  if (settings.isLoading || !settings.data) return <PageLoader />;
  const data = settings.data;

  const afterChange = (updated) => {
    queryClient.setQueryData(['settings', 'business'], updated);
    queryClient.invalidateQueries({ queryKey: ['settings', 'public'] });
  };

  const onSubmit = handleSubmit(async (values) => {
    setError(null);
    try {
      const updated = await settingsService.updateBusiness({ ...data, ...values, version: data.version });
      afterChange(updated);
      notify.success(t('business.saved'));
    } catch (e) {
      if (!applyServerFieldErrors(e, setFieldError)) setError(errorMessage(t, e));
    }
  });

  const onLogo = async (file) => {
    if (!file) return;
    setLogoBusy(true);
    try {
      afterChange(await settingsService.uploadLogo(file));
      notify.success(t('business.logoUploaded'));
    } catch (e) {
      notify.error(errorMessage(t, e));
    } finally {
      setLogoBusy(false);
      if (fileInput.current) fileInput.current.value = '';
    }
  };

  const onRemoveLogo = async () => {
    setLogoBusy(true);
    try {
      afterChange(await settingsService.removeLogo());
      notify.success(t('business.logoRemoved'));
    } catch (e) {
      notify.error(errorMessage(t, e));
    } finally {
      setLogoBusy(false);
    }
  };

  return (
    <Box component="form" onSubmit={onSubmit} noValidate>
      <PageHeader
        title={t('business.title')}
        subtitle={t('business.subtitle')}
        actions={
          <Button type="submit" variant="contained" loading={formState.isSubmitting} disabled={!formState.isDirty}>
            {t('common.save')}
          </Button>
        }
      />
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}
      <Grid container spacing={3}>
        <Grid size={{ xs: 12, lg: 8 }}>
          <SectionCard title={t('business.info')}>
            <Grid container spacing={2}>
              <Grid size={12}>
                <FormTextField name="businessName" control={control} label={t('business.name')} />
              </Grid>
              <Grid size={12}>
                <FormTextField name="address" control={control} label={t('business.address')} multiline minRows={2} />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <FormTextField name="phone" control={control} label={t('business.phone')} ltr inputMode="tel" />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <FormTextField name="email" control={control} label={t('business.email')} ltr type="email" helperText={t('common.optional')} />
              </Grid>
            </Grid>
          </SectionCard>
        </Grid>
        <Grid size={{ xs: 12, lg: 4 }}>
          <SectionCard title={t('business.logo')}>
            <Stack spacing={2} sx={{ alignItems: 'center', textAlign: 'center' }}>
              <BrandMark logoUrl={data.logoUrl} size={96} alt={data.businessName} />
              <Typography variant="caption" color="text.secondary">
                {t('business.logoHint')}
              </Typography>
              <Stack direction="row" spacing={1}>
                <Button variant="outlined" startIcon={<UploadOutlined />} onClick={() => fileInput.current?.click()} loading={logoBusy}>
                  {t('business.uploadLogo')}
                </Button>
                {data.logoUrl && (
                  <Button color="error" startIcon={<DeleteOutline />} onClick={onRemoveLogo} disabled={logoBusy}>
                    {t('business.removeLogo')}
                  </Button>
                )}
              </Stack>
              <input ref={fileInput} type="file" accept="image/png,image/jpeg,image/webp" hidden onChange={(e) => onLogo(e.target.files?.[0])} />
            </Stack>
          </SectionCard>
        </Grid>
        <Grid size={{ xs: 12, lg: 6 }}>
          <SectionCard title={t('business.regional')}>
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, sm: 6 }}>
                <FormSelect
                  name="defaultLanguage"
                  control={control}
                  label={t('business.defaultLanguage')}
                  helperText={t('business.defaultLanguageHint')}
                  options={LANGUAGES.map((l) => ({ value: l.code, label: t(l.labelKey) }))}
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <FormSelect
                  name="defaultTheme"
                  control={control}
                  label={t('business.defaultTheme')}
                  options={[
                    { value: 'light', label: t('appearance.light') },
                    { value: 'dark', label: t('appearance.dark') },
                  ]}
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <FormSelect
                  name="calendarDisplay"
                  control={control}
                  label={t('business.calendar')}
                  options={['GREGORIAN', 'SOLAR_HIJRI'].map((v) => ({ value: v, label: t(`business.${v}`) }))}
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <FormSelect name="digitStyle" control={control} label={t('business.digits')} options={['LATIN', 'PERSIAN'].map((v) => ({ value: v, label: t(`business.${v}`) }))} />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <FormSelect
                  name="defaultCurrency"
                  control={control}
                  label={t('business.defaultCurrency')}
                  options={(currencies.data ?? []).map((c) => ({ value: c.code, label: `${c.code} — ${t(`currencies.${c.code}`, c.name)}` }))}
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField label={t('business.baseCurrency')} value={data.baseCurrency} disabled helperText={t('business.baseCurrencyHint')} />
              </Grid>
            </Grid>
          </SectionCard>
        </Grid>
        <Grid size={{ xs: 12, lg: 6 }}>
          <SectionCard title={t('business.rules')} subheader={t('business.rulesHint')}>
            <Stack>
              <FormSwitch name="allowNegativeStock" control={control} label={t('business.allowNegativeStock')} />
              <FormSwitch name="allowNegativeCash" control={control} label={t('business.allowNegativeCash')} />
              <FormSwitch name="blockSaleBelowCost" control={control} label={t('business.blockSaleBelowCost')} />
            </Stack>
          </SectionCard>
        </Grid>
      </Grid>
    </Box>
  );
}
