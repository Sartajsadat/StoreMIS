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
import SupplierPaymentDialog from './SupplierPaymentDialog';
import { supplierPaymentService } from '../../services/supplierPaymentService';
import { errorMessage } from '../../services/apiClient';
import { useAuth } from '../../contexts/AuthContext';
import { useNotify } from '../../contexts/NotificationContext';
import { useDebounce } from '../../hooks/useDebounce';
import { resolveRange } from '../../utils/dateRange';
import { formatDate } from '../../utils/format';

export default function SupplierPaymentsPage() {
  const { t } = useTranslation();
  const notify = useNotify();
  const { isAdmin } = useAuth();
  const queryClient = useQueryClient();
  const [range, setRange] = useState({ preset: 'MONTH', ...resolveRange('MONTH') });
  const [search, setSearch] = useState('');
  const [pagination, setPagination] = useState({ page: 0, pageSize: 20 });
  const [newOpen, setNewOpen] = useState(false);
  const [reverseTarget, setReverseTarget] = useState(null);
  const [busy, setBusy] = useState(false);
  const q = useDebounce(search, 300);
  const params = { from: range.from, to: range.to, q: q || undefined, page: pagination.page, size: pagination.pageSize };
  const list = useQuery({ queryKey: ['supplier-payments', params], queryFn: () => supplierPaymentService.list(params), placeholderData: keepPreviousData });
  const refresh = () => ['supplier-payments', 'suppliers', 'purchases', 'cash-accounts'].forEach((k) => queryClient.invalidateQueries({ queryKey: [k] }));

  const columns = useMemo(
    () => [
      { field: 'paymentNo', headerName: t('payments.number'), width: 160, renderCell: (p) => <Ltr>{p.value}</Ltr> },
      { field: 'businessDate', headerName: t('payments.date'), width: 120, renderCell: (p) => <Ltr>{formatDate(p.value)}</Ltr> },
      { field: 'supplierName', headerName: t('payments.supplier'), flex: 1, minWidth: 180, renderCell: (p) => <UserText>{p.value}</UserText> },
      { field: 'amount', headerName: t('payments.amount'), width: 150, align: 'right', headerAlign: 'right', renderCell: (p) => <MoneyText amount={p.value} currency={p.row.currency} fontWeight={600} /> },
      {
        field: 'allocations',
        headerName: t('payments.allocated'),
        flex: 1,
        minWidth: 180,
        sortable: false,
        renderCell: (p) => (
          <Ltr>
            {[...p.value.map((a) => a.purchaseNo), ...(Number(p.row.unallocatedAmount) > 0 ? [t('payments.advance')] : [])].join(', ')}
          </Ltr>
        ),
      },
      { field: 'kind', headerName: t('common.details'), width: 140, renderCell: (p) => t(`payments.kind.${p.value}`) },
      { field: 'cashAccountName', headerName: t('payments.cashAccount'), width: 130 },
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
                  <Tooltip title={t('payments.reverse')}>
                    <IconButton size="small" onClick={() => setReverseTarget(p.row)} aria-label={t('payments.reverse')}>
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
        title={t('payments.title')}
        subtitle={t('payments.subtitle')}
        actions={
          <Button variant="contained" startIcon={<AddOutlined />} onClick={() => setNewOpen(true)}>
            {t('payments.new')}
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
      <SupplierPaymentDialog
        open={newOpen}
        onClose={() => setNewOpen(false)}
        onSaved={(p) => {
          setNewOpen(false);
          notify.success(t('payments.saved', { number: p.paymentNo }));
          refresh();
        }}
      />
      <ReasonDialog
        open={Boolean(reverseTarget)}
        title={t('payments.reverseTitle', { number: reverseTarget?.paymentNo ?? '' })}
        message={t('payments.reverseHint', { account: reverseTarget?.cashAccountName ?? '' })}
        confirmLabel={t('payments.reverse')}
        loading={busy}
        onClose={() => setReverseTarget(null)}
        onConfirm={async (reason) => {
          setBusy(true);
          try {
            await supplierPaymentService.reverse(reverseTarget.id, reason);
            notify.success(t('payments.reversed'));
            setReverseTarget(null);
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
