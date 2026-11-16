import { useMemo, useState } from 'react';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Box, Button, Card, IconButton, Tooltip } from '@mui/material';
import AddOutlined from '@mui/icons-material/AddOutlined';
import UndoOutlined from '@mui/icons-material/UndoOutlined';
import PageHeader from '../../components/display/PageHeader';
import DataTable from '../../components/data/DataTable';
import FilterBar from '../../components/data/FilterBar';
import DateRangeFilter from '../../components/data/DateRangeFilter';
import MoneyText from '../../components/display/MoneyText';
import StatusChip from '../../components/display/StatusChip';
import UserText from '../../components/display/UserText';
import Ltr from '../../components/display/Ltr';
import ReasonDialog from '../../components/feedback/ReasonDialog';
import CustomerPaymentDialog from './CustomerPaymentDialog';
import { customerPaymentService } from '../../services/customerPaymentService';
import { errorMessage } from '../../services/apiClient';
import { useAuth } from '../../contexts/AuthContext';
import { useNotify } from '../../contexts/NotificationContext';
import { useDebounce } from '../../hooks/useDebounce';
import { resolveRange } from '../../utils/dateRange';
import { formatDate } from '../../utils/format';

export default function CustomerPaymentsPage() {
  const { t } = useTranslation();
  const notify = useNotify();
  const { isAdmin } = useAuth();
  const queryClient = useQueryClient();
  const [range, setRange] = useState({ preset: 'MONTH', ...resolveRange('MONTH') });
  const [search, setSearch] = useState('');
  const [pagination, setPagination] = useState({ page: 0, pageSize: 20 });
  const [newOpen, setNewOpen] = useState(false);
  const [target, setTarget] = useState(null);
  const [busy, setBusy] = useState(false);
  const q = useDebounce(search, 300);
  const params = { from: range.from, to: range.to, q: q || undefined, page: pagination.page, size: pagination.pageSize };
  const list = useQuery({ queryKey: ['customer-payments', params], queryFn: () => customerPaymentService.list(params), placeholderData: keepPreviousData });
  const refresh = () => ['customer-payments', 'customers', 'sales', 'cash-accounts'].forEach((k) => queryClient.invalidateQueries({ queryKey: [k] }));

  const columns = useMemo(
    () => [
      { field: 'paymentNo', headerName: t('customerPayments.number'), width: 160, renderCell: (p) => <Ltr>{p.value}</Ltr> },
      { field: 'businessDate', headerName: t('customerPayments.date'), width: 120, renderCell: (p) => <Ltr>{formatDate(p.value)}</Ltr> },
      { field: 'customerName', headerName: t('customerPayments.customer'), flex: 1, minWidth: 170, renderCell: (p) => <UserText>{p.value}</UserText> },
      { field: 'amount', headerName: t('customerPayments.amount'), width: 150, align: 'right', headerAlign: 'right', renderCell: (p) => <MoneyText amount={p.value} currency={p.row.currency} fontWeight={600} /> },
      {
        field: 'allocations',
        headerName: t('customerPayments.allocated'),
        flex: 1,
        minWidth: 170,
        sortable: false,
        renderCell: (p) => <Ltr>{[...p.value.map((a) => a.invoiceNo), ...(Number(p.row.unallocatedAmount) > 0 ? [t('customerPayments.advance')] : [])].join(', ')}</Ltr>,
      },
      { field: 'kind', headerName: t('common.details'), width: 130, renderCell: (p) => t(`customerPayments.kind.${p.value}`) },
      { field: 'cashAccountName', headerName: t('customerPayments.account'), width: 130 },
      { field: 'createdByName', headerName: t('customerPayments.by'), width: 140, renderCell: (p) => <UserText>{p.value}</UserText> },
      { field: 'status', headerName: t('common.status'), width: 120, renderCell: (p) => <StatusChip status={p.value} /> },
      ...(isAdmin
        ? [
            {
              field: 'actions',
              headerName: '',
              width: 60,
              sortable: false,
              renderCell: (p) =>
                p.row.status === 'POSTED' && !p.row.allocations.some((a) => a.released) ? (
                  <Tooltip title={t('customerPayments.reverse')}>
                    <IconButton size="small" onClick={() => setTarget(p.row)} aria-label={t('customerPayments.reverse')}>
                      <UndoOutlined fontSize="small" />
                    </IconButton>
                  </Tooltip>
                ) : null,
            },
          ]
        : []),
    ],
    [t, isAdmin],
  );

  return (
    <>
      <PageHeader
        title={t('customerPayments.title')}
        subtitle={t('customerPayments.subtitle')}
        actions={
          <Button variant="contained" startIcon={<AddOutlined />} onClick={() => setNewOpen(true)}>
            {t('customerPayments.new')}
          </Button>
        }
      />
      <Card>
        <FilterBar search={search} onSearchChange={setSearch} onReset={() => (setSearch(''), setRange({ preset: 'MONTH', ...resolveRange('MONTH') }))}>
          <DateRangeFilter value={range} onChange={setRange} />
        </FilterBar>
        <Box sx={{ px: 1, pb: 1 }}>
          <DataTable server rows={list.data?.content} rowCount={list.data?.totalElements} columns={columns} loading={list.isFetching} paginationModel={pagination} onPaginationModelChange={setPagination} />
        </Box>
      </Card>
      <CustomerPaymentDialog open={newOpen} onClose={() => setNewOpen(false)} onSaved={(p) => (setNewOpen(false), notify.success(t('customerPayments.saved', { number: p.paymentNo })), refresh())} />
      <ReasonDialog
        open={Boolean(target)}
        title={t('customerPayments.reverseTitle', { number: target?.paymentNo ?? '' })}
        message={t('customerPayments.reverseHint', { account: target?.cashAccountName ?? '' })}
        confirmLabel={t('customerPayments.reverse')}
        loading={busy}
        onClose={() => setTarget(null)}
        onConfirm={async (reason) => {
          setBusy(true);
          try {
            await customerPaymentService.reverse(target.id, reason);
            notify.success(t('customerPayments.reversed'));
            setTarget(null);
            refresh();
          } catch (e) {
            notify.error(errorMessage(t, e));
          } finally {
            setBusy(false);
          }
        }}
      />
    </>
  );
}
