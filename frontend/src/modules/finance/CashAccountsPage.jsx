import { useMemo, useState } from 'react';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Alert, Box, Button, Card, CardHeader, Chip, Grid, Stack, TextField, Typography } from '@mui/material';
import AccountBalanceWalletOutlined from '@mui/icons-material/AccountBalanceWalletOutlined';
import AddCircleOutline from '@mui/icons-material/AddCircleOutline';
import RemoveCircleOutline from '@mui/icons-material/RemoveCircleOutline';
import SwapHorizOutlined from '@mui/icons-material/SwapHorizOutlined';
import CurrencyExchangeOutlined from '@mui/icons-material/CurrencyExchangeOutlined';
import DataTable from '../../components/data/DataTable';
import UserText from '../../components/display/UserText';
import Ltr from '../../components/display/Ltr';
import CashOperationDialog from './CashOperationDialog';
import { formatDate, formatNumber } from '../../utils/format';
import PageHeader from '../../components/display/PageHeader';
import MoneyText from '../../components/display/MoneyText';
import FormDialog from '../../components/form/FormDialog';
import { normalizeDecimalInput } from '../../components/form/FormMoneyField';
import CashTransactionsTable from './CashTransactionsTable';
import { cashService } from '../../services/cashService';
import { errorMessage } from '../../services/apiClient';
import { useCashAccounts } from '../../hooks/useCurrency';
import { useNotify } from '../../contexts/NotificationContext';

export default function CashAccountsPage() {
  const { t } = useTranslation();
  const notify = useNotify();
  const queryClient = useQueryClient();
  const accounts = useCashAccounts();
  const [target, setTarget] = useState(null);
  const [amount, setAmount] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [operation, setOperation] = useState(null);
  const refresh = () => ['cash-accounts', 'cash-transactions', 'cash-transfers', 'cash-closing'].forEach((k) => queryClient.invalidateQueries({ queryKey: [k] }));

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await cashService.openingBalance(target.id, amount);
      notify.success(t('cash.openingSaved'));
      setTarget(null);
      refresh();
    } catch (err) {
      setError(errorMessage(t, err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader
        title={t('cash.accountsTitle')}
        subtitle={t('cash.accountsSubtitle')}
        actions={
          <>
            <Button variant="outlined" startIcon={<AddCircleOutline />} onClick={() => setOperation('DEPOSIT')}>
              {t('cashOps.actions.DEPOSIT')}
            </Button>
            <Button variant="outlined" startIcon={<RemoveCircleOutline />} onClick={() => setOperation('WITHDRAWAL')}>
              {t('cashOps.actions.WITHDRAWAL')}
            </Button>
            <Button variant="outlined" startIcon={<SwapHorizOutlined />} onClick={() => setOperation('TRANSFER')}>
              {t('cashOps.actions.TRANSFER')}
            </Button>
            <Button variant="contained" startIcon={<CurrencyExchangeOutlined />} onClick={() => setOperation('EXCHANGE')}>
              {t('cashOps.actions.EXCHANGE')}
            </Button>
          </>
        }
      />
      <Grid container spacing={3} sx={{ mb: 3 }}>
        {(accounts.data ?? []).map((a) => (
          <Grid key={a.id} size={{ xs: 12, sm: 6, lg: 4 }}>
            <Card sx={{ p: 2.5 }}>
              <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                    <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 500 }}>
                      {a.name}
                    </Typography>
                    {a.isDefault && <Chip size="small" label={t('cash.default')} />}
                  </Stack>
                  <Typography variant="h5" sx={{ mt: 1 }}>
                    <MoneyText amount={a.balance} currency={a.currency} alwaysDecimals />
                  </Typography>
                </div>
                <AccountBalanceWalletOutlined color="primary" />
              </Stack>
              {!a.hasTransactions && (
                <Button
                  size="small"
                  sx={{ mt: 1.5 }}
                  onClick={() => {
                    setTarget(a);
                    setAmount('');
                    setError(null);
                  }}
                >
                  {t('cash.setOpening')}
                </Button>
              )}
            </Card>
          </Grid>
        ))}
      </Grid>
      <Card>
        <CardHeader title={t('cash.recent')} />
        <CashTransactionsTable />
      </Card>
      <Card sx={{ mt: 3 }}>
        <CardHeader title={t('cashOps.transfersTitle')} />
        <TransfersTable />
      </Card>
      <CashOperationDialog
        mode={operation}
        onClose={() => setOperation(null)}
        onSaved={(r) => {
          notify.success(t('cashOps.saved', { number: r.transferNo ?? r.txnNo }));
          setOperation(null);
          refresh();
        }}
      />
      <FormDialog open={Boolean(target)} title={t('cash.openingTitle', { account: target?.name ?? '' })} onClose={() => setTarget(null)} onSubmit={submit} submitting={busy}>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Typography variant="body2" color="text.secondary">
            {t('cash.openingHint')}
          </Typography>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField
            autoFocus
            label={t('payments.amount')}
            value={amount}
            onChange={(e) => setAmount(normalizeDecimalInput(e.target.value, 2))}
            slotProps={{ htmlInput: { inputMode: 'decimal', dir: 'ltr', style: { textAlign: 'end' } }, input: { endAdornment: target?.currency } }}
          />
        </Stack>
      </FormDialog>
    </>
  );
}

function TransfersTable() {
  const { t } = useTranslation();
  const [pagination, setPagination] = useState({ page: 0, pageSize: 10 });
  const params = { page: pagination.page, size: pagination.pageSize };
  const list = useQuery({ queryKey: ['cash-transfers', params], queryFn: () => cashService.transfers(params), placeholderData: keepPreviousData });
  const columns = useMemo(
    () => [
      { field: 'businessDate', headerName: t('inventory.date'), width: 120, renderCell: (p) => <Ltr>{formatDate(p.value)}</Ltr> },
      { field: 'transferNo', headerName: t('cash.reference'), width: 150, renderCell: (p) => <Ltr>{p.value}</Ltr> },
      { field: 'type', headerName: t('cash.type'), width: 130, renderCell: (p) => t(`cashOps.actions.${p.value}`) },
      { field: 'fromAccountName', headerName: t('cashOps.from'), width: 130 },
      { field: 'fromAmount', headerName: t('cashOps.give'), width: 150, align: 'right', headerAlign: 'right', renderCell: (p) => <MoneyText amount={p.value} currency={p.row.fromCurrency} /> },
      { field: 'toAccountName', headerName: t('cashOps.to'), width: 130 },
      { field: 'toAmount', headerName: t('cashOps.receive'), width: 150, align: 'right', headerAlign: 'right', renderCell: (p) => <MoneyText amount={p.value} currency={p.row.toCurrency} fontWeight={600} /> },
      {
        field: 'exchangeRate',
        headerName: t('cashOps.dealRate'),
        width: 150,
        renderCell: (p) => (p.row.type === 'EXCHANGE' ? <Ltr>{formatNumber(p.value, { maxDecimals: 6 })}</Ltr> : ''),
      },
      { field: 'notes', headerName: t('common.notes'), flex: 1, minWidth: 150, renderCell: (p) => <UserText>{p.value}</UserText> },
      { field: 'createdByName', headerName: t('customerPayments.by'), width: 130, renderCell: (p) => <UserText>{p.value}</UserText> },
    ],
    [t],
  );
  return (
    <Box sx={{ px: 1, pb: 1 }}>
      <DataTable server rows={list.data?.content} rowCount={list.data?.totalElements} columns={columns} loading={list.isFetching} paginationModel={pagination} onPaginationModelChange={setPagination} />
    </Box>
  );
}
