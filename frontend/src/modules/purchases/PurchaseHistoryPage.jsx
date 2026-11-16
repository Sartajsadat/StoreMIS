import { useMemo, useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Box, Button, Card, MenuItem, TextField } from '@mui/material';
import AddOutlined from '@mui/icons-material/AddOutlined';
import PageHeader from '../../components/display/PageHeader';
import DataTable from '../../components/data/DataTable';
import FilterBar from '../../components/data/FilterBar';
import DateRangeFilter from '../../components/data/DateRangeFilter';
import MoneyText from '../../components/display/MoneyText';
import StatusChip from '../../components/display/StatusChip';
import UserText from '../../components/display/UserText';
import Ltr from '../../components/display/Ltr';
import { purchaseService } from '../../services/purchaseService';
import { useDebounce } from '../../hooks/useDebounce';
import { resolveRange } from '../../utils/dateRange';
import { formatDate } from '../../utils/format';

export default function PurchaseHistoryPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [range, setRange] = useState({ preset: 'MONTH', ...resolveRange('MONTH') });
  const [status, setStatus] = useState('');
  const [paymentStatus, setPaymentStatus] = useState('');
  const [search, setSearch] = useState('');
  const [pagination, setPagination] = useState({ page: 0, pageSize: 20 });
  const q = useDebounce(search, 300);
  const params = { from: range.from, to: range.to, status: status || undefined, paymentStatus: paymentStatus || undefined, q: q || undefined, page: pagination.page, size: pagination.pageSize };
  const list = useQuery({ queryKey: ['purchases', params], queryFn: () => purchaseService.list(params), placeholderData: keepPreviousData });

  const columns = useMemo(
    () => [
      { field: 'purchaseNo', headerName: t('purchases.number'), width: 160, renderCell: (p) => <Ltr>{p.value ?? t('purchases.draftLabel', { id: p.row.id })}</Ltr> },
      { field: 'purchaseDate', headerName: t('purchases.date'), width: 120, renderCell: (p) => <Ltr>{formatDate(p.value)}</Ltr> },
      { field: 'supplierName', headerName: t('purchases.supplier'), flex: 1, minWidth: 180, renderCell: (p) => <UserText>{p.value}</UserText> },
      { field: 'supplierInvoiceNo', headerName: t('purchases.supplierInvoiceNo'), width: 150, renderCell: (p) => <Ltr>{p.value}</Ltr> },
      { field: 'grandTotal', headerName: t('purchases.total'), width: 150, align: 'right', headerAlign: 'right', renderCell: (p) => <MoneyText amount={p.value} currency={p.row.currency} /> },
      { field: 'balanceDue', headerName: t('purchases.due'), width: 140, align: 'right', headerAlign: 'right', renderCell: (p) => (p.row.status === 'CONFIRMED' ? <MoneyText amount={p.value} currency={p.row.currency} /> : null) },
      { field: 'status', headerName: t('purchases.status'), width: 130, renderCell: (p) => <StatusChip status={p.value} /> },
      { field: 'paymentStatus', headerName: t('purchases.paymentStatus'), width: 150, renderCell: (p) => (p.row.status === 'CONFIRMED' ? <StatusChip status={p.value} /> : null) },
    ],
    [t],
  );

  return (
    <>
      <PageHeader
        title={t('purchases.historyTitle')}
        subtitle={t('purchases.historySubtitle')}
        actions={
          <Button variant="contained" startIcon={<AddOutlined />} onClick={() => navigate('/purchases/new')}>
            {t('nav.newPurchase')}
          </Button>
        }
      />
      <Card>
        <FilterBar
          search={search}
          onSearchChange={setSearch}
          onReset={() => {
            setSearch('');
            setStatus('');
            setPaymentStatus('');
            setRange({ preset: 'MONTH', ...resolveRange('MONTH') });
          }}
        >
          <DateRangeFilter value={range} onChange={setRange} />
          <TextField select label={t('purchases.status')} value={status} onChange={(e) => setStatus(e.target.value)} sx={{ width: 150 }} fullWidth={false}>
            <MenuItem value="">{t('purchases.allStatuses')}</MenuItem>
            {['DRAFT', 'CONFIRMED', 'CANCELLED'].map((s) => (
              <MenuItem key={s} value={s}>
                {t(`status.${s}`)}
              </MenuItem>
            ))}
          </TextField>
          <TextField select label={t('purchases.paymentStatus')} value={paymentStatus} onChange={(e) => setPaymentStatus(e.target.value)} sx={{ width: 170 }} fullWidth={false}>
            <MenuItem value="">{t('purchases.allPayments')}</MenuItem>
            {['UNPAID', 'PARTIALLY_PAID', 'PAID'].map((s) => (
              <MenuItem key={s} value={s}>
                {t(`status.${s}`)}
              </MenuItem>
            ))}
          </TextField>
        </FilterBar>
        <Box sx={{ px: 1, pb: 1 }}>
          <DataTable server rows={list.data?.content} rowCount={list.data?.totalElements} columns={columns} loading={list.isFetching} paginationModel={pagination} onPaginationModelChange={setPagination} onRowClick={(p) => navigate(`/purchases/${p.id}`)} />
        </Box>
      </Card>
    </>
  );
}
