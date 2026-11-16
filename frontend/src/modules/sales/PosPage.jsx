import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  Alert, Badge, Box, Button, Card, CardContent, Dialog, DialogActions, DialogContent, DialogTitle, Divider, Grid, IconButton, InputAdornment, MenuItem,
  Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, ToggleButton, ToggleButtonGroup, Tooltip, Typography,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import PauseCircleOutline from '@mui/icons-material/PauseCircleOutline';
import RestartAltOutlined from '@mui/icons-material/RestartAltOutlined';
import CheckCircleOutline from '@mui/icons-material/CheckCircleOutline';
import PrintOutlined from '@mui/icons-material/PrintOutlined';
import AddOutlined from '@mui/icons-material/AddOutlined';
import ListAltOutlined from '@mui/icons-material/ListAltOutlined';
import ProductPicker from '../../components/inventory/ProductPicker';
import CustomerPicker from '../../components/party/CustomerPicker';
import MoneyText from '../../components/display/MoneyText';
import UserText from '../../components/display/UserText';
import Ltr from '../../components/display/Ltr';
import EmptyState from '../../components/feedback/EmptyState';
import LoadingOverlay from '../../components/feedback/LoadingOverlay';
import ConfirmDialog from '../../components/feedback/ConfirmDialog';
import { normalizeDecimalInput } from '../../components/form/FormMoneyField';
import HeldSalesDrawer from './HeldSalesDrawer';
import { salesService } from '../../services/salesService';
import { customerService } from '../../services/customerService';
import { productService } from '../../services/productService';
import { errorMessage } from '../../services/apiClient';
import { useCashAccounts, useCurrencies, useCurrentRates } from '../../hooks/useCurrency';
import { useUnitLabel } from '../../hooks/useUnitLabel';
import { useAuth } from '../../contexts/AuthContext';
import { useSettings } from '../../contexts/SettingsContext';
import { useNotify } from '../../contexts/NotificationContext';
import { calculateSale, covers } from './saleMath';
import { dec } from '../../utils/money';
import { formatMoney } from '../../utils/format';
import { uuid } from '../../utils/uuid';

const numProps = (decimals) => ({
  size: 'small',
  slotProps: { htmlInput: { inputMode: 'decimal', dir: 'ltr', style: { textAlign: 'end', padding: '6px 8px' } } },
  normalize: (v) => normalizeDecimalInput(v, decimals),
});

function NumberCell({ value, onChange, decimals, error, placeholder, width }) {
  const p = numProps(decimals);
  return (
    <TextField
      value={value}
      onChange={(e) => onChange(p.normalize(e.target.value))}
      size="small"
      error={error}
      placeholder={placeholder}
      sx={{ width }}
      fullWidth={false}
      slotProps={p.slotProps}
    />
  );
}

export default function PosPage() {
  const { t } = useTranslation();
  const theme = useTheme();
  const navigate = useNavigate();
  const notify = useNotify();
  const queryClient = useQueryClient();
  const { hasPermission } = useAuth();
  const { settings } = useSettings();
  const unitLabel = useUnitLabel();
  const currencies = useCurrencies();
  const rates = useCurrentRates();
  const accounts = useCashAccounts();
  const searchRef = useRef(null);
  const canDiscount = hasPermission('GIVE_DISCOUNT');
  const canSeeCost = hasPermission('VIEW_COST_PRICES');

  const walkIn = useQuery({ queryKey: ['customers', 'walk-in'], queryFn: customerService.walkIn, staleTime: Infinity });
  const [customer, setCustomer] = useState(null);
  const [saleType, setSaleType] = useState('RETAIL');
  const [currency, setCurrency] = useState(settings.baseCurrency);
  const [rate, setRate] = useState('1');
  const [lines, setLines] = useState([]);
  const [overall, setOverall] = useState('');
  const [payments, setPayments] = useState([]);
  const [notes, setNotes] = useState('');
  const [held, setHeld] = useState(null); // { id, version } of a resumed held sale
  const [requestId, setRequestId] = useState(uuid);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(null); // { sale, change }
  const [heldOpen, setHeldOpen] = useState(false);
  const [clearOpen, setClearOpen] = useState(false);
  const drafts = useQuery({ queryKey: ['sales', 'drafts'], queryFn: salesService.drafts });

  const effectiveCustomer = customer ?? walkIn.data ?? null;
  const isWalkIn = effectiveCustomer?.walkIn ?? true;
  const saleRate = currency === settings.baseCurrency ? '1' : rate;
  const accountFor = useCallback(
    (cur) => {
      const options = (accounts.data ?? []).filter((a) => a.active && a.currency === cur);
      return (options.find((a) => a.isDefault) ?? options[0])?.id ?? '';
    },
    [accounts.data],
  );

  // A fresh sale: one automatic payment line in the sale currency.
  const reset = useCallback(() => {
    setCustomer(null);
    setSaleType('RETAIL');
    setCurrency(settings.baseCurrency);
    setLines([]);
    setOverall('');
    setNotes('');
    setHeld(null);
    setError(null);
    setRequestId(uuid());
    setPayments([{ key: uuid(), currency: settings.baseCurrency, amount: '', rate: '1', accountId: accountFor(settings.baseCurrency), auto: true }]);
    setTimeout(() => searchRef.current?.focus(), 50);
  }, [settings.baseCurrency, accountFor]);

  useEffect(() => {
    if (payments.length === 0 && accounts.data) reset();
  }, [accounts.data, payments.length, reset]);

  useEffect(() => {
    if (currency !== settings.baseCurrency) setRate(rates.data?.rates?.[currency] ?? '');
  }, [currency, rates.data, settings.baseCurrency]);

  // Sale type follows the customer's type.
  useEffect(() => {
    if (effectiveCustomer?.customerType) setSaleType(effectiveCustomer.customerType);
  }, [effectiveCustomer?.id, effectiveCustomer?.customerType]);

  const calc = useMemo(() => calculateSale(lines, overall), [lines, overall]);
  const covered = (list) => list.reduce((acc, p) => acc.plus(covers(p, currency, saleRate)), dec(0));

  // Keep the automatic payment line equal to what is still due after the other lines.
  useEffect(() => {
    setPayments((ps) => {
      const autoIdx = ps.findIndex((p) => p.auto);
      if (autoIdx < 0) return ps;
      const others = ps.filter((_, i) => i !== autoIdx);
      const due = calc.grandTotal.minus(covered(others));
      const auto = ps[autoIdx];
      const autoRate = auto.currency === currency ? dec(saleRate) : dec(auto.rate);
      const amount = due.gt(0) && autoRate.gt(0) ? due.times(dec(saleRate)).dividedBy(autoRate).toDecimalPlaces(2).toFixed(2) : '';
      if (auto.amount === amount) return ps;
      return ps.map((p, i) => (i === autoIdx ? { ...p, amount } : p));
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [calc.grandTotal.toString(), currency, saleRate, payments.map((p) => `${p.key}:${p.auto ? '' : p.amount}:${p.currency}:${p.rate}`).join('|')]);

  const totalCovered = covered(payments);
  const remaining = calc.grandTotal.minus(totalCovered);
  const change = remaining.lt(0) ? remaining.negated() : dec(0);

  // ---- cart ----
  const addProduct = async (p) => {
    if (!p) return;
    const existing = lines.find((l) => l.product.id === p.id);
    if (existing) {
      const step = dec(1);
      setLines((ls) => ls.map((l) => (l.key === existing.key ? { ...l, quantity: dec(l.quantity).plus(step).toString() } : l)));
    } else {
      const key = uuid();
      setLines((ls) => [...ls, { key, product: p, quantity: '1', unitPrice: '', discount: '', hint: null }]);
      try {
        const hint = await salesService.priceHint(p.id, effectiveCustomer && !effectiveCustomer.walkIn ? effectiveCustomer.id : undefined);
        const suggestion = hint.lastCurrencyForCustomer === currency ? hint.lastPriceForCustomer : hint.lastCurrency === currency ? hint.lastPrice : null;
        setLines((ls) => ls.map((l) => (l.key === key ? { ...l, hint, unitPrice: l.unitPrice || (suggestion ? String(Number(suggestion)) : '') } : l)));
      } catch {
        /* hints are optional */
      }
    }
    setTimeout(() => searchRef.current?.focus(), 30);
  };
  const updateLine = (key, patch) => setLines((ls) => ls.map((l) => (l.key === key ? { ...l, ...patch } : l)));
  const removeLine = (key) => setLines((ls) => ls.filter((l) => l.key !== key));

  // ---- payments ----
  // Extra payment lines are typed in by hand; the first (sale-currency) line keeps absorbing whatever is still due.
  const addPayment = (cur) =>
    setPayments((ps) => [...ps, { key: uuid(), currency: cur, amount: '', rate: cur === settings.baseCurrency ? '1' : rates.data?.rates?.[cur] ?? '', accountId: accountFor(cur), auto: false }]);
  const updatePayment = (key, patch) => setPayments((ps) => ps.map((p) => (p.key === key ? { ...p, ...patch, auto: patch.amount !== undefined ? false : p.auto } : p)));
  const removePayment = (key) => setPayments((ps) => ps.filter((p) => p.key !== key));

  const saleBody = () => ({
    customerId: isWalkIn ? null : effectiveCustomer.id,
    saleType,
    currency,
    exchangeRate: currency === settings.baseCurrency ? null : rate || null,
    overallDiscount: overall || null,
    notes: notes || null,
    items: lines.map((l) => ({ productId: l.product.id, quantity: l.quantity, unitPrice: l.unitPrice, discount: l.discount || null })),
  });

  const complete = async () => {
    setError(null);
    if (lines.length === 0) return;
    if (lines.some((l) => l.unitPrice === '' || !(Number(l.quantity) > 0))) return setError(t('pos.priceRequired'));
    if (calc.lines.some((l) => l.invalid) || calc.overallInvalid) return setError(t('errors.DISCOUNT_TOO_LARGE'));
    let finalPayments = payments.filter((p) => Number(p.amount) > 0);
    if (change.gt(0)) {
      // Change is given back in the sale currency: record only what stays in the drawer.
      const idx = finalPayments.findIndex((p) => p.currency === currency && dec(p.amount).greaterThanOrEqualTo(change));
      if (idx >= 0) {
        finalPayments = finalPayments.map((p, i) => (i === idx ? { ...p, amount: dec(p.amount).minus(change).toFixed(2) } : p)).filter((p) => Number(p.amount) > 0);
      } else if (isWalkIn) {
        const other = finalPayments.find((p) => p.currency !== currency)?.currency ?? '';
        return setError(t('pos.foreignChange', { currency, other }));
      }
    }
    if (isWalkIn && remaining.greaterThan(dec('0.01'))) return setError(t('pos.walkInPayFull'));
    setBusy(true);
    try {
      const paymentLines = finalPayments.map((p) => ({ currency: p.currency, amount: p.amount, exchangeRate: p.currency === settings.baseCurrency ? null : p.rate || null, cashAccountId: p.accountId }));
      let sale;
      if (held) {
        await salesService.updateDraft(held.id, { ...saleBody(), version: held.version });
        sale = await salesService.confirmDraft(held.id, { payments: paymentLines, clientRequestId: requestId });
      } else {
        sale = await salesService.confirm({ ...saleBody(), payments: paymentLines, clientRequestId: requestId });
      }
      setDone({ sale, change });
      ['sales', 'products', 'inventory', 'customers', 'cash-accounts', 'customer-payments', 'product-search'].forEach((k) => queryClient.invalidateQueries({ queryKey: [k] }));
    } catch (e) {
      setError(errorMessage(t, e));
    } finally {
      setBusy(false);
    }
  };

  const hold = async () => {
    if (lines.length === 0) return;
    if (lines.some((l) => l.unitPrice === '')) return setError(t('pos.priceRequired'));
    setBusy(true);
    try {
      if (held) await salesService.updateDraft(held.id, { ...saleBody(), version: held.version });
      else await salesService.createDraft(saleBody());
      notify.info(t('pos.heldSaved'));
      queryClient.invalidateQueries({ queryKey: ['sales', 'drafts'] });
      reset();
    } catch (e) {
      setError(errorMessage(t, e));
    } finally {
      setBusy(false);
    }
  };

  const resume = async (d) => {
    setHeldOpen(false);
    const products = await Promise.all(d.items.map((i) => productService.get(i.productId).catch(() => null)));
    const c = d.walkIn ? null : await customerService.get(d.customerId).catch(() => null);
    setCustomer(c);
    setSaleType(d.saleType);
    setCurrency(d.currency);
    setRate(String(d.exchangeRate));
    setOverall(Number(d.overallDiscount) ? String(d.overallDiscount) : '');
    setNotes(d.notes ?? '');
    setLines(
      d.items.map((i, idx) => ({
        key: uuid(),
        product: products[idx] ?? { id: i.productId, name: i.productName, unit: { name: i.unitName, allowDecimal: true } },
        quantity: String(Number(i.quantity)),
        unitPrice: String(Number(i.unitPrice)),
        discount: Number(i.discount) ? String(i.discount) : '',
        hint: null,
      })),
    );
    setHeld({ id: d.id, version: d.version });
    setRequestId(uuid());
    setPayments([{ key: uuid(), currency: d.currency, amount: '', rate: d.currency === settings.baseCurrency ? '1' : String(d.exchangeRate), accountId: accountFor(d.currency), auto: true }]);
    notify.info(t('pos.resumed'));
  };

  // Keyboard shortcuts: F2 search, F9 complete.
  useEffect(() => {
    const onKey = (e) => {
      if (e.key === 'F2') {
        e.preventDefault();
        searchRef.current?.focus();
      } else if (e.key === 'F9') {
        e.preventDefault();
        if (!busy && !done) complete();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  const onCurrencyChange = (cur) => {
    setCurrency(cur);
    setPayments([{ key: uuid(), currency: cur, amount: '', rate: cur === settings.baseCurrency ? '1' : rates.data?.rates?.[cur] ?? '', accountId: accountFor(cur), auto: true }]);
  };

  const printReceipt = (id) => window.open(`/print/sales/${id}?autoprint=1`, '_blank', 'noopener,width=900,height=900');

  return (
    <>
      <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between', mb: 2, gap: 1, flexWrap: 'wrap' }}>
        <Typography variant="caption" color="text.secondary">
          {t('pos.shortcuts')}
        </Typography>
        <Stack direction="row" spacing={1}>
          <Badge color="primary" badgeContent={drafts.data?.length ?? 0}>
            <Button variant="outlined" startIcon={<ListAltOutlined />} onClick={() => setHeldOpen(true)}>
              {t('pos.held')}
            </Button>
          </Badge>
          <Button variant="outlined" startIcon={<RestartAltOutlined />} onClick={() => setClearOpen(true)} disabled={lines.length === 0}>
            {t('pos.clear')}
          </Button>
        </Stack>
      </Stack>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, lg: 8 }}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <ProductPicker value={null} onChange={addProduct} label={t('pos.search')} inputRef={searchRef} autoFocus clearOnSelect size="medium" />
              {lines.length === 0 ? (
                <EmptyState title={t('pos.empty')} />
              ) : (
                <Box sx={{ overflowX: 'auto', mt: 2 }}>
                  <Table size="small" sx={{ minWidth: 640 }}>
                    <TableHead>
                      <TableRow>
                        <TableCell>{t('pos.product')}</TableCell>
                        <TableCell align="center">{t('pos.qty')}</TableCell>
                        <TableCell align="center">{t('pos.price')}</TableCell>
                        {canDiscount && <TableCell align="center">{t('pos.discount')}</TableCell>}
                        <TableCell align="right">{t('pos.total')}</TableCell>
                        <TableCell />
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {lines.map((l, idx) => {
                        const r = calc.lines[idx];
                        const hint = l.hint;
                        const unitBase = dec(l.unitPrice).times(dec(saleRate));
                        const belowCost = canSeeCost && hint?.avgCost != null && l.unitPrice !== '' && unitBase.lessThan(dec(hint.avgCost));
                        return (
                          <TableRow key={l.key} hover>
                            <TableCell sx={{ maxWidth: 260 }}>
                              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                <UserText>{l.product.name}</UserText>
                              </Typography>
                              <Typography variant="caption" color="text.secondary" component="div">
                                {unitLabel(l.product.unit)}
                                {hint?.lastPriceForCustomer != null && ` · ${t('pos.lastPriceCustomer', { price: formatMoney(hint.lastPriceForCustomer, hint.lastCurrencyForCustomer) })}`}
                                {hint?.lastPriceForCustomer == null && hint?.lastPrice != null && ` · ${t('pos.lastPrice', { price: formatMoney(hint.lastPrice, hint.lastCurrency) })}`}
                              </Typography>
                              {canSeeCost && hint?.avgCost != null && (
                                <Typography variant="caption" sx={{ color: belowCost ? 'error.main' : 'text.secondary', fontWeight: belowCost ? 700 : 400 }}>
                                  {t('pos.cost', { cost: formatMoney(hint.avgCost, hint.baseCurrency) })}
                                  {belowCost && ` · ${t('pos.belowCost')}`}
                                </Typography>
                              )}
                            </TableCell>
                            <TableCell align="center">
                              <NumberCell value={l.quantity} onChange={(v) => updateLine(l.key, { quantity: v })} decimals={l.product.unit?.allowDecimal ? 3 : 0} width={80} error={!(Number(l.quantity) > 0)} />
                            </TableCell>
                            <TableCell align="center">
                              <NumberCell value={l.unitPrice} onChange={(v) => updateLine(l.key, { unitPrice: v })} decimals={2} width={120} error={l.unitPrice === '' || belowCost} placeholder="0" />
                            </TableCell>
                            {canDiscount && (
                              <TableCell align="center">
                                <NumberCell value={l.discount} onChange={(v) => updateLine(l.key, { discount: v })} decimals={2} width={100} error={r?.invalid} placeholder="0" />
                              </TableCell>
                            )}
                            <TableCell align="right">
                              <MoneyText amount={r?.lineTotal.toFixed(2)} currency={currency} fontWeight={700} />
                            </TableCell>
                            <TableCell padding="checkbox">
                              <Tooltip title={t('pos.remove')}>
                                <IconButton size="small" onClick={() => removeLine(l.key)} aria-label={t('pos.remove')}>
                                  <DeleteOutline fontSize="small" />
                                </IconButton>
                              </Tooltip>
                            </TableCell>
                          </TableRow>
                        );
                      })}
                    </TableBody>
                  </Table>
                </Box>
              )}
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, lg: 4 }}>
          <Stack spacing={2}>
            <Card>
              <CardContent>
                <Stack spacing={1.5}>
                  <CustomerPicker value={effectiveCustomer} onChange={(c) => setCustomer(c)} disableClearable />
                  <Stack direction="row" spacing={1}>
                    <ToggleButtonGroup size="small" exclusive value={saleType} onChange={(_e, v) => v && setSaleType(v)} fullWidth>
                      <ToggleButton value="RETAIL">{t('saleTypes.RETAIL')}</ToggleButton>
                      <ToggleButton value="WHOLESALE">{t('saleTypes.WHOLESALE')}</ToggleButton>
                    </ToggleButtonGroup>
                    <ToggleButtonGroup size="small" exclusive value={currency} onChange={(_e, v) => v && onCurrencyChange(v)}>
                      {currencies.map((c) => (
                        <ToggleButton key={c} value={c}>
                          {c}
                        </ToggleButton>
                      ))}
                    </ToggleButtonGroup>
                  </Stack>
                  {currency !== settings.baseCurrency && (
                    <TextField
                      label={t('pos.rate')}
                      value={rate}
                      onChange={(e) => setRate(normalizeDecimalInput(e.target.value, 6))}
                      slotProps={{ htmlInput: { inputMode: 'decimal', dir: 'ltr' }, input: { startAdornment: <InputAdornment position="start">{`1 ${currency} =`}</InputAdornment>, endAdornment: settings.baseCurrency } }}
                    />
                  )}
                </Stack>
              </CardContent>
            </Card>

            <Card>
              <CardContent>
                <Stack spacing={1}>
                  <Line label={t('pos.subtotal')} value={<MoneyText amount={calc.subtotal.toFixed(2)} currency={currency} />} />
                  {calc.itemDiscounts.gt(0) && <Line label={t('pos.itemDiscounts')} value={<MoneyText amount={calc.itemDiscounts.toFixed(2)} currency={currency} />} />}
                  {canDiscount && (
                    <TextField
                      label={t('pos.overallDiscount')}
                      value={overall}
                      onChange={(e) => setOverall(normalizeDecimalInput(e.target.value, 2))}
                      error={calc.overallInvalid}
                      slotProps={{ htmlInput: { inputMode: 'decimal', dir: 'ltr', style: { textAlign: 'end' } }, input: { endAdornment: currency } }}
                    />
                  )}
                  <Divider />
                  <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'baseline' }}>
                    <Typography variant="h6">{t('pos.grandTotal')}</Typography>
                    <Typography variant="h4" sx={{ fontWeight: 800, color: 'primary.main' }}>
                      <Ltr>{formatMoney(calc.grandTotal.toFixed(2), currency)}</Ltr>
                    </Typography>
                  </Stack>
                </Stack>
              </CardContent>
            </Card>

            <Card>
              <CardContent>
                <Typography variant="subtitle1" sx={{ mb: 1 }}>
                  {t('pos.payments')}
                </Typography>
                <Stack spacing={1.25}>
                  {payments.map((p) => {
                    const options = (accounts.data ?? []).filter((a) => a.active && a.currency === p.currency);
                    return (
                      <Stack key={p.key} direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                        <TextField
                          label={`${t('pos.received')} (${p.currency})`}
                          value={p.amount}
                          onChange={(e) => updatePayment(p.key, { amount: normalizeDecimalInput(e.target.value, 2) })}
                          slotProps={{ htmlInput: { inputMode: 'decimal', dir: 'ltr', style: { textAlign: 'end', fontWeight: 700 } } }}
                        />
                        {p.currency !== currency && (
                          <TextField label={t('pos.rate')} value={p.rate} onChange={(e) => updatePayment(p.key, { rate: normalizeDecimalInput(e.target.value, 6) })} sx={{ width: 110 }} fullWidth={false} slotProps={{ htmlInput: { dir: 'ltr', inputMode: 'decimal' } }} />
                        )}
                        {options.length > 1 && (
                          <TextField select label={t('pos.account')} value={p.accountId} onChange={(e) => updatePayment(p.key, { accountId: e.target.value })} sx={{ width: 140 }} fullWidth={false}>
                            {options.map((a) => (
                              <MenuItem key={a.id} value={a.id}>
                                {a.name}
                              </MenuItem>
                            ))}
                          </TextField>
                        )}
                        {payments.length > 1 && (
                          <IconButton size="small" onClick={() => removePayment(p.key)} aria-label={t('pos.remove')}>
                            <DeleteOutline fontSize="small" />
                          </IconButton>
                        )}
                      </Stack>
                    );
                  })}
                  <Stack direction="row" spacing={1}>
                    {currencies
                      .filter((c) => !payments.some((p) => p.currency === c))
                      .map((c) => (
                        <Button key={c} size="small" startIcon={<AddOutlined />} onClick={() => addPayment(c)}>
                          {t('pos.addPayment', { currency: c })}
                        </Button>
                      ))}
                  </Stack>
                  <Divider />
                  {change.gt(0) ? (
                    <Line label={<b>{t('pos.change')}</b>} value={<MoneyText amount={change.toFixed(2)} currency={currency} variant="h6" color={theme.palette.success.main} />} />
                  ) : (
                    <Line
                      label={remaining.gt(0) ? (isWalkIn ? t('pos.remaining') : t('pos.onCredit')) : t('pos.remaining')}
                      value={<MoneyText amount={Decimal0(remaining)} currency={currency} variant="h6" color={remaining.gt(0) ? (isWalkIn ? theme.palette.error.main : theme.palette.warning.main) : undefined} />}
                    />
                  )}
                </Stack>
              </CardContent>
            </Card>

            <TextField label={t('pos.notes')} value={notes} onChange={(e) => setNotes(e.target.value)} size="small" />
            <Stack direction="row" spacing={1}>
              <Button variant="outlined" size="large" startIcon={<PauseCircleOutline />} onClick={hold} disabled={lines.length === 0 || busy}>
                {t('pos.hold')}
              </Button>
              <Button variant="contained" size="large" color="success" startIcon={<CheckCircleOutline />} onClick={complete} disabled={lines.length === 0 || busy} sx={{ flex: 1, py: 1.5, fontSize: '1.05rem' }}>
                {t('pos.complete')}
              </Button>
            </Stack>
          </Stack>
        </Grid>
      </Grid>

      <Dialog open={Boolean(done)} maxWidth="xs" fullWidth onClose={() => (setDone(null), reset())}>
        <DialogTitle sx={{ textAlign: 'center' }}>
          <CheckCircleOutline color="success" sx={{ fontSize: 56, display: 'block', mx: 'auto', mb: 1 }} />
          {t('pos.successTitle')}
        </DialogTitle>
        {done && (
          <DialogContent sx={{ textAlign: 'center' }}>
            <Typography color="text.secondary">{t('pos.invoice')}</Typography>
            <Typography variant="h6">
              <Ltr>{done.sale.invoiceNo}</Ltr>
            </Typography>
            <Typography variant="h4" sx={{ my: 1, fontWeight: 800 }}>
              <MoneyText amount={done.sale.grandTotal} currency={done.sale.currency} />
            </Typography>
            {done.sale.status === 'CONFIRMED' && <Typography color="text.secondary">{t(`status.${done.sale.paymentStatus}`)}</Typography>}
            {done.change.gt(0) && (
              <Alert severity="success" sx={{ mt: 2, justifyContent: 'center', fontSize: '1.1rem' }}>
                {t('pos.giveChange', { amount: formatMoney(done.change.toFixed(2), done.sale.currency) })}
              </Alert>
            )}
          </DialogContent>
        )}
        <DialogActions sx={{ px: 3, pb: 3, gap: 1, justifyContent: 'center', flexWrap: 'wrap' }}>
          <Button variant="outlined" startIcon={<PrintOutlined />} onClick={() => printReceipt(done.sale.id)} autoFocus>
            {t('pos.printReceipt')}
          </Button>
          <Button variant="text" onClick={() => navigate(`/sales/${done.sale.id}`)}>
            {t('pos.viewInvoice')}
          </Button>
          <Button variant="contained" onClick={() => (setDone(null), reset())}>
            {t('pos.newSale')}
          </Button>
        </DialogActions>
      </Dialog>
      <HeldSalesDrawer open={heldOpen} onClose={() => setHeldOpen(false)} onResume={resume} onDeleted={() => drafts.refetch()} />
      <ConfirmDialog open={clearOpen} message={t('pos.clearConfirm')} onClose={() => setClearOpen(false)} onConfirm={() => (setClearOpen(false), reset())} />
      <LoadingOverlay open={busy} />
    </>
  );
}

const Decimal0 = (d) => (d.lt(0) ? '0.00' : d.toFixed(2));

function Line({ label, value }) {
  return (
    <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
      <Typography variant="body2" color="text.secondary" component="div">
        {label}
      </Typography>
      <Box>{value}</Box>
    </Stack>
  );
}
