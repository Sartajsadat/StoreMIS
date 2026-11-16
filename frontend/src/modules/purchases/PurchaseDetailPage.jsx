import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  Alert, Box, Button, Card, CardContent, CardHeader, Divider, FormControlLabel, Grid, Radio, RadioGroup, Stack, Table, TableBody, TableCell, TableHead, TableRow,
  TextField, Typography,
} from '@mui/material';
import EditOutlined from '@mui/icons-material/EditOutlined';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import BlockOutlined from '@mui/icons-material/BlockOutlined';
import PaymentsOutlined from '@mui/icons-material/PaymentsOutlined';
import CheckCircleOutline from '@mui/icons-material/CheckCircleOutline';
import AssignmentReturnOutlined from '@mui/icons-material/AssignmentReturnOutlined';
import PageHeader from '../../components/display/PageHeader';
import PageLoader from '../../components/feedback/PageLoader';
import ConfirmDialog from '../../components/feedback/ConfirmDialog';
import FormDialog from '../../components/form/FormDialog';
import MoneyText from '../../components/display/MoneyText';
import QtyText from '../../components/display/QtyText';
import StatusChip from '../../components/display/StatusChip';
import UserText from '../../components/display/UserText';
import Ltr from '../../components/display/Ltr';
import SupplierPaymentDialog from '../suppliers/SupplierPaymentDialog';
import ReturnDialog from '../returns/ReturnDialog';
import DocumentReturns from '../returns/DocumentReturns';
import { purchaseService } from '../../services/purchaseService';
import { errorMessage } from '../../services/apiClient';
import { useAuth } from '../../contexts/AuthContext';
import { useNotify } from '../../contexts/NotificationContext';
import { useSettings } from '../../contexts/SettingsContext';
import { formatDate, formatDateTime, formatNumber } from '../../utils/format';

function CancelDialog({ open, purchase, onClose, onDone }) {
  const { t } = useTranslation();
  const [reason, setReason] = useState('');
  const [handling, setHandling] = useState('REFUND');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const hasPayments = Number(purchase?.amountPaid ?? 0) > 0;
  const submit = async (e) => {
    e.preventDefault();
    if (reason.trim().length < 3) return setError(t('validation.minLength', { min: 3 }));
    setBusy(true);
    setError(null);
    try {
      await purchaseService.cancel(purchase.id, { reason: reason.trim(), paymentHandling: handling });
      onDone();
    } catch (err) {
      setError(errorMessage(t, err));
    } finally {
      setBusy(false);
    }
  };
  return (
    <FormDialog open={open} title={t('purchases.cancelTitle', { number: purchase?.purchaseNo ?? '' })} onClose={onClose} onSubmit={submit} submitting={busy} submitLabel={t('purchases.cancel')}>
      <Stack spacing={2} sx={{ pt: 1 }}>
        <Alert severity="warning">{t('purchases.cancelHint')}</Alert>
        {error && <Alert severity="error">{error}</Alert>}
        <TextField label={t('purchases.cancelReason')} value={reason} onChange={(e) => setReason(e.target.value)} multiline minRows={2} autoFocus helperText={t('dialogs.reasonHint')} />
        {hasPayments && (
          <Box>
            <Typography variant="subtitle2">{t('purchases.paymentHandling')}</Typography>
            <RadioGroup value={handling} onChange={(e) => setHandling(e.target.value)}>
              <FormControlLabel value="REFUND" control={<Radio />} label={t('purchases.refund')} />
              <FormControlLabel value="CREDIT" control={<Radio />} label={t('purchases.keepCredit')} />
            </RadioGroup>
          </Box>
        )}
      </Stack>
    </FormDialog>
  );
}

export default function PurchaseDetailPage() {
  const { id } = useParams();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const notify = useNotify();
  const { isAdmin, hasPermission } = useAuth();
  const { settings } = useSettings();
  const queryClient = useQueryClient();
  const [dialog, setDialog] = useState(null);
  const q = useQuery({ queryKey: ['purchases', 'detail', id], queryFn: () => purchaseService.get(id) });
  if (q.isLoading || !q.data) return <PageLoader />;
  const p = q.data;
  const refresh = () => ['purchases', 'products', 'inventory', 'suppliers', 'cash-accounts', 'supplier-payments', 'returns'].forEach((k) => queryClient.invalidateQueries({ queryKey: [k] }));
  const isDraft = p.status === 'DRAFT';
  const isConfirmed = p.status === 'CONFIRMED';
  const foreign = p.currency !== settings.baseCurrency;

  return (
    <>
      <PageHeader
        title={<Ltr>{p.purchaseNo ?? t('purchases.draftLabel', { id: p.id })}</Ltr>}
        subtitle={
          <Stack direction="row" spacing={1} component="span" sx={{ alignItems: 'center' }}>
            <StatusChip status={p.status} />
            {isConfirmed && <StatusChip status={p.paymentStatus} />}
          </Stack>
        }
        actions={
          <>
            {isDraft && hasPermission('RECORD_PURCHASES') && (
              <>
                <Button variant="outlined" color="error" startIcon={<DeleteOutline />} onClick={() => setDialog('discard')}>
                  {t('purchases.discardDraft')}
                </Button>
                <Button variant="contained" startIcon={<EditOutlined />} onClick={() => navigate(`/purchases/${p.id}/edit`)}>
                  {t('purchases.edit')}
                </Button>
              </>
            )}
            {isConfirmed && isAdmin && p.items.some((i) => Number(i.returnedQuantity) < Number(i.quantity)) && (
              <Button variant="outlined" startIcon={<AssignmentReturnOutlined />} onClick={() => setDialog('return')}>
                {t('returns.purchase.new')}
              </Button>
            )}
            {isConfirmed && isAdmin && Number(p.returnedAmount) === 0 && (
              <Button variant="outlined" color="error" startIcon={<BlockOutlined />} onClick={() => setDialog('cancel')}>
                {t('purchases.cancel')}
              </Button>
            )}
            {isConfirmed && Number(p.balanceDue) > 0 && hasPermission('PAY_SUPPLIERS') && (
              <Button variant="contained" startIcon={<PaymentsOutlined />} onClick={() => setDialog('pay')}>
                {t('purchases.payNow')}
              </Button>
            )}
          </>
        }
      />
      {p.status === 'CANCELLED' && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {t('purchases.cancelReason')}: <UserText>{p.cancelReason}</UserText>
        </Alert>
      )}
      {isDraft && (
        <Alert severity="info" icon={<CheckCircleOutline />} sx={{ mb: 2 }}>
          {t('purchases.confirmHint')}
        </Alert>
      )}
      <Grid container spacing={3}>
        <Grid size={{ xs: 12, lg: 8 }}>
          <Card>
            <CardHeader title={t('purchases.items')} />
            <Box sx={{ overflowX: 'auto' }}>
              <Table size="small" sx={{ minWidth: 640 }}>
                <TableHead>
                  <TableRow>
                    <TableCell>{t('purchases.product')}</TableCell>
                    <TableCell align="right">{t('purchases.quantity')}</TableCell>
                    <TableCell align="right">{t('purchases.unitCost')}</TableCell>
                    <TableCell align="right">{t('purchases.discount')}</TableCell>
                    <TableCell align="right">{t('purchases.lineTotal')}</TableCell>
                    {foreign || Number(p.overallDiscount) > 0 ? <TableCell align="right">{t('purchases.landedCost', { base: settings.baseCurrency })}</TableCell> : null}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {p.items.map((i) => (
                    <TableRow key={i.id}>
                      <TableCell>
                        <UserText>{i.productName}</UserText>
                      </TableCell>
                      <TableCell align="right">
                        <QtyText value={i.quantity} unit={i.unitName} />
                        {Number(i.returnedQuantity) > 0 && (
                          <Typography variant="caption" color="warning.main" component="div">
                            {t('returns.returnedQty')}: <QtyText value={i.returnedQuantity} unit={i.unitName} />
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell align="right">
                        <Ltr>{formatNumber(i.unitCost, { maxDecimals: 4 })}</Ltr>
                      </TableCell>
                      <TableCell align="right">{Number(i.discount) ? <MoneyText amount={i.discount} currency={p.currency} /> : ''}</TableCell>
                      <TableCell align="right">
                        <MoneyText amount={i.lineTotal} currency={p.currency} fontWeight={600} />
                      </TableCell>
                      {foreign || Number(p.overallDiscount) > 0 ? (
                        <TableCell align="right">
                          <MoneyText amount={i.landedUnitCostBase} currency={settings.baseCurrency} />
                        </TableCell>
                      ) : null}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>
          </Card>
          {!isDraft && (
            <Card sx={{ mt: 3 }}>
              <CardHeader title={t('purchases.paymentsTitle')} />
              <CardContent>
                {p.payments.length === 0 ? (
                  <Typography color="text.secondary">{t('purchases.noPayments')}</Typography>
                ) : (
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>{t('payments.number')}</TableCell>
                        <TableCell>{t('payments.date')}</TableCell>
                        <TableCell align="right">{t('payments.amount')}</TableCell>
                        <TableCell align="right">{t('payments.allocated')}</TableCell>
                        <TableCell>{t('common.status')}</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {p.payments.map((pay) => (
                        <TableRow key={pay.paymentId}>
                          <TableCell>
                            <Ltr>{pay.paymentNo}</Ltr>
                          </TableCell>
                          <TableCell>
                            <Ltr>{formatDateTime(pay.paymentDate)}</Ltr>
                          </TableCell>
                          <TableCell align="right">
                            <MoneyText amount={pay.paidAmount} currency={pay.currency} />
                          </TableCell>
                          <TableCell align="right">
                            <MoneyText amount={pay.appliedAmount} currency={p.currency} />
                          </TableCell>
                          <TableCell>
                            <StatusChip status={pay.status} />
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </CardContent>
            </Card>
          )}
          {!isDraft && <DocumentReturns kind="purchase" documentId={p.id} />}
        </Grid>
        <Grid size={{ xs: 12, lg: 4 }}>
          <Card>
            <CardContent>
              <Stack spacing={1.25}>
                <Info label={t('purchases.supplier')} value={<Button size="small" onClick={() => navigate(`/suppliers/${p.supplierId}`)}><UserText>{p.supplierName}</UserText></Button>} />
                <Info label={t('purchases.supplierInvoiceNo')} value={<Ltr>{p.supplierInvoiceNo ?? '—'}</Ltr>} />
                <Info label={t('purchases.purchaseDate')} value={<Ltr>{formatDate(p.purchaseDate)}</Ltr>} />
                <Info label={t('purchases.currency')} value={p.currency} />
                {foreign && <Info label={t('purchases.exchangeRate')} value={<Ltr>{`1 ${p.currency} = ${formatNumber(p.exchangeRate, { maxDecimals: 6 })} ${settings.baseCurrency}`}</Ltr>} />}
                <Info label={t('purchases.createdBy')} value={<UserText>{p.createdByName}</UserText>} />
                <Divider />
                <Info label={t('purchases.subtotal')} value={<MoneyText amount={p.subtotal} currency={p.currency} />} />
                {Number(p.itemDiscountTotal) > 0 && <Info label={t('purchases.itemDiscounts')} value={<MoneyText amount={p.itemDiscountTotal} currency={p.currency} />} />}
                {Number(p.overallDiscount) > 0 && <Info label={t('purchases.overallDiscount')} value={<MoneyText amount={p.overallDiscount} currency={p.currency} />} />}
                <Info label={<b>{t('purchases.grandTotal')}</b>} value={<MoneyText amount={p.grandTotal} currency={p.currency} variant="h6" />} />
                {!isDraft && (
                  <>
                    <Info label={t('purchases.paid')} value={<MoneyText amount={p.amountPaid} currency={p.currency} />} />
                    {Number(p.returnedAmount) > 0 && <Info label={t('returns.alreadyReturned')} value={<MoneyText amount={p.returnedAmount} currency={p.currency} />} />}
                    <Info label={t('purchases.due')} value={<MoneyText amount={p.balanceDue} currency={p.currency} fontWeight={700} />} />
                  </>
                )}
                {p.notes && (
                  <Typography variant="body2" color="text.secondary">
                    <UserText>{p.notes}</UserText>
                  </Typography>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <ConfirmDialog
        open={dialog === 'discard'}
        message={t('purchases.discardConfirm')}
        color="error"
        confirmLabel={t('purchases.discardDraft')}
        onClose={() => setDialog(null)}
        onConfirm={async () => {
          try {
            await purchaseService.remove(p.id);
            notify.success(t('purchases.discarded'));
            refresh();
            navigate('/purchases', { replace: true });
          } catch (e) {
            notify.error(errorMessage(t, e));
          }
        }}
      />
      <CancelDialog
        open={dialog === 'cancel'}
        purchase={p}
        onClose={() => setDialog(null)}
        onDone={() => {
          setDialog(null);
          notify.success(t('purchases.cancelled'));
          refresh();
        }}
      />
      <ReturnDialog
        open={dialog === 'return'}
        kind="purchase"
        documentId={p.id}
        onClose={() => setDialog(null)}
        onSaved={(r) => {
          setDialog(null);
          notify.success(t('returns.saved', { number: r.returnNo }));
          refresh();
        }}
      />
      <SupplierPaymentDialog
        open={dialog === 'pay'}
        initialSupplier={{ id: p.supplierId, name: p.supplierName }}
        initialPurchaseId={p.id}
        onClose={() => setDialog(null)}
        onSaved={(pay) => {
          setDialog(null);
          notify.success(t('payments.saved', { number: pay.paymentNo }));
          refresh();
        }}
      />
    </>
  );
}

function Info({ label, value }) {
  return (
    <Stack direction="row" spacing={2} sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
      <Typography variant="body2" color="text.secondary" component="div">
        {label}
      </Typography>
      <Box sx={{ textAlign: 'end' }}>{value}</Box>
    </Stack>
  );
}
