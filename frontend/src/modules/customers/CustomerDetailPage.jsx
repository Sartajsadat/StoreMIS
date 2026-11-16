import { useMemo, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Alert, Box, Button, Card, CardContent, Grid, MenuItem, Stack, Tab, Tabs, TextField, Typography } from '@mui/material';
import EditOutlined from '@mui/icons-material/EditOutlined';
import PaymentsOutlined from '@mui/icons-material/PaymentsOutlined';
import AddCardOutlined from '@mui/icons-material/AddCardOutlined';
import PageHeader from '../../components/display/PageHeader';
import PageLoader from '../../components/feedback/PageLoader';
import DataTable from '../../components/data/DataTable';
import MoneyText from '../../components/display/MoneyText';
import StatusChip from '../../components/display/StatusChip';
import UserText from '../../components/display/UserText';
import Ltr from '../../components/display/Ltr';
import BalanceChips from '../../components/party/BalanceChips';
import StatementView from '../../components/party/StatementView';
import FormDialog from '../../components/form/FormDialog';
import { normalizeDecimalInput } from '../../components/form/FormMoneyField';
import CustomerFormDialog from './CustomerFormDialog';
import CustomerPaymentDialog from './CustomerPaymentDialog';
import { customerService } from '../../services/customerService';
import { salesService } from '../../services/salesService';
import { customerPaymentService } from '../../services/customerPaymentService';
import { errorMessage } from '../../services/apiClient';
import { useAuth } from '../../contexts/AuthContext';
import { useNotify } from '../../contexts/NotificationContext';
import { useCurrencies } from '../../hooks/useCurrency';
import { formatDate, formatMoney } from '../../utils/format';

function OpeningBalanceDialog({ open, customer, onClose, onSaved }) {
  const { t } = useTranslation();
  const currencies = useCurrencies();
  const [currency, setCurrency] = useState(currencies[0]);
  const [amount, setAmount] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await customerService.openingBalance(customer.id, { currency, amount });
      setAmount('');
      onSaved();
    } catch (err) {
      setError(errorMessage(t, err));
    } finally {
      setBusy(false);
    }
  };
  return (
    <FormDialog open={open} title={t('customers.openingTitle', { name: customer?.name ?? '' })} onClose={onClose} onSubmit={submit} submitting={busy}>
      <Stack spacing={2} sx={{ pt: 1 }}>
        <Typography variant="body2" color="text.secondary">
          {t('customers.openingHint')}
        </Typography>
        {error && <Alert severity="error">{error}</Alert>}
        <Stack direction="row" spacing={2}>
          <TextField select label={t('customerPayments.currency')} value={currency} onChange={(e) => setCurrency(e.target.value)} sx={{ width: 140 }} fullWidth={false}>
            {currencies.map((c) => (
              <MenuItem key={c} value={c}>
                {c}
              </MenuItem>
            ))}
          </TextField>
          <TextField label={t('customerPayments.amount')} value={amount} onChange={(e) => setAmount(normalizeDecimalInput(e.target.value, 2))} slotProps={{ htmlInput: { inputMode: 'decimal', dir: 'ltr' } }} />
        </Stack>
      </Stack>
    </FormDialog>
  );
}

export default function CustomerDetailPage() {
  const { id } = useParams();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const notify = useNotify();
  const { isAdmin, hasPermission } = useAuth();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState('sales');
  const [dialog, setDialog] = useState(null);
  const customer = useQuery({ queryKey: ['customers', 'detail', id], queryFn: () => customerService.get(id) });
  const totals = useQuery({ queryKey: ['sales', 'customer-totals', id], queryFn: () => salesService.customerTotals(id) });
  const sales = useQuery({ queryKey: ['sales', 'byCustomer', id], queryFn: () => salesService.list({ customerId: id, size: 100 }), enabled: tab === 'sales' });
  const payments = useQuery({ queryKey: ['customer-payments', 'byCustomer', id], queryFn: () => customerPaymentService.list({ customerId: id, size: 100 }), enabled: tab === 'payments' && hasPermission('RECEIVE_CUSTOMER_PAYMENTS') });

  const saleColumns = useMemo(
    () => [
      { field: 'invoiceNo', headerName: t('sales.number'), width: 160, renderCell: (p) => <Ltr>{p.value}</Ltr> },
      { field: 'businessDate', headerName: t('sales.date'), width: 120, renderCell: (p) => <Ltr>{formatDate(p.value)}</Ltr> },
      { field: 'saleType', headerName: t('sales.type'), width: 120, renderCell: (p) => t(`saleTypes.${p.value}`) },
      { field: 'grandTotal', headerName: t('sales.total'), width: 150, align: 'right', headerAlign: 'right', renderCell: (p) => <MoneyText amount={p.value} currency={p.row.currency} /> },
      { field: 'balanceDue', headerName: t('sales.due'), width: 140, align: 'right', headerAlign: 'right', renderCell: (p) => <MoneyText amount={p.value} currency={p.row.currency} /> },
      { field: 'status', headerName: t('sales.status'), width: 130, renderCell: (p) => <StatusChip status={p.value} /> },
      { field: 'paymentStatus', headerName: t('sales.payment'), width: 150, renderCell: (p) => (p.row.status === 'CONFIRMED' ? <StatusChip status={p.value} /> : null) },
    ],
    [t],
  );
  const paymentColumns = useMemo(
    () => [
      { field: 'paymentNo', headerName: t('customerPayments.number'), width: 160, renderCell: (p) => <Ltr>{p.value}</Ltr> },
      { field: 'businessDate', headerName: t('customerPayments.date'), width: 120, renderCell: (p) => <Ltr>{formatDate(p.value)}</Ltr> },
      { field: 'amount', headerName: t('customerPayments.amount'), width: 150, align: 'right', headerAlign: 'right', renderCell: (p) => <MoneyText amount={p.value} currency={p.row.currency} /> },
      { field: 'kind', headerName: t('common.details'), width: 140, renderCell: (p) => t(`customerPayments.kind.${p.value}`) },
      { field: 'status', headerName: t('common.status'), width: 130, renderCell: (p) => <StatusChip status={p.value} /> },
    ],
    [t],
  );

  if (customer.isLoading || !customer.data) return <PageLoader />;
  const c = customer.data;
  const refresh = () => ['customers', 'sales', 'customer-payments', 'cash-accounts'].forEach((k) => queryClient.invalidateQueries({ queryKey: [k] }));

  return (
    <>
      <PageHeader
        title={c.walkIn ? t('customers.walkIn') : <UserText>{c.name}</UserText>}
        subtitle={[c.phone, c.address, t(`saleTypes.${c.customerType}`)].filter(Boolean).join(' · ')}
        actions={
          !c.walkIn && (
            <>
              <Button variant="outlined" startIcon={<EditOutlined />} onClick={() => setDialog('edit')}>
                {t('common.edit')}
              </Button>
              {isAdmin && (
                <Button variant="outlined" startIcon={<AddCardOutlined />} onClick={() => setDialog('opening')}>
                  {t('customers.openingBalance')}
                </Button>
              )}
              {hasPermission('RECEIVE_CUSTOMER_PAYMENTS') && (
                <Button variant="contained" startIcon={<PaymentsOutlined />} onClick={() => setDialog('pay')}>
                  {t('customers.receive')}
                </Button>
              )}
            </>
          )
        }
      />
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card>
            <CardContent>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                {t('customers.balance')}
              </Typography>
              <BalanceChips balances={c.balances} size="medium" emptyText={t('customers.settled')} owedLabel={t('customers.owes')} creditLabel={t('customers.credit')} />
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card>
            <CardContent>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                {t('customers.totalPurchases')}
              </Typography>
              <Typography variant="h6">
                <Ltr>{Object.entries(totals.data ?? {}).map(([cur, v]) => formatMoney(v, cur)).join(' · ') || '—'}</Ltr>
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
      <Card>
        <Tabs value={tab} onChange={(_e, v) => setTab(v)} sx={{ px: 2, borderBottom: 1, borderColor: 'divider' }}>
          <Tab value="sales" label={t('customers.sales')} />
          {hasPermission('RECEIVE_CUSTOMER_PAYMENTS') && <Tab value="payments" label={t('customers.payments')} />}
          <Tab value="statement" label={t('customers.statement')} />
        </Tabs>
        <Box sx={{ p: 2 }}>
          {tab === 'sales' && <DataTable rows={sales.data?.content} columns={saleColumns} loading={sales.isFetching} onRowClick={(p) => navigate(`/sales/${p.id}`)} />}
          {tab === 'payments' && <DataTable rows={payments.data?.content} columns={paymentColumns} loading={payments.isFetching} />}
          {tab === 'statement' && <StatementView queryKey={['customers', id, 'statement']} load={(params) => customerService.statement(id, params)} increaseField="debit" />}
        </Box>
      </Card>
      <CustomerFormDialog open={dialog === 'edit'} customer={c} onClose={() => setDialog(null)} onSaved={() => (setDialog(null), notify.success(t('customers.saved')), refresh())} />
      <OpeningBalanceDialog open={dialog === 'opening'} customer={c} onClose={() => setDialog(null)} onSaved={() => (setDialog(null), notify.success(t('customers.openingSaved')), refresh())} />
      <CustomerPaymentDialog
        open={dialog === 'pay'}
        initialCustomer={c}
        onClose={() => setDialog(null)}
        onSaved={(p) => {
          setDialog(null);
          notify.success(t('customerPayments.saved', { number: p.paymentNo }));
          refresh();
        }}
      />
    </>
  );
}
