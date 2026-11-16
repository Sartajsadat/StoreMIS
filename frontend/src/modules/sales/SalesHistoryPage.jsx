import { useMemo, useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Box, Button, Card, MenuItem, TextField } from '@mui/material';
import PointOfSaleOutlined from '@mui/icons-material/PointOfSaleOutlined';
import PageHeader from '../../components/display/PageHeader';
import DataTable from '../../components/data/DataTable';
import FilterBar from '../../components/data/FilterBar';
import DateRangeFilter from '../../components/data/DateRangeFilter';
import MoneyText from '../../components/display/MoneyText';
import StatusChip from '../../components/display/StatusChip';
import UserText from '../../components/display/UserText';
import Ltr from '../../components/display/Ltr';
import { salesService } from '../../services/salesService';
import { useDebounce } from '../../hooks/useDebounce';
import { resolveRange } from '../../utils/dateRange';
import { formatDateTime } from '../../utils/format';

export default function SalesHistoryPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [range, setRange] = useState({ preset: 'TODAY', ...resolveRange('TODAY') });
  const [status, setStatus] = useState('');
  const [paymentStatus, setPaymentStatus] = useState('');
  const [saleType, setSaleType] = useState('');
  const [search, setSearch] = useState('');
  const [pagination, setPagination] = useState({ page: 0, pageSize: 20 });
  const q = useDebounce(search, 300);
  const params = {
    from: range.from,
    to: range.to,
    status: status || undefined,
    paymentStatus: paymentStatus || undefined,
    saleType: saleType || undefined,
    q: q || undefined,
    page: pagination.page,
    size: pagination.pageSize,
  };
  const list = useQuery({ queryKey: ['sales', params], queryFn: () => salesService.list(params), placeholderData: keepPreviousData });

  const columns = useMemo(
    () => [
      { field: 'invoiceNo', headerName: t('sales.number'), width: 160, renderCell: (p) => <Ltr>{p.value}</Ltr> },
      { field: 'saleDate', headerName: t('sales.date'), width: 150, renderCell: (p) => <Ltr>{formatDateTime(p.value)}</Ltr> },
      { field: 'customerName', headerName: t('sales.customer'), flex: 1, minWidth: 170, renderCell: (p) => (p.row.walkIn ? t('customers.walkIn') : <UserText>{p.value}</UserText>) },
      { field: 'saleType', headerName: t('sales.type'), width: 110, renderCell: (p) => t(`saleTypes.${p.value}`) },
      { field: 'grandTotal', headerName: t('sales.total'), width: 150, align: 'right', headerAlign: 'right', renderCell: (p) => <MoneyText amount={p.value} currency={p.row.currency} fontWeight={600} /> },
      { field: 'balanceDue', headerName: t('sales.due'), width: 130, align: 'right', headerAlign: 'right', renderCell: (p) => (Number(p.value) ? <MoneyText amount={p.value} currency={p.row.currency} /> : '') },
      { field: 'status', headerName: t('sales.status'), width: 130, renderCell: (p) => <StatusChip status={p.value} /> },
      { field: 'paymentStatus', headerName: t('sales.payment'), width: 150, renderCell: (p) => (p.row.status === 'CONFIRMED' ? <StatusChip status={p.value} /> : null) },
      { field: 'createdByName', headerName: t('sales.cashier'), width: 140, renderCell: (p) => <UserText>{p.value}</UserText> },
    ],
    [t],
  );

  return (
    <>
      <PageHeader
        title={t('sales.historyTitle')}
        subtitle={t('sales.historySubtitle')}
        actions={
          <Button variant="contained" startIcon={<PointOfSaleOutlined />} onClick={() => navigate('/sales/new')}>
            {t('nav.newSale')}
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
            setSaleType('');
            setRange({ preset: 'TODAY', ...resolveRange('TODAY') });
          }}
        >
          <DateRangeFilter value={range} onChange={setRange} />
          <TextField select label={t('sales.status')} value={status} onChange={(e) => setStatus(e.target.value)} sx={{ width: 140 }} fullWidth={false}>
            <MenuItem value="">{t('sales.allStatuses')}</MenuItem>
            {['CONFIRMED', 'CANCELLED'].map((s) => (
              <MenuItem key={s} value={s}>
                {t(`status.${s}`)}
              </MenuItem>
            ))}
          </TextField>
          <TextField select label={t('sales.payment')} value={paymentStatus} onChange={(e) => setPaymentStatus(e.target.value)} sx={{ width: 160 }} fullWidth={false}>
            <MenuItem value="">{t('sales.allPayments')}</MenuItem>
            {['UNPAID', 'PARTIALLY_PAID', 'PAID'].map((s) => (
              <MenuItem key={s} value={s}>
                {t(`status.${s}`)}
              </MenuItem>
            ))}
          </TextField>
          <TextField select label={t('sales.type')} value={saleType} onChange={(e) => setSaleType(e.target.value)} sx={{ width: 140 }} fullWidth={false}>
            <MenuItem value="">{t('sales.allTypes')}</MenuItem>
            <MenuItem value="RETAIL">{t('saleTypes.RETAIL')}</MenuItem>
            <MenuItem value="WHOLESALE">{t('saleTypes.WHOLESALE')}</MenuItem>
          </TextField>
        </FilterBar>
        <Box sx={{ px: 1, pb: 1 }}>
          <DataTable server rows={list.data?.content} rowCount={list.data?.totalElements} columns={columns} loading={list.isFetching} paginationModel={pagination} onPaginationModelChange={setPagination} onRowClick={(p) => navigate(`/sales/${p.id}`)} />
        </Box>
      </Card>
    </>
  );
}
