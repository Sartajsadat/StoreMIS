import { useMemo, useState } from 'react';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Box, Button, Card, FormControlLabel, Grid, Stack, Switch, Typography } from '@mui/material';
import AddOutlined from '@mui/icons-material/AddOutlined';
import AccountBalanceWalletOutlined from '@mui/icons-material/AccountBalanceWalletOutlined';
import PageHeader from '../../components/display/PageHeader';
import KpiCard from '../../components/display/KpiCard';
import DataTable from '../../components/data/DataTable';
import FilterBar from '../../components/data/FilterBar';
import StatusChip from '../../components/display/StatusChip';
import UserText from '../../components/display/UserText';
import Ltr from '../../components/display/Ltr';
import BalanceChips from '../../components/party/BalanceChips';
import SupplierFormDialog from './SupplierFormDialog';
import { supplierService } from '../../services/supplierService';
import { useAuth } from '../../contexts/AuthContext';
import { useNotify } from '../../contexts/NotificationContext';
import { useDebounce } from '../../hooks/useDebounce';
import { formatMoney } from '../../utils/format';

export default function SuppliersPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const notify = useNotify();
  const { hasPermission } = useAuth();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [withBalance, setWithBalance] = useState(false);
  const [pagination, setPagination] = useState({ page: 0, pageSize: 20 });
  const [dialog, setDialog] = useState(false);
  const q = useDebounce(search, 300);
  const canEdit = hasPermission('RECORD_PURCHASES');

  const params = { q: q || undefined, withBalance, page: pagination.page, size: pagination.pageSize };
  const list = useQuery({ queryKey: ['suppliers', params], queryFn: () => supplierService.list(params), placeholderData: keepPreviousData });
  const payables = useQuery({ queryKey: ['suppliers', 'payables'], queryFn: supplierService.payables });

  const columns = useMemo(
    () => [
      {
        field: 'name',
        headerName: t('suppliers.name'),
        flex: 1.4,
        minWidth: 200,
        renderCell: (p) => (
          <Box sx={{ display: 'flex', flexDirection: 'column', justifyContent: 'center', height: '100%', lineHeight: 1.3 }}>
            <Typography variant="body2" sx={{ fontWeight: 600 }} noWrap>
              <UserText>{p.value}</UserText>
            </Typography>
            {p.row.contactPerson && (
              <Typography variant="caption" color="text.secondary" noWrap>
                <UserText>{p.row.contactPerson}</UserText>
              </Typography>
            )}
          </Box>
        ),
      },
      { field: 'phone', headerName: t('suppliers.phone'), width: 150, renderCell: (p) => <Ltr>{p.value}</Ltr> },
      { field: 'balances', headerName: t('suppliers.balance'), flex: 1.2, minWidth: 220, sortable: false, renderCell: (p) => <Stack sx={{ justifyContent: 'center', height: '100%' }}><BalanceChips balances={p.value} /></Stack> },
      { field: 'active', headerName: t('common.status'), width: 120, renderCell: (p) => <StatusChip status={p.value ? 'ACTIVE' : 'INACTIVE'} /> },
    ],
    [t],
  );

  const totals = Object.entries(payables.data ?? {});

  return (
    <>
      <PageHeader
        title={t('suppliers.title')}
        subtitle={t('suppliers.subtitle')}
        actions={
          canEdit && (
            <Button variant="contained" startIcon={<AddOutlined />} onClick={() => setDialog(true)}>
              {t('suppliers.add')}
            </Button>
          )
        }
      />
      {totals.length > 0 && (
        <Grid container spacing={3} sx={{ mb: 3 }}>
          {totals.map(([cur, v]) => (
            <Grid key={cur} size={{ xs: 12, sm: 6, lg: 3 }}>
              <KpiCard label={`${t('suppliers.totalPayables')} · ${cur}`} value={formatMoney(v, cur)} icon={AccountBalanceWalletOutlined} color="warning" />
            </Grid>
          ))}
        </Grid>
      )}
      <Card>
        <FilterBar search={search} onSearchChange={setSearch} onReset={() => (setSearch(''), setWithBalance(false))}>
          <FormControlLabel control={<Switch checked={withBalance} onChange={(e) => setWithBalance(e.target.checked)} />} label={t('suppliers.withBalance')} />
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
            onRowClick={(p) => navigate(`/suppliers/${p.id}`)}
            rowHeight={60}
          />
        </Box>
      </Card>
      <SupplierFormDialog
        open={dialog}
        onClose={() => setDialog(false)}
        onSaved={(s) => {
          setDialog(false);
          notify.success(t('suppliers.saved'));
          queryClient.invalidateQueries({ queryKey: ['suppliers'] });
          navigate(`/suppliers/${s.id}`);
        }}
      />
    </>
  );
}
