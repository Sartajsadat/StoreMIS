import { useMemo, useState } from 'react';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Box, Button, Card, Chip, FormControlLabel, Grid, MenuItem, Stack, Switch, TextField, Typography } from '@mui/material';
import PersonAddAlt1Outlined from '@mui/icons-material/PersonAddAlt1Outlined';
import AccountBalanceWalletOutlined from '@mui/icons-material/AccountBalanceWalletOutlined';
import PageHeader from '../../components/display/PageHeader';
import KpiCard from '../../components/display/KpiCard';
import DataTable from '../../components/data/DataTable';
import FilterBar from '../../components/data/FilterBar';
import StatusChip from '../../components/display/StatusChip';
import UserText from '../../components/display/UserText';
import Ltr from '../../components/display/Ltr';
import BalanceChips from '../../components/party/BalanceChips';
import CustomerFormDialog from './CustomerFormDialog';
import { customerService } from '../../services/customerService';
import { useNotify } from '../../contexts/NotificationContext';
import { useDebounce } from '../../hooks/useDebounce';
import { formatMoney } from '../../utils/format';

export default function CustomersPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [type, setType] = useState('');
  const [withBalance, setWithBalance] = useState(false);
  const [pagination, setPagination] = useState({ page: 0, pageSize: 20 });
  const [dialog, setDialog] = useState(false);
  const q = useDebounce(search, 300);
  const params = { q: q || undefined, type: type || undefined, withBalance, page: pagination.page, size: pagination.pageSize };
  const list = useQuery({ queryKey: ['customers', params], queryFn: () => customerService.list(params), placeholderData: keepPreviousData });
  const receivables = useQuery({ queryKey: ['customers', 'receivables'], queryFn: customerService.receivables });

  const columns = useMemo(
    () => [
      {
        field: 'name',
        headerName: t('customers.name'),
        flex: 1.4,
        minWidth: 200,
        renderCell: (p) => (
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', height: '100%' }}>
            <Typography variant="body2" sx={{ fontWeight: 600 }} noWrap>
              {p.row.walkIn ? t('customers.walkIn') : <UserText>{p.value}</UserText>}
            </Typography>
            {p.row.walkIn && <Chip size="small" variant="outlined" label={t('customers.walkInHint')} />}
          </Stack>
        ),
      },
      { field: 'phone', headerName: t('customers.phone'), width: 150, renderCell: (p) => <Ltr>{p.value}</Ltr> },
      { field: 'customerType', headerName: t('customers.type'), width: 130, renderCell: (p) => t(`saleTypes.${p.value}`) },
      {
        field: 'balances',
        headerName: t('customers.balance'),
        flex: 1.2,
        minWidth: 200,
        sortable: false,
        renderCell: (p) => (
          <Stack sx={{ justifyContent: 'center', height: '100%' }}>
            <BalanceChips balances={p.value} emptyText={t('customers.settled')} owedLabel={t('customers.owes')} creditLabel={t('customers.credit')} />
          </Stack>
        ),
      },
      { field: 'active', headerName: t('common.status'), width: 120, renderCell: (p) => <StatusChip status={p.value ? 'ACTIVE' : 'INACTIVE'} /> },
    ],
    [t],
  );

  return (
    <>
      <PageHeader
        title={t('customers.title')}
        subtitle={t('customers.subtitle')}
        actions={
          <Button variant="contained" startIcon={<PersonAddAlt1Outlined />} onClick={() => setDialog(true)}>
            {t('customers.add')}
          </Button>
        }
      />
      {Object.keys(receivables.data ?? {}).length > 0 && (
        <Grid container spacing={3} sx={{ mb: 3 }}>
          {Object.entries(receivables.data).map(([cur, v]) => (
            <Grid key={cur} size={{ xs: 12, sm: 6, lg: 3 }}>
              <KpiCard label={`${t('customers.totalReceivables')} · ${cur}`} value={formatMoney(v, cur)} icon={AccountBalanceWalletOutlined} color="info" />
            </Grid>
          ))}
        </Grid>
      )}
      <Card>
        <FilterBar search={search} onSearchChange={setSearch} onReset={() => (setSearch(''), setType(''), setWithBalance(false))}>
          <TextField select label={t('customers.type')} value={type} onChange={(e) => setType(e.target.value)} sx={{ width: 150 }} fullWidth={false}>
            <MenuItem value="">{t('customers.allTypes')}</MenuItem>
            <MenuItem value="RETAIL">{t('saleTypes.RETAIL')}</MenuItem>
            <MenuItem value="WHOLESALE">{t('saleTypes.WHOLESALE')}</MenuItem>
          </TextField>
          <FormControlLabel control={<Switch checked={withBalance} onChange={(e) => setWithBalance(e.target.checked)} />} label={t('customers.withBalance')} />
        </FilterBar>
        <Box sx={{ px: 1, pb: 1 }}>
          <DataTable server rows={list.data?.content} rowCount={list.data?.totalElements} columns={columns} loading={list.isFetching} paginationModel={pagination} onPaginationModelChange={setPagination} onRowClick={(p) => navigate(`/customers/${p.id}`)} />
        </Box>
      </Card>
      <CustomerFormDialog
        open={dialog}
        onClose={() => setDialog(false)}
        onSaved={(c) => {
          setDialog(false);
          notify.success(t('customers.saved'));
          queryClient.invalidateQueries({ queryKey: ['customers'] });
          navigate(`/customers/${c.id}`);
        }}
      />
    </>
  );
}
