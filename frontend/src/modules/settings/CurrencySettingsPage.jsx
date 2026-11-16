import { useMemo, useState } from 'react';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Alert, Box, Button, Card, CardContent, CardHeader, Grid, Stack, TextField, Typography } from '@mui/material';
import PageHeader from '../../components/display/PageHeader';
import DataTable from '../../components/data/DataTable';
import UserText from '../../components/display/UserText';
import Ltr from '../../components/display/Ltr';
import RateField from '../../components/party/RateField';
import { currencyService } from '../../services/currencyService';
import { errorMessage } from '../../services/apiClient';
import { useCurrencies, useCurrentRates } from '../../hooks/useCurrency';
import { useSettings } from '../../contexts/SettingsContext';
import { useNotify } from '../../contexts/NotificationContext';
import { formatDateTime, formatNumber } from '../../utils/format';

function RateCard({ currency, current }) {
  const { t } = useTranslation();
  const { settings } = useSettings();
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [rate, setRate] = useState('');
  const [notes, setNotes] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [pagination, setPagination] = useState({ page: 0, pageSize: 10 });
  const history = useQuery({
    queryKey: ['exchange-rates', 'history', currency, pagination],
    queryFn: () => currencyService.history(currency, { page: pagination.page, size: pagination.pageSize }),
    placeholderData: keepPreviousData,
  });
  const columns = useMemo(
    () => [
      { field: 'effectiveAt', headerName: t('currencyPage.effective'), width: 180, renderCell: (p) => <Ltr>{formatDateTime(p.value)}</Ltr> },
      { field: 'rate', headerName: t('currencyPage.rate'), width: 200, renderCell: (p) => <Ltr>{`1 ${currency} = ${formatNumber(p.value, { maxDecimals: 6 })} ${settings.baseCurrency}`}</Ltr> },
      { field: 'createdByName', headerName: t('currencyPage.by'), width: 160, renderCell: (p) => <UserText>{p.value}</UserText> },
      { field: 'notes', headerName: t('currencyPage.notes'), flex: 1, minWidth: 160, renderCell: (p) => <UserText>{p.value}</UserText> },
    ],
    [t, currency, settings.baseCurrency],
  );
  const save = async () => {
    if (!(Number(rate) > 0)) return setError(t('validation.positive'));
    setBusy(true);
    setError(null);
    try {
      await currencyService.setRate({ currency, rate, notes: notes || null });
      notify.success(t('currencyPage.saved'));
      setRate('');
      setNotes('');
      queryClient.invalidateQueries({ queryKey: ['exchange-rates'] });
    } catch (e) {
      setError(errorMessage(t, e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <Card>
      <CardHeader
        title={`${currency} — ${t(`currencies.${currency}`, currency)}`}
        subheader={
          <span>
            {t('currencyPage.current')}: <Ltr>{current ? `1 ${currency} = ${formatNumber(current, { maxDecimals: 6 })} ${settings.baseCurrency}` : t('currencyPage.notSet')}</Ltr>
          </span>
        }
      />
      <CardContent>
        <Stack spacing={2}>
          {error && <Alert severity="error">{error}</Alert>}
          <Grid container spacing={2} sx={{ alignItems: 'flex-start' }}>
            <Grid size={{ xs: 12, md: 5 }}>
              <RateField currency={currency} value={rate} onChange={setRate} label={t('currencyPage.setRate')} helperText={t('currencyPage.rateLabel', { currency, base: settings.baseCurrency })} />
            </Grid>
            <Grid size={{ xs: 12, md: 5 }}>
              <TextField label={t('currencyPage.notes')} value={notes} onChange={(e) => setNotes(e.target.value)} />
            </Grid>
            <Grid size={{ xs: 12, md: 2 }}>
              <Button variant="contained" fullWidth onClick={save} loading={busy} sx={{ height: 40 }}>
                {t('common.save')}
              </Button>
            </Grid>
          </Grid>
          <Typography variant="subtitle2">{t('currencyPage.history')}</Typography>
          <Box>
            <DataTable server rows={history.data?.content} rowCount={history.data?.totalElements} columns={columns} loading={history.isFetching} paginationModel={pagination} onPaginationModelChange={setPagination} pageSizeOptions={[10, 20]} />
          </Box>
        </Stack>
      </CardContent>
    </Card>
  );
}

export default function CurrencySettingsPage() {
  const { t } = useTranslation();
  const { settings } = useSettings();
  const currencies = useCurrencies();
  const rates = useCurrentRates();
  return (
    <>
      <PageHeader title={t('currencyPage.title')} subtitle={t('currencyPage.subtitle')} />
      <Alert severity="info" sx={{ mb: 3 }}>
        {t('currencyPage.base')}: <b>{settings.baseCurrency}</b> — {t('currencyPage.baseHint', { base: settings.baseCurrency })}
      </Alert>
      <Stack spacing={3}>
        {currencies
          .filter((c) => c !== settings.baseCurrency)
          .map((c) => (
            <RateCard key={c} currency={c} current={rates.data?.rates?.[c]} />
          ))}
      </Stack>
    </>
  );
}
