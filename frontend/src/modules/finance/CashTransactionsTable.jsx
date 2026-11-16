import { useMemo, useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Box, MenuItem, TextField } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import DataTable from '../../components/data/DataTable';
import FilterBar from '../../components/data/FilterBar';
import DateRangeFilter from '../../components/data/DateRangeFilter';
import MoneyText from '../../components/display/MoneyText';
import UserText from '../../components/display/UserText';
import Ltr from '../../components/display/Ltr';
import { cashService, CASH_TXN_TYPES } from '../../services/cashService';
import { useCashAccounts } from '../../hooks/useCurrency';
import { useDebounce } from '../../hooks/useDebounce';
import { resolveRange } from '../../utils/dateRange';
import { formatDate } from '../../utils/format';

/** Cash ledger with filters; shared by Cash Accounts and Cash Transactions pages. */
export default function CashTransactionsTable({ defaultPreset = 'MONTH' }) {
  const { t } = useTranslation();
  const theme = useTheme();
  const accounts = useCashAccounts();
  const [range, setRange] = useState({ preset: defaultPreset, ...resolveRange(defaultPreset) });
  const [accountId, setAccountId] = useState('');
  const [type, setType] = useState('');
  const [search, setSearch] = useState('');
  const [pagination, setPagination] = useState({ page: 0, pageSize: 20 });
  const q = useDebounce(search, 300);
  const params = { from: range.from, to: range.to, accountId: accountId || undefined, type: type || undefined, q: q || undefined, page: pagination.page, size: pagination.pageSize };
  const list = useQuery({ queryKey: ['cash-transactions', params], queryFn: () => cashService.transactions(params), placeholderData: keepPreviousData });

  const columns = useMemo(
    () => [
      { field: 'businessDate', headerName: t('inventory.date'), width: 120, renderCell: (p) => <Ltr>{formatDate(p.value)}</Ltr> },
      { field: 'txnNo', headerName: t('cash.reference'), width: 150, renderCell: (p) => <Ltr>{p.value}</Ltr> },
      { field: 'accountName', headerName: t('cash.account'), width: 130 },
      { field: 'type', headerName: t('cash.type'), width: 190, renderCell: (p) => t(`cash.types.${p.value}`) },
      {
        field: 'amount',
        headerName: t('payments.amount'),
        width: 160,
        align: 'right',
        headerAlign: 'right',
        renderCell: (p) => (
          <MoneyText amount={p.row.direction === 'OUT' ? `-${p.value}` : p.value} currency={p.row.currency} fontWeight={600} color={p.row.direction === 'OUT' ? theme.palette.error.main : theme.palette.success.main} />
        ),
      },
      { field: 'balanceAfter', headerName: t('cash.balanceAfter'), width: 160, align: 'right', headerAlign: 'right', renderCell: (p) => <MoneyText amount={p.value} currency={p.row.currency} /> },
      { field: 'referenceNo', headerName: t('inventory.reference'), width: 150, renderCell: (p) => <Ltr>{p.value}</Ltr> },
      { field: 'description', headerName: t('cash.description'), flex: 1, minWidth: 180, renderCell: (p) => <UserText>{p.value}</UserText> },
      { field: 'createdByName', headerName: t('inventory.by'), width: 140, renderCell: (p) => <UserText>{p.value}</UserText> },
    ],
    [t, theme],
  );

  return (
    <>
      <FilterBar search={search} onSearchChange={setSearch} onReset={() => (setSearch(''), setAccountId(''), setType(''))}>
        <DateRangeFilter value={range} onChange={setRange} />
        <TextField select label={t('cash.account')} value={accountId} onChange={(e) => setAccountId(e.target.value)} sx={{ width: 160 }} fullWidth={false}>
          <MenuItem value="">{t('cash.allAccounts')}</MenuItem>
          {(accounts.data ?? []).map((a) => (
            <MenuItem key={a.id} value={a.id}>
              {a.name}
            </MenuItem>
          ))}
        </TextField>
        <TextField select label={t('cash.type')} value={type} onChange={(e) => setType(e.target.value)} sx={{ width: 190 }} fullWidth={false}>
          <MenuItem value="">{t('cash.allTypes')}</MenuItem>
          {CASH_TXN_TYPES.map((x) => (
            <MenuItem key={x} value={x}>
              {t(`cash.types.${x}`)}
            </MenuItem>
          ))}
        </TextField>
      </FilterBar>
      <Box sx={{ px: 1, pb: 1 }}>
        <DataTable server rows={list.data?.content} rowCount={list.data?.totalElements} columns={columns} loading={list.isFetching} paginationModel={pagination} onPaginationModelChange={setPagination} />
      </Box>
    </>
  );
}
