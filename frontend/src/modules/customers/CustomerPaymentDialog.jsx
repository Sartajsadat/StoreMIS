import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Alert, Grid, MenuItem, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from '@mui/material';
import FormDialog from '../../components/form/FormDialog';
import CustomerPicker from '../../components/party/CustomerPicker';
import RateField from '../../components/party/RateField';
import MoneyText from '../../components/display/MoneyText';
import Ltr from '../../components/display/Ltr';
import { normalizeDecimalInput } from '../../components/form/FormMoneyField';
import { customerPaymentService } from '../../services/customerPaymentService';
import { salesService } from '../../services/salesService';
import { errorMessage } from '../../services/apiClient';
import { useCashAccounts, useCurrencies, useCurrentRates } from '../../hooks/useCurrency';
import { useSettings } from '../../contexts/SettingsContext';
import { formatDate } from '../../utils/format';
import { uuid } from '../../utils/uuid';

/** Receive money from a customer and apply it to an invoice (or oldest unpaid invoices first). */
export default function CustomerPaymentDialog({ open, onClose, onSaved, initialCustomer, initialSaleId }) {
  const { t } = useTranslation();
  const { settings } = useSettings();
  const currencies = useCurrencies();
  const rates = useCurrentRates();
  const accounts = useCashAccounts();
  const [customer, setCustomer] = useState(null);
  const [currency, setCurrency] = useState(settings.baseCurrency);
  const [amount, setAmount] = useState('');
  const [rate, setRate] = useState('');
  const [accountId, setAccountId] = useState('');
  const [saleId, setSaleId] = useState('');
  const [notes, setNotes] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [requestId, setRequestId] = useState(uuid);
  const open$ = useQuery({ queryKey: ['sales', 'open', customer?.id], queryFn: () => salesService.open(customer.id), enabled: open && Boolean(customer?.id) && !customer?.walkIn });
  const accountOptions = useMemo(() => (accounts.data ?? []).filter((a) => a.active && a.currency === currency), [accounts.data, currency]);

  useEffect(() => {
    if (!open) return;
    setCustomer(initialCustomer ?? null);
    setCurrency(settings.baseCurrency);
    setAmount('');
    setNotes('');
    setSaleId(initialSaleId ?? '');
    setError(null);
    setRequestId(uuid());
  }, [open, initialCustomer, initialSaleId, settings.baseCurrency]);

  useEffect(() => {
    setRate(rates.data?.rates?.[currency] ?? '');
    setAccountId((accountOptions.find((a) => a.isDefault) ?? accountOptions[0])?.id ?? '');
  }, [currency, rates.data, accountOptions]);

  const openInvoices = open$.data ?? [];
  useEffect(() => {
    // Suggest the amount still due on the chosen invoice when it is in the same currency.
    const inv = openInvoices.find((s) => String(s.id) === String(saleId));
    if (inv && inv.currency === currency && !amount) setAmount(String(inv.balanceDue));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [saleId, openInvoices.length]);

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    if (!customer || customer.walkIn || !(Number(amount) > 0) || !accountId) return setError(t('errors.VALIDATION_FAILED'));
    setBusy(true);
    try {
      const saved = await customerPaymentService.create({
        customerId: customer.id,
        currency,
        amount,
        exchangeRate: currency === settings.baseCurrency ? null : rate || null,
        cashAccountId: accountId,
        saleId: saleId || null,
        notes: notes || null,
        clientRequestId: requestId,
      });
      onSaved(saved);
    } catch (err) {
      setError(errorMessage(t, err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <FormDialog open={open} title={t('customerPayments.dialogTitle')} onClose={onClose} onSubmit={submit} submitting={busy} maxWidth="md" submitLabel={t('customerPayments.new')}>
      <Stack spacing={2} sx={{ pt: 1 }}>
        {error && <Alert severity="error">{error}</Alert>}
        <CustomerPicker value={customer} onChange={setCustomer} />
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, sm: 4 }}>
            <TextField select label={t('customerPayments.currency')} value={currency} onChange={(e) => setCurrency(e.target.value)}>
              {currencies.map((c) => (
                <MenuItem key={c} value={c}>
                  {c}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, sm: 8 }}>
            <TextField
              label={t('customerPayments.amount')}
              value={amount}
              autoFocus={Boolean(initialCustomer)}
              onChange={(e) => setAmount(normalizeDecimalInput(e.target.value, 2))}
              slotProps={{ htmlInput: { inputMode: 'decimal', dir: 'ltr', style: { textAlign: 'end' } }, input: { endAdornment: currency } }}
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField select label={t('customerPayments.account')} value={accountId} onChange={(e) => setAccountId(e.target.value)}>
              {accountOptions.map((a) => (
                <MenuItem key={a.id} value={a.id}>
                  {a.name}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <RateField currency={currency} value={rate} onChange={setRate} label={t('customerPayments.rate')} />
          </Grid>
          <Grid size={12}>
            <TextField select label={t('customerPayments.apply')} value={saleId} onChange={(e) => setSaleId(e.target.value)} disabled={!customer || customer.walkIn}>
              <MenuItem value="">{t('customerPayments.auto')}</MenuItem>
              {openInvoices.map((s) => (
                <MenuItem key={s.id} value={s.id}>
                  <Ltr>{`${s.invoiceNo} · ${formatDate(s.businessDate)}`}</Ltr>
                  &nbsp;—&nbsp;
                  <MoneyText amount={s.balanceDue} currency={s.currency} />
                </MenuItem>
              ))}
            </TextField>
          </Grid>
        </Grid>
        {customer && !customer.walkIn && (
          <>
            <Typography variant="subtitle2">{t('customerPayments.openInvoices')}</Typography>
            {openInvoices.length === 0 ? (
              <Alert severity="info">{t('customerPayments.nothingOpen')}</Alert>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>{t('sales.number')}</TableCell>
                    <TableCell>{t('sales.date')}</TableCell>
                    <TableCell align="right">{t('sales.total')}</TableCell>
                    <TableCell align="right">{t('sales.due')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {openInvoices.map((s) => (
                    <TableRow key={s.id} selected={String(s.id) === String(saleId)} hover onClick={() => setSaleId(s.id)} sx={{ cursor: 'pointer' }}>
                      <TableCell>
                        <Ltr>{s.invoiceNo}</Ltr>
                      </TableCell>
                      <TableCell>
                        <Ltr>{formatDate(s.businessDate)}</Ltr>
                      </TableCell>
                      <TableCell align="right">
                        <MoneyText amount={s.grandTotal} currency={s.currency} />
                      </TableCell>
                      <TableCell align="right">
                        <MoneyText amount={s.balanceDue} currency={s.currency} fontWeight={600} />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </>
        )}
        <TextField label={t('customerPayments.notes')} value={notes} onChange={(e) => setNotes(e.target.value)} multiline minRows={2} />
      </Stack>
    </FormDialog>
  );
}
