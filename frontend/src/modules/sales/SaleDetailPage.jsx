import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  Alert, Box, Button, Card, CardContent, CardHeader, Divider, FormControlLabel, Grid, Radio, RadioGroup, Stack, Table, TableBody, TableCell, TableHead, TableRow,
  TextField, Typography,
} from '@mui/material';
import PrintOutlined from '@mui/icons-material/PrintOutlined';
import BlockOutlined from '@mui/icons-material/BlockOutlined';
import PaymentsOutlined from '@mui/icons-material/PaymentsOutlined';
import PageHeader from '../../components/display/PageHeader';
import PageLoader from '../../components/feedback/PageLoader';
import FormDialog from '../../components/form/FormDialog';
import MoneyText from '../../components/display/MoneyText';
import QtyText from '../../components/display/QtyText';
import StatusChip from '../../components/display/StatusChip';
import UserText from '../../components/display/UserText';
import Ltr from '../../components/display/Ltr';
import CustomerPaymentDialog from '../customers/CustomerPaymentDialog';
import { salesService } from '../../services/salesService';
import { errorMessage } from '../../services/apiClient';
import { useAuth } from '../../contexts/AuthContext';
import { useNotify } from '../../contexts/NotificationContext';
import { useSettings } from '../../contexts/SettingsContext';
import { dec } from '../../utils/money';
import { formatDateTime, formatNumber } from '../../utils/format';

function CancelDialog({ open, sale, onClose, onDone }) {
  const { t } = useTranslation();
  const [reason, setReason] = useState('');
  const [handling, setHandling] = useState('REFUND');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const submit = async (e) => {
    e.preventDefault();
    if (reason.trim().length < 3) return setError(t('validation.minLength', { min: 3 }));
    setBusy(true);
    setError(null);
    try {
      await salesService.cancel(sale.id, { reason: reason.trim(), paymentHandling: sale.walkIn ? 'REFUND' : handling });
      onDone();
    } catch (err) {
      setError(errorMessage(t, err));
    } finally {
      setBusy(false);
    }
  };
  return (
    <FormDialog open={open} title={t('sales.cancelTitle', { number: sale?.invoiceNo ?? '' })} onClose={onClose} onSubmit={submit} submitting={busy} submitLabel={t('sales.cancel')}>
      <Stack spacing={2} sx={{ pt: 1 }}>
        <Alert severity="warning">{t('sales.cancelHint')}</Alert>
        {error && <Alert severity="error">{error}</Alert>}
        <TextField label={t('sales.reason')} value={reason} onChange={(e) => setReason(e.target.value)} multiline minRows={2} autoFocus helperText={t('dialogs.reasonHint')} />
        {Number(sale?.amountPaid ?? 0) > 0 && !sale?.walkIn && (
          <Box>
            <Typography variant="subtitle2">{t('sales.paymentHandling')}</Typography>
            <RadioGroup value={handling} onChange={(e) => setHandling(e.target.value)}>
              <FormControlLabel value="REFUND" control={<Radio />} label={t('sales.refund')} />
              <FormControlLabel value="CREDIT" control={<Radio />} label={t('sales.keepCredit')} />
            </RadioGroup>
          </Box>
        )}
      </Stack>
    </FormDialog>
  );
}

export default function SaleDetailPage() {
  const { id } = useParams();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const notify = useNotify();
  const { isAdmin, hasPermission } = useAuth();
  const { settings } = useSettings();
  const queryClient = useQueryClient();
  const [dialog, setDialog] = useState(null);
  const q = useQuery({ queryKey: ['sales', 'detail', id], queryFn: () => salesService.get(id) });
  if (q.isLoading || !q.data) return <PageLoader />;
  const s = q.data;
  const refresh = () => ['sales', 'products', 'inventory', 'customers', 'cash-accounts', 'customer-payments'].forEach((k) => queryClient.invalidateQueries({ queryKey: [k] }));
  const confirmed = s.status === 'CONFIRMED';
  const foreign = s.currency !== settings.baseCurrency;
  const profit = s.totalCostBase != null ? dec(s.grandTotal).times(dec(s.exchangeRate)).minus(dec(s.totalCostBase)) : null;

  return (
    <>
      <PageHeader
        title={<Ltr>{s.invoiceNo}</Ltr>}
        subtitle={
          <Stack direction="row" spacing={1} component="span" sx={{ alignItems: 'center' }}>
            <StatusChip status={s.status} />
            {confirmed && <StatusChip status={s.paymentStatus} />}
          </Stack>
        }
        actions={
          <>
            <Button variant="outlined" startIcon={<PrintOutlined />} onClick={() => window.open(`/print/sales/${s.id}?autoprint=1`, '_blank', 'noopener,width=900,height=900')}>
              {t('sales.print')}
            </Button>
            {confirmed && isAdmin && (
              <Button variant="outlined" color="error" startIcon={<BlockOutlined />} onClick={() => setDialog('cancel')}>
                {t('sales.cancel')}
              </Button>
            )}
            {confirmed && Number(s.balanceDue) > 0 && !s.walkIn && hasPermission('RECEIVE_CUSTOMER_PAYMENTS') && (
              <Button variant="contained" startIcon={<PaymentsOutlined />} onClick={() => setDialog('pay')}>
                {t('sales.receivePayment')}
              </Button>
            )}
          </>
        }
      />
      {s.status === 'CANCELLED' && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {t('sales.cancelReason')}: <UserText>{s.cancelReason}</UserText>
        </Alert>
      )}
      <Grid container spacing={3}>
        <Grid size={{ xs: 12, lg: 8 }}>
          <Card>
            <CardHeader title={t('sales.items')} />
            <Box sx={{ overflowX: 'auto' }}>
              <Table size="small" sx={{ minWidth: 600 }}>
                <TableHead>
                  <TableRow>
                    <TableCell>{t('sales.product')}</TableCell>
                    <TableCell align="right">{t('sales.qty')}</TableCell>
                    <TableCell align="right">{t('sales.unitPrice')}</TableCell>
                    <TableCell align="right">{t('sales.discount')}</TableCell>
                    <TableCell align="right">{t('sales.lineTotal')}</TableCell>
                    {s.totalCostBase != null && <TableCell align="right">{t('sales.cost')}</TableCell>}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {s.items.map((i) => (
                    <TableRow key={i.id}>
                      <TableCell>
                        <UserText>{i.productName}</UserText>
                        {i.productCode && (
                          <Typography variant="caption" color="text.secondary" component="div">
                            <Ltr>{i.productCode}</Ltr>
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell align="right">
                        <QtyText value={i.quantity} unit={i.unitName} />
                      </TableCell>
                      <TableCell align="right">
                        <MoneyText amount={i.unitPrice} currency={s.currency} />
                      </TableCell>
                      <TableCell align="right">{Number(i.discount) ? <MoneyText amount={i.discount} currency={s.currency} /> : ''}</TableCell>
                      <TableCell align="right">
                        <MoneyText amount={i.lineTotal} currency={s.currency} fontWeight={600} />
                      </TableCell>
                      {s.totalCostBase != null && (
                        <TableCell align="right">
                          <MoneyText amount={i.unitCostBase} currency={settings.baseCurrency} />
                        </TableCell>
                      )}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>
          </Card>
          <Card sx={{ mt: 3 }}>
            <CardHeader title={t('sales.payments')} />
            <CardContent>
              {s.payments.length === 0 ? (
                <Typography color="text.secondary">{t('sales.noPayments')}</Typography>
              ) : (
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>{t('customerPayments.number')}</TableCell>
                      <TableCell>{t('customerPayments.date')}</TableCell>
                      <TableCell align="right">{t('customerPayments.amount')}</TableCell>
                      <TableCell align="right">{t('customerPayments.allocated')}</TableCell>
                      <TableCell>{t('customerPayments.account')}</TableCell>
                      <TableCell>{t('common.status')}</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {s.payments.map((p) => (
                      <TableRow key={p.paymentId}>
                        <TableCell>
                          <Ltr>{p.paymentNo}</Ltr>
                        </TableCell>
                        <TableCell>
                          <Ltr>{formatDateTime(p.paymentDate)}</Ltr>
                        </TableCell>
                        <TableCell align="right">
                          <MoneyText amount={p.paidAmount} currency={p.currency} />
                        </TableCell>
                        <TableCell align="right">
                          <MoneyText amount={p.appliedAmount} currency={s.currency} />
                        </TableCell>
                        <TableCell>{p.cashAccountName}</TableCell>
                        <TableCell>
                          <StatusChip status={p.status} />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, lg: 4 }}>
          <Card>
            <CardContent>
              <Stack spacing={1.25}>
                <Info
                  label={t('sales.customer')}
                  value={s.walkIn ? t('customers.walkIn') : <Button size="small" onClick={() => navigate(`/customers/${s.customerId}`)}><UserText>{s.customerName}</UserText></Button>}
                />
                <Info label={t('sales.date')} value={<Ltr>{formatDateTime(s.saleDate)}</Ltr>} />
                <Info label={t('sales.type')} value={t(`saleTypes.${s.saleType}`)} />
                {foreign && <Info label={t('sales.exchangeRate')} value={<Ltr>{`1 ${s.currency} = ${formatNumber(s.exchangeRate, { maxDecimals: 6 })} ${settings.baseCurrency}`}</Ltr>} />}
                <Info label={t('sales.cashier')} value={<UserText>{s.createdByName}</UserText>} />
                <Divider />
                <Info label={t('sales.subtotal')} value={<MoneyText amount={s.subtotal} currency={s.currency} />} />
                {Number(s.itemDiscountTotal) > 0 && <Info label={t('sales.itemDiscounts')} value={<MoneyText amount={s.itemDiscountTotal} currency={s.currency} />} />}
                {Number(s.overallDiscount) > 0 && <Info label={t('sales.overallDiscount')} value={<MoneyText amount={s.overallDiscount} currency={s.currency} />} />}
                <Info label={<b>{t('sales.grandTotal')}</b>} value={<MoneyText amount={s.grandTotal} currency={s.currency} variant="h6" />} />
                <Info label={t('sales.paid')} value={<MoneyText amount={s.amountPaid} currency={s.currency} />} />
                <Info label={t('sales.due')} value={<MoneyText amount={s.balanceDue} currency={s.currency} fontWeight={700} />} />
                {profit && confirmed && <Info label={t('sales.grossProfit')} value={<MoneyText amount={profit.toFixed(2)} currency={settings.baseCurrency} />} />}
                {s.notes && (
                  <Typography variant="body2" color="text.secondary">
                    <UserText>{s.notes}</UserText>
                  </Typography>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
      <CancelDialog open={dialog === 'cancel'} sale={s} onClose={() => setDialog(null)} onDone={() => (setDialog(null), notify.success(t('sales.cancelled')), refresh())} />
      <CustomerPaymentDialog
        open={dialog === 'pay'}
        initialCustomer={{ id: s.customerId, name: s.customerName, walkIn: s.walkIn }}
        initialSaleId={s.id}
        onClose={() => setDialog(null)}
        onSaved={(p) => (setDialog(null), notify.success(t('customerPayments.saved', { number: p.paymentNo })), refresh())}
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
