import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Alert, Grid, MenuItem, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from '@mui/material';
import FormDialog from '../../components/form/FormDialog';
import SupplierPicker from '../../components/party/SupplierPicker';
import RateField from '../../components/party/RateField';
import MoneyText from '../../components/display/MoneyText';
import Ltr from '../../components/display/Ltr';
import { normalizeDecimalInput } from '../../components/form/FormMoneyField';
import { supplierPaymentService } from '../../services/supplierPaymentService';
import { purchaseService } from '../../services/purchaseService';
import { errorMessage } from '../../services/apiClient';
import { useCashAccounts, useCurrencies, useCurrentRates } from '../../hooks/useCurrency';
import { useSettings } from '../../contexts/SettingsContext';
import { formatDate } from '../../utils/format';
import { uuid } from '../../utils/uuid';

/** Pay a supplier: amount, currency, cash account, and which purchase to apply it to (default: oldest first). */
export default function SupplierPaymentDialog({ open, onClose, onSaved, initialSupplier, initialPurchaseId }) {
  const { t } = useTranslation();
  const { settings } = useSettings();
  const currencies = useCurrencies();
  const rates = useCurrentRates();
  const accounts = useCashAccounts();
  const [supplier, setSupplier] = useState(null);
  const [currency, setCurrency] = useState(settings.baseCurrency);
  const [amount, setAmount] = useState('');
  const [rate, setRate] = useState('');
  const [accountId, setAccountId] = useState('');
  const [purchaseId, setPurchaseId] = useState('');
  const [notes, setNotes] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [requestId, setRequestId] = useState(uuid);

  const open$ = useQuery({ queryKey: ['purchases', 'open', supplier?.id], queryFn: () => purchaseService.open(supplier.id), enabled: open && Boolean(supplier?.id) });
  const accountOptions = useMemo(() => (accounts.data ?? []).filter((a) => a.active && a.currency === currency), [accounts.data, currency]);

  useEffect(() => {
    if (!open) return;
    setSupplier(initialSupplier ?? null);
    setCurrency(settings.baseCurrency);
    setAmount('');
    setNotes('');
    setPurchaseId(initialPurchaseId ?? '');
    setError(null);
    setRequestId(uuid());
  }, [open, initialSupplier, initialPurchaseId, settings.baseCurrency]);

  useEffect(() => {
    setRate(rates.data?.rates?.[currency] ?? '');
    const def = accountOptions.find((a) => a.isDefault) ?? accountOptions[0];
    setAccountId(def?.id ?? '');
  }, [currency, rates.data, accountOptions]);

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    if (!supplier || !amount || Number(amount) <= 0 || !accountId) {
      setError(t('errors.VALIDATION_FAILED'));
      return;
    }
    setBusy(true);
    try {
      const saved = await supplierPaymentService.create({
        supplierId: supplier.id,
        currency,
        amount,
        exchangeRate: currency === settings.baseCurrency ? null : rate || null,
        cashAccountId: accountId,
        purchaseId: purchaseId || null,
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

  const openPurchases = open$.data ?? [];
  return (
    <FormDialog open={open} title={t('payments.dialogTitle')} onClose={onClose} onSubmit={submit} submitting={busy} maxWidth="md" submitLabel={t('payments.new')}>
      <Stack spacing={2} sx={{ pt: 1 }}>
        {error && <Alert severity="error">{error}</Alert>}
        <SupplierPicker value={supplier} onChange={setSupplier} autoFocus={!initialSupplier} />
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, sm: 4 }}>
            <TextField select label={t('payments.currency')} value={currency} onChange={(e) => setCurrency(e.target.value)}>
              {currencies.map((c) => (
                <MenuItem key={c} value={c}>
                  {c}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, sm: 8 }}>
            <TextField
              label={t('payments.amount')}
              value={amount}
              onChange={(e) => setAmount(normalizeDecimalInput(e.target.value, 2))}
              slotProps={{ htmlInput: { inputMode: 'decimal', dir: 'ltr', style: { textAlign: 'end' } }, input: { endAdornment: currency } }}
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField select label={t('payments.cashAccount')} value={accountId} onChange={(e) => setAccountId(e.target.value)}>
              {accountOptions.map((a) => (
                <MenuItem key={a.id} value={a.id}>
                  {a.name}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <RateField currency={currency} value={rate} onChange={setRate} label={t('payments.exchangeRate')} />
          </Grid>
          <Grid size={12}>
            <TextField select label={t('payments.purchase')} value={purchaseId} onChange={(e) => setPurchaseId(e.target.value)} disabled={!supplier}>
              <MenuItem value="">{t('payments.autoAllocate')}</MenuItem>
              {openPurchases.map((p) => (
                <MenuItem key={p.id} value={p.id}>
                  <Ltr>{`${p.purchaseNo} · ${formatDate(p.purchaseDate)}`}</Ltr>
                  &nbsp;—&nbsp;
                  <MoneyText amount={p.balanceDue} currency={p.currency} />
                </MenuItem>
              ))}
            </TextField>
          </Grid>
        </Grid>
        {supplier && (
          <>
            <Typography variant="subtitle2">{t('payments.openPurchases')}</Typography>
            {openPurchases.length === 0 ? (
              <Alert severity="info">{t('payments.nothingOpen')}</Alert>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>{t('purchases.number')}</TableCell>
                    <TableCell>{t('purchases.date')}</TableCell>
                    <TableCell align="right">{t('purchases.total')}</TableCell>
                    <TableCell align="right">{t('purchases.due')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {openPurchases.map((p) => (
                    <TableRow key={p.id} selected={String(p.id) === String(purchaseId)}>
                      <TableCell>
                        <Ltr>{p.purchaseNo}</Ltr>
                      </TableCell>
                      <TableCell>
                        <Ltr>{formatDate(p.purchaseDate)}</Ltr>
                      </TableCell>
                      <TableCell align="right">
                        <MoneyText amount={p.grandTotal} currency={p.currency} />
                      </TableCell>
                      <TableCell align="right">
                        <MoneyText amount={p.balanceDue} currency={p.currency} fontWeight={600} />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </>
        )}
        <TextField label={t('payments.notes')} value={notes} onChange={(e) => setNotes(e.target.value)} multiline minRows={2} />
      </Stack>
    </FormDialog>
  );
}
