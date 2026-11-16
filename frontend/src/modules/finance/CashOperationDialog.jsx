import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert, Grid, MenuItem, Stack, TextField, Typography } from '@mui/material';
import FormDialog from '../../components/form/FormDialog';
import MoneyText from '../../components/display/MoneyText';
import { normalizeDecimalInput } from '../../components/form/FormMoneyField';
import { cashService } from '../../services/cashService';
import { errorMessage } from '../../services/apiClient';
import { useCashAccounts, useCurrentRates } from '../../hooks/useCurrency';
import { useSettings } from '../../contexts/SettingsContext';
import { dec, roundMoney } from '../../utils/money';
import { uuid } from '../../utils/uuid';

/**
 * Owner deposit / withdrawal, same-currency transfer or currency exchange.
 * For an exchange the rate is always entered as "1 <foreign> = X <base>", whichever way the money goes.
 */
export function exchangeTarget(fromAmount, rate, fromCurrency, toCurrency, baseCurrency) {
  const a = dec(fromAmount);
  const r = dec(rate);
  if (a.lte(0) || r.lte(0)) return null;
  if (toCurrency === baseCurrency) return roundMoney(a.times(r), toCurrency);
  if (fromCurrency === baseCurrency) return roundMoney(a.dividedBy(r), toCurrency);
  return roundMoney(a.times(r), toCurrency);
}

export default function CashOperationDialog({ mode, onClose, onSaved }) {
  const { t } = useTranslation();
  const { settings } = useSettings();
  const base = settings.baseCurrency;
  const accounts = useCashAccounts();
  const rates = useCurrentRates();
  const active = useMemo(() => (accounts.data ?? []).filter((a) => a.active), [accounts.data]);
  const [fromId, setFromId] = useState('');
  const [toId, setToId] = useState('');
  const [amount, setAmount] = useState('');
  const [rate, setRate] = useState('');
  const [notes, setNotes] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [requestId, setRequestId] = useState(uuid);
  const open = Boolean(mode);
  const from = active.find((a) => String(a.id) === String(fromId));
  const to = active.find((a) => String(a.id) === String(toId));

  useEffect(() => {
    if (!open) return;
    setAmount('');
    setNotes('');
    setError(null);
    setRequestId(uuid());
    const first = active[0];
    setFromId(first?.id ?? '');
    if (mode === 'EXCHANGE') {
      const other = active.find((a) => a.currency !== first?.currency);
      setToId(other?.id ?? '');
    } else if (mode === 'TRANSFER') {
      setToId(active.find((a) => a.id !== first?.id && a.currency === first?.currency)?.id ?? '');
    }
  }, [open, mode, active]);

  // Default rate: the current rate of the foreign side of the exchange.
  useEffect(() => {
    if (mode !== 'EXCHANGE' || !from || !to) return;
    const foreign = from.currency === base ? to.currency : from.currency;
    setRate(rates.data?.rates?.[foreign] ?? '');
  }, [mode, from, to, base, rates.data]);

  const toOptions = useMemo(() => {
    if (!from) return [];
    if (mode === 'EXCHANGE') return active.filter((a) => a.currency !== from.currency);
    return active.filter((a) => a.id !== from.id && a.currency === from.currency);
  }, [mode, from, active]);
  // Keep the receiving account valid when the sending account changes.
  useEffect(() => {
    if (mode !== 'TRANSFER' && mode !== 'EXCHANGE') return;
    if (!toOptions.some((a) => String(a.id) === String(toId))) setToId(toOptions[0]?.id ?? '');
  }, [mode, toOptions, toId]);
  const received = mode === 'EXCHANGE' && from && to ? exchangeTarget(amount, rate, from.currency, to.currency, base) : null;
  const foreign = from && to ? (from.currency === base ? to.currency : from.currency) : '';

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    if (!from || !(Number(amount) > 0)) return setError(t('errors.VALIDATION_FAILED'));
    setBusy(true);
    try {
      let saved;
      if (mode === 'DEPOSIT' || mode === 'WITHDRAWAL') {
        if (!notes.trim()) throw { code: 'VALIDATION_FAILED' };
        const body = { accountId: from.id, amount, notes: notes.trim() };
        saved = await (mode === 'DEPOSIT' ? cashService.deposit(body) : cashService.withdrawal(body));
      } else {
        if (!to || (mode === 'EXCHANGE' && !received)) throw { code: 'VALIDATION_FAILED' };
        saved = await cashService.transfer({
          clientRequestId: requestId,
          type: mode,
          fromAccountId: from.id,
          toAccountId: to.id,
          fromAmount: amount,
          toAmount: mode === 'EXCHANGE' ? received.toFixed(2) : null,
          notes: notes.trim() || null,
        });
      }
      onSaved(saved);
    } catch (err) {
      setError(err?.code === 'VALIDATION_FAILED' && !err.isNormalized ? t('errors.VALIDATION_FAILED') : errorMessage(t, err));
    } finally {
      setBusy(false);
    }
  };

  const accountMenu = (list) =>
    list.map((a) => (
      <MenuItem key={a.id} value={a.id}>
        {a.name}
        {a.balance != null && (
          <Typography component="span" variant="body2" color="text.secondary">
            &nbsp;(<MoneyText amount={a.balance} currency={a.currency} />)
          </Typography>
        )}
      </MenuItem>
    ));

  return (
    <FormDialog open={open} title={mode ? t(`cashOps.titles.${mode}`) : ''} onClose={onClose} onSubmit={submit} submitting={busy} submitLabel={mode ? t(`cashOps.submit.${mode}`) : ''}>
      <Stack spacing={2} sx={{ pt: 1 }}>
        {mode && <Typography variant="body2" color="text.secondary">{t(`cashOps.hints.${mode}`)}</Typography>}
        {error && <Alert severity="error">{error}</Alert>}
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, sm: mode === 'DEPOSIT' || mode === 'WITHDRAWAL' ? 12 : 6 }}>
            <TextField select label={mode === 'DEPOSIT' || mode === 'WITHDRAWAL' ? t('cash.account') : t('cashOps.from')} value={fromId} onChange={(e) => setFromId(e.target.value)}>
              {accountMenu(active)}
            </TextField>
          </Grid>
          {(mode === 'TRANSFER' || mode === 'EXCHANGE') && (
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField select label={t('cashOps.to')} value={toId} onChange={(e) => setToId(e.target.value)} error={toOptions.length === 0} helperText={toOptions.length === 0 ? t('cashOps.noTarget') : ' '}>
                {accountMenu(toOptions)}
              </TextField>
            </Grid>
          )}
          <Grid size={{ xs: 12, sm: mode === 'EXCHANGE' ? 6 : 12 }}>
            <TextField
              autoFocus
              label={mode === 'EXCHANGE' ? t('cashOps.give') : t('payments.amount')}
              value={amount}
              onChange={(e) => setAmount(normalizeDecimalInput(e.target.value, 2))}
              slotProps={{ htmlInput: { inputMode: 'decimal', dir: 'ltr', style: { textAlign: 'end' } }, input: { endAdornment: from?.currency } }}
            />
          </Grid>
          {mode === 'EXCHANGE' && (
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                label={t('cashOps.rate', { foreign, base })}
                value={rate}
                onChange={(e) => setRate(normalizeDecimalInput(e.target.value, 6))}
                slotProps={{ htmlInput: { inputMode: 'decimal', dir: 'ltr', style: { textAlign: 'end' } }, input: { endAdornment: base } }}
              />
            </Grid>
          )}
        </Grid>
        {mode === 'EXCHANGE' && received && (
          <Alert severity="info" icon={false}>
            {t('cashOps.receive')}: <MoneyText amount={received.toFixed(2)} currency={to?.currency} fontWeight={700} />
          </Alert>
        )}
        <TextField
          label={mode === 'DEPOSIT' || mode === 'WITHDRAWAL' ? t('cashOps.notesRequired') : t('common.notes')}
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          multiline
          minRows={2}
        />
      </Stack>
    </FormDialog>
  );
}
