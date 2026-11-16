import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Alert, Box, Button, FormControlLabel, Grid, MenuItem, Radio, RadioGroup, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import SearchOutlined from '@mui/icons-material/SearchOutlined';
import FormDialog from '../../components/form/FormDialog';
import MoneyText from '../../components/display/MoneyText';
import QtyText from '../../components/display/QtyText';
import UserText from '../../components/display/UserText';
import Ltr from '../../components/display/Ltr';
import { normalizeDecimalInput } from '../../components/form/FormMoneyField';
import { returnsService } from '../../services/returnsService';
import { errorMessage } from '../../services/apiClient';
import { useCashAccounts } from '../../hooks/useCurrency';
import { dec } from '../../utils/money';
import { formatDate } from '../../utils/format';
import { uuid } from '../../utils/uuid';
import { returnTotal, settlement } from './returnMath';

/**
 * Sales return (kind 'sales') or purchase return (kind 'purchase') against one original document.
 * Pick the document by number (or open it from the document page), enter what comes back, then settle.
 */
export default function ReturnDialog({ open, kind, documentId, onClose, onSaved }) {
  const { t } = useTranslation();
  const accounts = useCashAccounts();
  const [docNo, setDocNo] = useState('');
  const [doc, setDoc] = useState(null);
  const [lines, setLines] = useState({});
  const [reason, setReason] = useState('');
  const [mode, setMode] = useState('REFUND');
  const [accountId, setAccountId] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(false);
  const [requestId, setRequestId] = useState(uuid);
  const sales = kind === 'sales';
  const ns = sales ? 'returns.sales' : 'returns.purchase';

  const load = async (fn) => {
    setLoading(true);
    setError(null);
    try {
      const d = await fn();
      setDoc(d);
      setDocNo(d.documentNo);
      setLines({});
    } catch (e) {
      setDoc(null);
      setError(errorMessage(t, e));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!open) return;
    setDoc(null);
    setDocNo('');
    setLines({});
    setReason('');
    setMode('REFUND');
    setError(null);
    setRequestId(uuid());
    if (documentId) load(() => returnsService.returnable(kind, documentId));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, documentId, kind]);

  const accountOptions = useMemo(() => (accounts.data ?? []).filter((a) => a.active && a.currency === doc?.currency), [accounts.data, doc?.currency]);
  useEffect(() => {
    setAccountId((accountOptions.find((a) => a.isDefault) ?? accountOptions[0])?.id ?? '');
  }, [accountOptions]);

  const total = returnTotal(doc, lines);
  const { reduction, rest } = settlement(total, doc?.balanceDue);
  const refund = doc?.walkIn || mode === 'REFUND';
  const chosen = doc ? doc.items.filter((i) => Number(lines[i.itemId]?.qty) > 0) : [];
  const overLimit = doc ? doc.items.some((i) => Number(lines[i.itemId]?.qty || 0) > Number(i.returnableQuantity)) : false;
  const setLine = (id, patch) => setLines((l) => ({ ...l, [id]: { condition: 'SELLABLE', ...l[id], ...patch } }));

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    if (!doc || chosen.length === 0 || overLimit) return setError(t('returns.chooseItems'));
    if (reason.trim().length < 3) return setError(t('validation.minLength', { min: 3 }));
    if (rest.gt(0) && refund && !accountId) return setError(t('errors.VALIDATION_FAILED'));
    setBusy(true);
    try {
      const saved = await returnsService.create(kind, {
        clientRequestId: requestId,
        documentId: doc.documentId,
        reason: reason.trim(),
        settlement: rest.gt(0) ? (refund ? 'REFUND' : 'CREDIT') : null,
        refundCashAccountId: rest.gt(0) && refund ? accountId : null,
        items: chosen.map((i) => ({
          itemId: i.itemId,
          quantity: lines[i.itemId].qty,
          condition: sales ? lines[i.itemId].condition ?? 'SELLABLE' : null,
          reason: lines[i.itemId].reason?.trim() || null,
        })),
      });
      onSaved(saved);
    } catch (err) {
      setError(errorMessage(t, err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <FormDialog open={open} title={t(`${ns}.new`)} onClose={onClose} onSubmit={submit} submitting={busy} maxWidth="lg" submitLabel={t(`${ns}.save`)}>
      <Stack spacing={2} sx={{ pt: 1 }}>
        {error && <Alert severity="error">{error}</Alert>}
        {!documentId && (
          <Stack direction="row" spacing={1}>
            <TextField
              size="small"
              label={t(`${ns}.documentNo`)}
              value={docNo}
              onChange={(e) => setDocNo(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  if (docNo.trim()) load(() => returnsService.lookup(kind, docNo.trim()));
                }
              }}
              slotProps={{ htmlInput: { dir: 'ltr' } }}
              sx={{ maxWidth: 280 }}
              autoFocus
            />
            <Button variant="outlined" startIcon={<SearchOutlined />} loading={loading} disabled={!docNo.trim()} onClick={() => load(() => returnsService.lookup(kind, docNo.trim()))}>
              {t('returns.find')}
            </Button>
          </Stack>
        )}
        {doc && (
          <>
            <Grid container spacing={2}>
              <Info label={t(`${ns}.documentNo`)} value={<Ltr>{doc.documentNo}</Ltr>} />
              <Info label={t(`${ns}.party`)} value={doc.walkIn ? t('customers.walkIn') : <UserText>{doc.partyName}</UserText>} />
              <Info label={t('returns.date')} value={<Ltr>{formatDate(doc.businessDate)}</Ltr>} />
              <Info label={t('returns.docTotal')} value={<MoneyText amount={doc.grandTotal} currency={doc.currency} />} />
              <Info label={t('returns.stillOwed')} value={<MoneyText amount={doc.balanceDue} currency={doc.currency} fontWeight={600} />} />
              {Number(doc.returnedAmount) > 0 && <Info label={t('returns.alreadyReturned')} value={<MoneyText amount={doc.returnedAmount} currency={doc.currency} />} />}
            </Grid>
            {doc.status !== 'CONFIRMED' && <Alert severity="warning">{t('returns.notReturnable')}</Alert>}
            <Box sx={{ overflowX: 'auto' }}>
              <Table size="small" sx={{ minWidth: 760 }}>
                <TableHead>
                  <TableRow>
                    <TableCell>{t('sales.product')}</TableCell>
                    <TableCell align="right">{t('returns.sold')}</TableCell>
                    <TableCell align="right">{t('returns.returnable')}</TableCell>
                    <TableCell align="right">{t('returns.netPrice')}</TableCell>
                    <TableCell sx={{ width: 130 }}>{t('returns.qty')}</TableCell>
                    {sales && <TableCell sx={{ width: 150 }}>{t('returns.condition')}</TableCell>}
                    <TableCell>{t('returns.lineReason')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {doc.items.map((i) => {
                    const l = lines[i.itemId] ?? {};
                    const tooMany = Number(l.qty || 0) > Number(i.returnableQuantity);
                    const none = Number(i.returnableQuantity) <= 0;
                    return (
                      <TableRow key={i.itemId} sx={{ opacity: none ? 0.5 : 1 }}>
                        <TableCell>
                          <UserText>{i.productName}</UserText>
                          {!sales && i.stockOnHand != null && (
                            <Typography variant="caption" color="text.secondary" component="div">
                              {t('returns.inStock')}: <QtyText value={i.stockOnHand} unit={i.unitName} />
                            </Typography>
                          )}
                        </TableCell>
                        <TableCell align="right">
                          <QtyText value={i.quantity} unit={i.unitName} />
                        </TableCell>
                        <TableCell align="right">
                          <QtyText value={i.returnableQuantity} unit={i.unitName} />
                        </TableCell>
                        <TableCell align="right">
                          <MoneyText amount={dec(i.unitNetPrice).toFixed(2)} currency={doc.currency} />
                        </TableCell>
                        <TableCell>
                          <TextField
                            size="small"
                            disabled={none}
                            value={l.qty ?? ''}
                            error={tooMany}
                            onChange={(e) => setLine(i.itemId, { qty: normalizeDecimalInput(e.target.value, i.allowDecimal ? 3 : 0) })}
                            slotProps={{ htmlInput: { inputMode: 'decimal', dir: 'ltr', style: { textAlign: 'end' }, 'aria-label': `${t('returns.qty')} ${i.productName}` } }}
                          />
                        </TableCell>
                        {sales && (
                          <TableCell>
                            <TextField select size="small" disabled={none} value={l.condition ?? 'SELLABLE'} onChange={(e) => setLine(i.itemId, { condition: e.target.value })}>
                              <MenuItem value="SELLABLE">{t('returns.conditions.SELLABLE')}</MenuItem>
                              <MenuItem value="DAMAGED">{t('returns.conditions.DAMAGED')}</MenuItem>
                            </TextField>
                          </TableCell>
                        )}
                        <TableCell>
                          <TextField size="small" disabled={none} value={l.reason ?? ''} onChange={(e) => setLine(i.itemId, { reason: e.target.value })} />
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </Box>
            <Grid container spacing={3}>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField label={t('returns.reason')} value={reason} onChange={(e) => setReason(e.target.value)} multiline minRows={3} helperText={t('dialogs.reasonHint')} />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <Stack spacing={1}>
                  <Row label={<b>{t('returns.total')}</b>} value={<MoneyText amount={total.toFixed(2)} currency={doc.currency} variant="h6" />} />
                  <Row label={t(`${ns}.reduction`)} value={<MoneyText amount={reduction.toFixed(2)} currency={doc.currency} />} />
                  {rest.gt(0) && (
                    <>
                      <Row label={t(`${ns}.rest`)} value={<MoneyText amount={rest.toFixed(2)} currency={doc.currency} fontWeight={700} />} />
                      {doc.walkIn ? (
                        <Alert severity="info">{t('returns.walkInRefund')}</Alert>
                      ) : (
                        <RadioGroup value={mode} onChange={(e) => setMode(e.target.value)}>
                          <FormControlLabel value="REFUND" control={<Radio />} label={t(`${ns}.refund`)} />
                          <FormControlLabel value="CREDIT" control={<Radio />} label={t(`${ns}.credit`)} />
                        </RadioGroup>
                      )}
                      {refund && (
                        <TextField select label={t(`${ns}.account`)} value={accountId} onChange={(e) => setAccountId(e.target.value)} error={accountOptions.length === 0}>
                          {accountOptions.map((a) => (
                            <MenuItem key={a.id} value={a.id}>
                              {a.name}
                            </MenuItem>
                          ))}
                        </TextField>
                      )}
                    </>
                  )}
                </Stack>
              </Grid>
            </Grid>
          </>
        )}
      </Stack>
    </FormDialog>
  );
}

function Info({ label, value }) {
  return (
    <Grid size={{ xs: 6, md: 2 }}>
      <Typography variant="caption" color="text.secondary" component="div">
        {label}
      </Typography>
      <Box sx={{ fontWeight: 500 }}>{value}</Box>
    </Grid>
  );
}

function Row({ label, value }) {
  return (
    <Stack direction="row" spacing={2} sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
      <Typography variant="body2" color="text.secondary" component="div">
        {label}
      </Typography>
      <Box sx={{ textAlign: 'end' }}>{value}</Box>
    </Stack>
  );
}
