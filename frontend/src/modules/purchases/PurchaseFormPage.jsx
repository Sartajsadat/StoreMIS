import { useEffect, useMemo, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  Alert, Box, Button, Card, CardContent, Divider, Grid, IconButton, InputAdornment, MenuItem, Stack, Table, TableBody, TableCell, TableHead, TableRow,
  TextField, Tooltip, Typography,
} from '@mui/material';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import dayjs from 'dayjs';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import SaveOutlined from '@mui/icons-material/SaveOutlined';
import CheckCircleOutline from '@mui/icons-material/CheckCircleOutline';
import PageHeader from '../../components/display/PageHeader';
import PageLoader from '../../components/feedback/PageLoader';
import LoadingOverlay from '../../components/feedback/LoadingOverlay';
import ConfirmDialog from '../../components/feedback/ConfirmDialog';
import ProductPicker from '../../components/inventory/ProductPicker';
import SupplierPicker from '../../components/party/SupplierPicker';
import RateField from '../../components/party/RateField';
import MoneyText from '../../components/display/MoneyText';
import UserText from '../../components/display/UserText';
import EmptyState from '../../components/feedback/EmptyState';
import { normalizeDecimalInput } from '../../components/form/FormMoneyField';
import { purchaseService } from '../../services/purchaseService';
import { productService } from '../../services/productService';
import { errorMessage } from '../../services/apiClient';
import { useCashAccounts, useCurrencies, useCurrentRates } from '../../hooks/useCurrency';
import { useSettings } from '../../contexts/SettingsContext';
import { useNotify } from '../../contexts/NotificationContext';
import { useUnitLabel } from '../../hooks/useUnitLabel';
import { calculatePurchase } from './purchaseMath';
import { dec } from '../../utils/money';
import { uuid } from '../../utils/uuid';

const numInput = (value, onChange, decimals, props = {}) => ({
  value,
  onChange: (e) => onChange(normalizeDecimalInput(e.target.value, decimals)),
  size: 'small',
  slotProps: { htmlInput: { inputMode: 'decimal', dir: 'ltr', style: { textAlign: 'end' } }, ...props.slotProps },
  ...props,
});

export default function PurchaseFormPage() {
  const { id } = useParams();
  const isEdit = Boolean(id);
  const { t } = useTranslation();
  const navigate = useNavigate();
  const notify = useNotify();
  const queryClient = useQueryClient();
  const { settings } = useSettings();
  const unitLabel = useUnitLabel();
  const currencies = useCurrencies();
  const rates = useCurrentRates();
  const accounts = useCashAccounts();

  const [supplier, setSupplier] = useState(null);
  const [invoiceNo, setInvoiceNo] = useState('');
  const [date, setDate] = useState(dayjs());
  const [currency, setCurrency] = useState(settings.baseCurrency);
  const [rate, setRate] = useState('');
  const [lines, setLines] = useState([]);
  const [overall, setOverall] = useState('');
  const [notes, setNotes] = useState('');
  const [paid, setPaid] = useState('');
  const [accountId, setAccountId] = useState('');
  const [version, setVersion] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [loaded, setLoaded] = useState(!isEdit);
  const [draftId, setDraftId] = useState(null);

  const draft = useQuery({ queryKey: ['purchases', 'detail', id], queryFn: () => purchaseService.get(id), enabled: isEdit });

  // Load an existing draft into the form.
  useEffect(() => {
    const d = draft.data;
    if (!d || loaded) return;
    (async () => {
      const products = await Promise.all(d.items.map((i) => productService.get(i.productId).catch(() => null)));
      setSupplier({ id: d.supplierId, name: d.supplierName });
      setInvoiceNo(d.supplierInvoiceNo ?? '');
      setDate(dayjs(d.purchaseDate));
      setCurrency(d.currency);
      setRate(String(d.exchangeRate));
      setOverall(Number(d.overallDiscount) ? String(d.overallDiscount) : '');
      setNotes(d.notes ?? '');
      setVersion(d.version);
      setLines(
        d.items.map((i, idx) => ({
          key: `${i.productId}-${idx}`,
          product: products[idx] ?? { id: i.productId, name: i.productName, unit: { name: i.unitName, allowDecimal: true } },
          quantity: String(Number(i.quantity)),
          unitCost: String(Number(i.unitCost)),
          discount: Number(i.discount) ? String(i.discount) : '',
        })),
      );
      setLoaded(true);
    })();
  }, [draft.data, loaded]);

  // Default rate and cash account when the currency changes.
  useEffect(() => {
    if (!isEdit || loaded) {
      if (currency !== settings.baseCurrency && !(isEdit && draft.data?.currency === currency)) setRate(rates.data?.rates?.[currency] ?? '');
    }
    const options = (accounts.data ?? []).filter((a) => a.active && a.currency === currency);
    setAccountId((options.find((a) => a.isDefault) ?? options[0])?.id ?? '');
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currency, rates.data, accounts.data]);

  const calc = useMemo(() => calculatePurchase(lines, overall), [lines, overall]);
  const remaining = calc.grandTotal.minus(dec(paid));
  const accountOptions = (accounts.data ?? []).filter((a) => a.active && a.currency === currency);
  const isForeign = currency !== settings.baseCurrency;

  const addProduct = (p) => {
    if (!p) return;
    setLines((ls) => [...ls, { key: uuid(), product: p, quantity: '1', unitCost: '', discount: '' }]);
  };
  const updateLine = (key, patch) => setLines((ls) => ls.map((l) => (l.key === key ? { ...l, ...patch } : l)));
  const removeLine = (key) => setLines((ls) => ls.filter((l) => l.key !== key));

  const body = () => ({
    supplierId: supplier?.id,
    supplierInvoiceNo: invoiceNo || null,
    purchaseDate: date?.format('YYYY-MM-DD'),
    currency,
    exchangeRate: isForeign ? rate || null : null,
    overallDiscount: overall || null,
    notes: notes || null,
    items: lines.map((l) => ({ productId: l.product.id, quantity: l.quantity, unitCost: l.unitCost || '0', discount: l.discount || null })),
    version,
  });

  const validate = () => {
    if (!supplier) return t('purchases.supplier') + ' — ' + t('validation.required');
    if (lines.length === 0) return t('purchases.validationItems');
    if (lines.some((l) => !(Number(l.quantity) > 0))) return t('purchases.quantity') + ' — ' + t('validation.positive');
    if (calc.lines.some((l) => l.invalid) || calc.overallInvalid) return t('errors.DISCOUNT_TOO_LARGE');
    if (isForeign && !(Number(rate) > 0)) return t('purchases.exchangeRate') + ' — ' + t('validation.required');
    return null;
  };

  /** Saves (creates or updates) the draft and returns it. */
  const saveDraft = async () => {
    const saved = isEdit || draftId ? await purchaseService.update(draftId ?? id, body()) : await purchaseService.create(body());
    setDraftId(saved.id);
    setVersion(saved.version);
    return saved;
  };
  const onSaveDraft = async () => {
    const problem = validate();
    if (problem) return setError(problem);
    setBusy(true);
    setError(null);
    try {
      const saved = await saveDraft();
      notify.success(t('purchases.draftSaved'));
      queryClient.invalidateQueries({ queryKey: ['purchases'] });
      navigate(`/purchases/${saved.id}`);
    } catch (e) {
      setError(errorMessage(t, e));
    } finally {
      setBusy(false);
    }
  };

  const onConfirm = async () => {
    setConfirmOpen(false);
    setBusy(true);
    setError(null);
    try {
      const saved = await saveDraft();
      const done = await purchaseService.confirm(saved.id, { amountPaid: paid || '0', cashAccountId: Number(paid) > 0 ? accountId : null });
      notify.success(t('purchases.confirmed', { number: done.purchaseNo }));
      ['purchases', 'products', 'inventory', 'suppliers', 'cash-accounts', 'supplier-payments'].forEach((k) => queryClient.invalidateQueries({ queryKey: [k] }));
      navigate(`/purchases/${done.id}`, { replace: true });
    } catch (e) {
      setError(errorMessage(t, e));
    } finally {
      setBusy(false);
    }
  };

  if (isEdit && (!loaded || draft.isLoading)) return <PageLoader />;
  if (isEdit && draft.data && draft.data.status !== 'DRAFT') {
    return <Alert severity="warning">{t('errors.INVALID_STATE')}</Alert>;
  }

  return (
    <>
      <PageHeader
        title={isEdit ? t('purchases.editTitle') : t('purchases.newTitle')}
        actions={
          <>
            <Button variant="outlined" startIcon={<SaveOutlined />} onClick={onSaveDraft} disabled={busy}>
              {t('purchases.saveDraft')}
            </Button>
            <Button
              variant="contained"
              startIcon={<CheckCircleOutline />}
              disabled={busy}
              onClick={() => {
                const problem = validate();
                if (problem) return setError(problem);
                setError(null);
                setConfirmOpen(true);
              }}
            >
              {t('purchases.confirm')}
            </Button>
          </>
        }
      />
      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}
      <Grid container spacing={3}>
        <Grid size={{ xs: 12, lg: 8 }}>
          <Stack spacing={3}>
            <Card>
              <CardContent>
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <SupplierPicker value={supplier} onChange={setSupplier} autoFocus={!isEdit} />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                    <TextField label={t('purchases.supplierInvoiceNo')} value={invoiceNo} onChange={(e) => setInvoiceNo(e.target.value)} slotProps={{ htmlInput: { dir: 'ltr' } }} />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                    <DatePicker label={t('purchases.purchaseDate')} value={date} onChange={setDate} maxDate={dayjs()} format="YYYY-MM-DD" slotProps={{ textField: { size: 'small', fullWidth: true } }} />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 4, md: 3 }}>
                    <TextField select label={t('purchases.currency')} value={currency} onChange={(e) => setCurrency(e.target.value)}>
                      {currencies.map((c) => (
                        <MenuItem key={c} value={c}>
                          {`${c} — ${t(`currencies.${c}`, c)}`}
                        </MenuItem>
                      ))}
                    </TextField>
                  </Grid>
                  {isForeign && (
                    <Grid size={{ xs: 12, sm: 8, md: 5 }}>
                      <RateField currency={currency} value={rate} onChange={setRate} />
                    </Grid>
                  )}
                </Grid>
              </CardContent>
            </Card>

            <Card>
              <CardContent>
                <Typography variant="subtitle1" sx={{ mb: 1.5 }}>
                  {t('purchases.items')}
                </Typography>
                <ProductPicker value={null} onChange={addProduct} label={t('purchases.addProduct')} />
                {lines.length === 0 ? (
                  <EmptyState title={t('purchases.noItems')} dense />
                ) : (
                  <Box sx={{ overflowX: 'auto', mt: 2 }}>
                    <Table size="small" sx={{ minWidth: 720 }}>
                      <TableHead>
                        <TableRow>
                          <TableCell>{t('purchases.product')}</TableCell>
                          <TableCell sx={{ width: 120 }}>{t('purchases.quantity')}</TableCell>
                          <TableCell sx={{ width: 150 }}>{t('purchases.unitCost')}</TableCell>
                          <TableCell sx={{ width: 130 }}>{t('purchases.discount')}</TableCell>
                          <TableCell align="right" sx={{ width: 150 }}>
                            {t('purchases.lineTotal')}
                          </TableCell>
                          <TableCell sx={{ width: 48 }} />
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {lines.map((l, idx) => {
                          const r = calc.lines[idx];
                          const decimals = l.product.unit?.allowDecimal ? 3 : 0;
                          return (
                            <TableRow key={l.key}>
                              <TableCell>
                                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                  <UserText>{l.product.name}</UserText>
                                </Typography>
                                <Typography variant="caption" color="text.secondary">
                                  {unitLabel(l.product.unit)}
                                </Typography>
                              </TableCell>
                              <TableCell>
                                <TextField {...numInput(l.quantity, (v) => updateLine(l.key, { quantity: v }), decimals)} error={!(Number(l.quantity) > 0)} />
                              </TableCell>
                              <TableCell>
                                <TextField {...numInput(l.unitCost, (v) => updateLine(l.key, { unitCost: v }), 4)} placeholder="0" />
                              </TableCell>
                              <TableCell>
                                <TextField {...numInput(l.discount, (v) => updateLine(l.key, { discount: v }), 2)} placeholder="0" error={r?.invalid} />
                              </TableCell>
                              <TableCell align="right">
                                <MoneyText amount={r?.lineTotal.toFixed(2)} currency={currency} fontWeight={600} />
                              </TableCell>
                              <TableCell>
                                <Tooltip title={t('common.delete')}>
                                  <IconButton size="small" onClick={() => removeLine(l.key)} aria-label={t('common.delete')}>
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
            <TextField label={t('purchases.notes')} value={notes} onChange={(e) => setNotes(e.target.value)} multiline minRows={2} />
          </Stack>
        </Grid>

        <Grid size={{ xs: 12, lg: 4 }}>
          <Stack spacing={3} sx={{ position: { lg: 'sticky' }, top: { lg: 88 } }}>
            <Card>
              <CardContent>
                <Stack spacing={1.25}>
                  <Row label={t('purchases.subtotal')} value={<MoneyText amount={calc.subtotal.toFixed(2)} currency={currency} />} />
                  <Row label={t('purchases.itemDiscounts')} value={<MoneyText amount={calc.itemDiscounts.toFixed(2)} currency={currency} />} />
                  <TextField
                    {...numInput(overall, setOverall, 2)}
                    label={t('purchases.overallDiscount')}
                    error={calc.overallInvalid}
                    slotProps={{ htmlInput: { inputMode: 'decimal', dir: 'ltr', style: { textAlign: 'end' } }, input: { endAdornment: <InputAdornment position="end">{currency}</InputAdornment> } }}
                  />
                  <Divider />
                  <Row label={<Typography variant="subtitle1">{t('purchases.grandTotal')}</Typography>} value={<MoneyText amount={calc.grandTotal.toFixed(2)} currency={currency} variant="h6" />} />
                  {isForeign && Number(rate) > 0 && (
                    <Typography variant="caption" color="text.secondary" sx={{ textAlign: 'end' }}>
                      ≈ <MoneyText amount={calc.grandTotal.times(dec(rate)).toFixed(2)} currency={settings.baseCurrency} />
                    </Typography>
                  )}
                </Stack>
              </CardContent>
            </Card>
            <Card>
              <CardContent>
                <Typography variant="subtitle1" sx={{ mb: 1.5 }}>
                  {t('purchases.payment')}
                </Typography>
                <Stack spacing={2}>
                  <TextField
                    {...numInput(paid, setPaid, 2)}
                    label={t('purchases.amountPaid')}
                    error={remaining.isNegative()}
                    slotProps={{
                      htmlInput: { inputMode: 'decimal', dir: 'ltr', style: { textAlign: 'end' } },
                      input: {
                        endAdornment: (
                          <InputAdornment position="end">
                            <Button size="small" onClick={() => setPaid(calc.grandTotal.toFixed(2))}>
                              {t('purchases.payFull')}
                            </Button>
                          </InputAdornment>
                        ),
                      },
                    }}
                  />
                  <TextField select label={t('purchases.cashAccount')} value={accountId} onChange={(e) => setAccountId(e.target.value)} disabled={!(Number(paid) > 0)}>
                    {accountOptions.map((a) => (
                      <MenuItem key={a.id} value={a.id}>
                        {a.name}
                      </MenuItem>
                    ))}
                  </TextField>
                  <Row label={t('purchases.remaining')} value={<MoneyText amount={Decimal0(remaining)} currency={currency} fontWeight={700} color={remaining.isPositive() && !remaining.isZero() ? 'warning.main' : undefined} />} />
                </Stack>
              </CardContent>
            </Card>
          </Stack>
        </Grid>
      </Grid>

      <ConfirmDialog
        open={confirmOpen}
        title={t('purchases.confirmTitle')}
        message={t('purchases.confirmHint')}
        confirmLabel={t('purchases.confirm')}
        onClose={() => setConfirmOpen(false)}
        onConfirm={onConfirm}
      />
      <LoadingOverlay open={busy} />
    </>
  );
}

const Decimal0 = (d) => (d.isNegative() ? '0.00' : d.toFixed(2));

function Row({ label, value }) {
  return (
    <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
      <Typography variant="body2" component="div" color="text.secondary">
        {label}
      </Typography>
      <Box>{value}</Box>
    </Stack>
  );
}
