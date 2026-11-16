import { useMemo, useState } from 'react';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Box, Button, Card, Link } from '@mui/material';
import AssignmentReturnOutlined from '@mui/icons-material/AssignmentReturnOutlined';
import PageHeader from '../../components/display/PageHeader';
import DataTable from '../../components/data/DataTable';
import FilterBar from '../../components/data/FilterBar';
import DateRangeFilter from '../../components/data/DateRangeFilter';
import MoneyText from '../../components/display/MoneyText';
import UserText from '../../components/display/UserText';
import Ltr from '../../components/display/Ltr';
import ReturnDialog from './ReturnDialog';
import ReturnDetailDialog from './ReturnDetailDialog';
import { returnsService } from '../../services/returnsService';
import { useNotify } from '../../contexts/NotificationContext';
import { useDebounce } from '../../hooks/useDebounce';
import { resolveRange } from '../../utils/dateRange';
import { formatDate } from '../../utils/format';

/** Sales returns or purchase returns: history plus "New return". */
export default function ReturnsPage({ kind }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const notify = useNotify();
  const queryClient = useQueryClient();
  const ns = kind === 'sales' ? 'returns.sales' : 'returns.purchase';
  const [range, setRange] = useState({ preset: 'MONTH', ...resolveRange('MONTH') });
  const [search, setSearch] = useState('');
  const [pagination, setPagination] = useState({ page: 0, pageSize: 20 });
  const [newOpen, setNewOpen] = useState(false);
  const [detail, setDetail] = useState(null);
  const q = useDebounce(search, 300);
  const params = { from: range.from, to: range.to, q: q || undefined, page: pagination.page, size: pagination.pageSize };
  const list = useQuery({ queryKey: ['returns', kind, params], queryFn: () => returnsService.list(kind, params), placeholderData: keepPreviousData });

  const columns = useMemo(
    () => [
      { field: 'returnNo', headerName: t('returns.number'), width: 160, renderCell: (p) => <Ltr>{p.value}</Ltr> },
      { field: 'businessDate', headerName: t('returns.date'), width: 115, renderCell: (p) => <Ltr>{formatDate(p.value)}</Ltr> },
      {
        field: 'documentNo',
        headerName: t(`${ns}.documentNo`),
        width: 160,
        renderCell: (p) => (
          <Link
            component="button"
            onClick={(e) => {
              e.stopPropagation();
              navigate(kind === 'sales' ? `/sales/${p.row.documentId}` : `/purchases/${p.row.documentId}`);
            }}
          >
            <Ltr>{p.value}</Ltr>
          </Link>
        ),
      },
      { field: 'partyName', headerName: t(`${ns}.party`), flex: 1, minWidth: 160, renderCell: (p) => <UserText>{p.value}</UserText> },
      { field: 'totalAmount', headerName: t('returns.total'), width: 140, align: 'right', headerAlign: 'right', renderCell: (p) => <MoneyText amount={p.value} currency={p.row.currency} fontWeight={600} /> },
      { field: 'cashRefundAmount', headerName: t(`${ns}.refunded`), width: 130, align: 'right', headerAlign: 'right', renderCell: (p) => (Number(p.value) ? <MoneyText amount={p.value} currency={p.row.currency} /> : '') },
      { field: 'creditAmount', headerName: t(`${ns}.credited`), width: 130, align: 'right', headerAlign: 'right', renderCell: (p) => (Number(p.value) ? <MoneyText amount={p.value} currency={p.row.currency} /> : '') },
      { field: 'reason', headerName: t('returns.reason'), flex: 1, minWidth: 150, renderCell: (p) => <UserText>{p.value}</UserText> },
      { field: 'createdByName', headerName: t('returns.by'), width: 130, renderCell: (p) => <UserText>{p.value}</UserText> },
    ],
    [t, ns, kind, navigate],
  );

  return (
    <>
      <PageHeader
        title={t(`${ns}.title`)}
        subtitle={t(`${ns}.subtitle`)}
        actions={
          <Button variant="contained" startIcon={<AssignmentReturnOutlined />} onClick={() => setNewOpen(true)}>
            {t(`${ns}.new`)}
          </Button>
        }
      />
      <Card>
        <FilterBar search={search} onSearchChange={setSearch} onReset={() => (setSearch(''), setRange({ preset: 'MONTH', ...resolveRange('MONTH') }))}>
          <DateRangeFilter value={range} onChange={setRange} />
        </FilterBar>
        <Box sx={{ px: 1, pb: 1 }}>
          <DataTable
            server
            rows={list.data?.content}
            rowCount={list.data?.totalElements}
            columns={columns}
            loading={list.isFetching}
            paginationModel={pagination}
            onPaginationModelChange={setPagination}
            onRowClick={(p) => setDetail(p.row)}
          />
        </Box>
      </Card>
      <ReturnDialog
        open={newOpen}
        kind={kind}
        onClose={() => setNewOpen(false)}
        onSaved={(r) => {
          setNewOpen(false);
          notify.success(t('returns.saved', { number: r.returnNo }));
          ['returns', 'sales', 'purchases', 'inventory', 'products', 'customers', 'suppliers', 'cash-accounts'].forEach((k) => queryClient.invalidateQueries({ queryKey: [k] }));
          setDetail(r);
        }}
      />
      <ReturnDetailDialog value={detail} onClose={() => setDetail(null)} />
    </>
  );
}

export function SalesReturnsPage() {
  return <ReturnsPage kind="sales" />;
}

export function PurchaseReturnsPage() {
  return <ReturnsPage kind="purchase" />;
}
