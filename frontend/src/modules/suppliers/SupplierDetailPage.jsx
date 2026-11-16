import { useMemo, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  Alert, Box, Button, Card, CardContent, Grid, MenuItem, Stack, Tab, Tabs, TextField, Typography,
} from '@mui/material';
import PaymentsOutlined from '@mui/icons-material/PaymentsOutlined';
import EditOutlined from '@mui/icons-material/EditOutlined';
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
import SupplierFormDialog from './SupplierFormDialog';
import SupplierPaymentDialog from './SupplierPaymentDialog';
import { supplierService } from '../../services/supplierService';
import { purchaseService } from '../../services/purchaseService';
import { supplierPaymentService } from '../../services/supplierPaymentService';
import { errorMessage } from '../../services/apiClient';
import { useAuth } from '../../contexts/AuthContext';
import { useNotify } from '../../contexts/NotificationContext';
import { useCurrencies } from '../../hooks/useCurrency';
import { formatDate } from '../../utils/format';

function OpeningBalanceDialog({ open, supplier, onClose, onSaved }) {
  const { t } = useTranslation();
  const currencies = useCurrencies();
  const [currency, setCurrency] = useState(currencies[0]);
  const [amount, setAmount] = useState('');
  const [notes, setNotes] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await supplierService.openingBalance(supplier.id, { currency, amount, notes: notes || null });
      setAmount('');
      onSaved();
    } catch (err) {
      setError(errorMessage(t, err));
    } finally {
      setBusy(false);
    }
  };
  return (
    <FormDialog open={open} title={t('suppliers.openingTitle', { name: supplier?.name ?? '' })} onClose={onClose} onSubmit={submit} submitting={busy}>
      <Stack spacing={2} sx={{ pt: 1 }}>
        <Typography variant="body2" color="text.secondary">
          {t('suppliers.openingHint')}
        </Typography>
        {error && <Alert severity="error">{error}</Alert>}
        <Stack direction="row" spacing={2}>
          <TextField select label={t('payments.currency')} value={currency} onChange={(e) => setCurrency(e.target.value)} sx={{ width: 140 }} fullWidth={false}>
            {currencies.map((c) => (
              <MenuItem key={c} value={c}>
                {c}
              </MenuItem>
            ))}
          </TextField>
          <TextField label={t('payments.amount')} value={amount} onChange={(e) => setAmount(normalizeDecimalInput(e.target.value, 2))} slotProps={{ htmlInput: { inputMode: 'decimal', dir: 'ltr' } }} />
        </Stack>
        <TextField label={t('payments.notes')} value={notes} onChange={(e) => setNotes(e.target.value)} />
      </Stack>
    </FormDialog>
  );
}

export default function SupplierDetailPage() {
  const { id } = useParams();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const notify = useNotify();
  const { isAdmin, hasPermission } = useAuth();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState('purchases');
  const [dialog, setDialog] = useState(null);
  const supplier = useQuery({ queryKey: ['suppliers', 'detail', id], queryFn: () => supplierService.get(id) });
  const purchases = useQuery({ queryKey: ['purchases', 'bySupplier', id], queryFn: () => purchaseService.list({ supplierId: id, size: 100 }), enabled: tab === 'purchases' && hasPermission('RECORD_PURCHASES') });
  const payments = useQuery({ queryKey: ['supplier-payments', 'bySupplier', id], queryFn: () => supplierPaymentService.list({ supplierId: id, size: 100 }), enabled: tab === 'payments' && hasPermission('PAY_SUPPLIERS') });

  const purchaseColumns = useMemo(
    () => [
      { field: 'purchaseNo', headerName: t('purchases.number'), width: 160, renderCell: (p) => <Ltr>{p.value ?? t('purchases.draftLabel', { id: p.row.id })}</Ltr> },
      { field: 'purchaseDate', headerName: t('purchases.date'), width: 120, renderCell: (p) => <Ltr>{formatDate(p.value)}</Ltr> },
      { field: 'grandTotal', headerName: t('purchases.total'), width: 150, align: 'right', headerAlign: 'right', renderCell: (p) => <MoneyText amount={p.value} currency={p.row.currency} /> },
      { field: 'balanceDue', headerName: t('purchases.due'), width: 150, align: 'right', headerAlign: 'right', renderCell: (p) => <MoneyText amount={p.value} currency={p.row.currency} /> },
      { field: 'status', headerName: t('purchases.status'), width: 130, renderCell: (p) => <StatusChip status={p.value} /> },
      { field: 'paymentStatus', headerName: t('purchases.paymentStatus'), width: 150, renderCell: (p) => (p.row.status === 'CONFIRMED' ? <StatusChip status={p.value} /> : null) },
    ],
    [t],
  );
  const paymentColumns = useMemo(
    () => [
      { field: 'paymentNo', headerName: t('payments.number'), width: 160, renderCell: (p) => <Ltr>{p.value}</Ltr> },
      { field: 'businessDate', headerName: t('payments.date'), width: 120, renderCell: (p) => <Ltr>{formatDate(p.value)}</Ltr> },
      { field: 'amount', headerName: t('payments.amount'), width: 150, align: 'right', headerAlign: 'right', renderCell: (p) => <MoneyText amount={p.value} currency={p.row.currency} /> },
      { field: 'cashAccountName', headerName: t('payments.cashAccount'), width: 140 },
      { field: 'status', headerName: t('common.status'), width: 130, renderCell: (p) => <StatusChip status={p.value} /> },
    ],
    [t],
  );

  if (supplier.isLoading || !supplier.data) return <PageLoader />;
  const s = supplier.data;
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['suppliers'] });
    queryClient.invalidateQueries({ queryKey: ['purchases'] });
    queryClient.invalidateQueries({ queryKey: ['supplier-payments'] });
  };

  return (
    <>
      <PageHeader
        title={<UserText>{s.name}</UserText>}
        subtitle={[s.contactPerson, s.phone, s.address].filter(Boolean).join(' · ')}
        actions={
          <>
            {hasPermission('RECORD_PURCHASES') && (
              <Button variant="outlined" startIcon={<EditOutlined />} onClick={() => setDialog('edit')}>
                {t('common.edit')}
              </Button>
            )}
            {isAdmin && (
              <Button variant="outlined" startIcon={<AddCardOutlined />} onClick={() => setDialog('opening')}>
                {t('suppliers.openingBalance')}
              </Button>
            )}
            {hasPermission('PAY_SUPPLIERS') && (
              <Button variant="contained" startIcon={<PaymentsOutlined />} onClick={() => setDialog('pay')}>
                {t('suppliers.pay')}
              </Button>
            )}
          </>
        }
      />
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card>
            <CardContent>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                {t('suppliers.balance')}
              </Typography>
              <BalanceChips balances={s.balances} size="medium" />
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card>
            <CardContent>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                {t('common.status')}
              </Typography>
              <StatusChip status={s.active ? 'ACTIVE' : 'INACTIVE'} />
              {s.notes && (
                <Typography variant="body2" sx={{ mt: 1 }}>
                  <UserText>{s.notes}</UserText>
                </Typography>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>
      <Card>
        <Tabs value={tab} onChange={(_e, v) => setTab(v)} sx={{ px: 2, borderBottom: 1, borderColor: 'divider' }}>
          {hasPermission('RECORD_PURCHASES') && <Tab value="purchases" label={t('suppliers.purchases')} />}
          {hasPermission('PAY_SUPPLIERS') && <Tab value="payments" label={t('suppliers.payments')} />}
          <Tab value="statement" label={t('suppliers.statement')} />
        </Tabs>
        <Box sx={{ p: 2 }}>
          {tab === 'purchases' && <DataTable rows={purchases.data?.content} columns={purchaseColumns} loading={purchases.isFetching} onRowClick={(p) => navigate(`/purchases/${p.id}`)} />}
          {tab === 'payments' && <DataTable rows={payments.data?.content} columns={paymentColumns} loading={payments.isFetching} />}
          {tab === 'statement' && <StatementView queryKey={['suppliers', id, 'statement']} load={(params) => supplierService.statement(id, params)} increaseField="credit" />}
        </Box>
      </Card>

      <SupplierFormDialog
        open={dialog === 'edit'}
        supplier={s}
        onClose={() => setDialog(null)}
        onSaved={() => {
          setDialog(null);
          notify.success(t('suppliers.saved'));
          refresh();
        }}
      />
      <OpeningBalanceDialog
        open={dialog === 'opening'}
        supplier={s}
        onClose={() => setDialog(null)}
        onSaved={() => {
          setDialog(null);
          notify.success(t('suppliers.openingSaved'));
          refresh();
        }}
      />
      <SupplierPaymentDialog
        open={dialog === 'pay'}
        initialSupplier={s}
        onClose={() => setDialog(null)}
        onSaved={(p) => {
          setDialog(null);
          notify.success(t('payments.saved', { number: p.paymentNo }));
          refresh();
        }}
      />
    </>
  );
}
