import { useEffect, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Alert, Box, Button, Card, CardContent, CardHeader, FormControlLabel, Grid, MenuItem, Stack, Switch, Table, TableBody, TableCell, TableHead, TableRow, TextField } from '@mui/material';
import PageHeader from '../../components/display/PageHeader';
import PageLoader from '../../components/feedback/PageLoader';
import Ltr from '../../components/display/Ltr';
import { settingsService } from '../../services/settingsService';
import { sequenceService } from '../../services/sequenceService';
import { errorMessage } from '../../services/apiClient';
import { useNotify } from '../../contexts/NotificationContext';

function PrefixRow({ seq, onSaved }) {
  const { t } = useTranslation();
  const notify = useNotify();
  const [value, setValue] = useState(seq.prefix);
  const [busy, setBusy] = useState(false);
  const valid = /^[A-Za-z0-9]{1,10}$/.test(value);
  return (
    <TableRow>
      <TableCell>{t(`invoiceSettings.docTypes.${seq.docType}`)}</TableCell>
      <TableCell sx={{ width: 160 }}>
        <TextField value={value} onChange={(e) => setValue(e.target.value.toUpperCase())} size="small" error={!valid} slotProps={{ htmlInput: { dir: 'ltr', maxLength: 10 } }} />
      </TableCell>
      <TableCell>
        <Ltr>{seq.nextNumberPreview}</Ltr>
      </TableCell>
      <TableCell sx={{ width: 100 }}>
        <Button
          size="small"
          disabled={!valid || value === seq.prefix || busy}
          onClick={async () => {
            setBusy(true);
            try {
              await sequenceService.update(seq.docType, value);
              notify.success(t('invoiceSettings.prefixSaved'));
              onSaved();
            } catch (e) {
              notify.error(errorMessage(t, e));
            } finally {
              setBusy(false);
            }
          }}
        >
          {t('common.save')}
        </Button>
      </TableCell>
    </TableRow>
  );
}

export default function InvoiceSettingsPage() {
  const { t } = useTranslation();
  const notify = useNotify();
  const queryClient = useQueryClient();
  const settings = useQuery({ queryKey: ['settings', 'business'], queryFn: settingsService.getBusiness });
  const sequences = useQuery({ queryKey: ['settings', 'sequences'], queryFn: sequenceService.list });
  const [form, setForm] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (settings.data) setForm({ receiptFormat: settings.data.receiptFormat, invoiceHeaderNote: settings.data.invoiceHeaderNote ?? '', invoiceFooterNote: settings.data.invoiceFooterNote ?? '', showLogoOnReceipt: settings.data.showLogoOnReceipt });
  }, [settings.data]);

  if (!form || !settings.data) return <PageLoader />;
  const save = async () => {
    setBusy(true);
    setError(null);
    try {
      const updated = await settingsService.updateBusiness({ ...settings.data, ...form, invoiceHeaderNote: form.invoiceHeaderNote || null, invoiceFooterNote: form.invoiceFooterNote || null });
      queryClient.setQueryData(['settings', 'business'], updated);
      notify.success(t('invoiceSettings.saved'));
    } catch (e) {
      setError(errorMessage(t, e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader
        title={t('invoiceSettings.title')}
        subtitle={t('invoiceSettings.subtitle')}
        actions={
          <Button variant="contained" onClick={save} loading={busy}>
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
        <Grid size={{ xs: 12, lg: 6 }}>
          <Card>
            <CardContent>
              <Stack spacing={2}>
                <TextField select label={t('invoiceSettings.format')} value={form.receiptFormat} onChange={(e) => setForm({ ...form, receiptFormat: e.target.value })}>
                  {['THERMAL_80', 'THERMAL_58', 'A4'].map((f) => (
                    <MenuItem key={f} value={f}>
                      {t(`invoiceSettings.${f}`)}
                    </MenuItem>
                  ))}
                </TextField>
                <TextField label={t('invoiceSettings.header')} helperText={t('invoiceSettings.headerHint')} value={form.invoiceHeaderNote} onChange={(e) => setForm({ ...form, invoiceHeaderNote: e.target.value })} slotProps={{ htmlInput: { maxLength: 500 } }} />
                <TextField label={t('invoiceSettings.footer')} helperText={t('invoiceSettings.footerHint')} value={form.invoiceFooterNote} onChange={(e) => setForm({ ...form, invoiceFooterNote: e.target.value })} multiline minRows={2} slotProps={{ htmlInput: { maxLength: 500 } }} />
                <FormControlLabel control={<Switch checked={form.showLogoOnReceipt} onChange={(e) => setForm({ ...form, showLogoOnReceipt: e.target.checked })} />} label={t('invoiceSettings.showLogo')} />
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, lg: 6 }}>
          <Card>
            <CardHeader title={t('invoiceSettings.numbering')} subheader={t('invoiceSettings.prefixHint')} />
            <Box sx={{ overflowX: 'auto' }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell />
                    <TableCell>{t('invoiceSettings.prefix')}</TableCell>
                    <TableCell>{t('invoiceSettings.next')}</TableCell>
                    <TableCell />
                  </TableRow>
                </TableHead>
                <TableBody>
                  {(sequences.data ?? []).map((s) => (
                    <PrefixRow key={s.docType} seq={s} onSaved={() => sequences.refetch()} />
                  ))}
                </TableBody>
              </Table>
            </Box>
          </Card>
        </Grid>
      </Grid>
    </>
  );
}
