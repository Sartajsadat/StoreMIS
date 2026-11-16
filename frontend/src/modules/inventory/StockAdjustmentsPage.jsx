import { useMemo, useState } from 'react';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Box, Button, Card, Chip, MenuItem, TextField, Tooltip, Typography } from '@mui/material';
import AddOutlined from '@mui/icons-material/AddOutlined';
import PageHeader from '../../components/display/PageHeader';
import DataTable from '../../components/data/DataTable';
import FilterBar from '../../components/data/FilterBar';
import DateRangeFilter from '../../components/data/DateRangeFilter';
import MoneyText from '../../components/display/MoneyText';
import QtyText from '../../components/display/QtyText';
import UserText from '../../components/display/UserText';
import Ltr from '../../components/display/Ltr';
import AdjustmentDialog from './AdjustmentDialog';
import { inventoryService, ADJUSTMENT_TYPES } from '../../services/inventoryService';
import { useAuth } from '../../contexts/AuthContext';
import { useSettings } from '../../contexts/SettingsContext';
import { useNotify } from '../../contexts/NotificationContext';
import { useDebounce } from '../../hooks/useDebounce';
import { useUnitLabel } from '../../hooks/useUnitLabel';
import { resolveRange } from '../../utils/dateRange';
import { formatDate } from '../../utils/format';

const INBOUND = new Set(['OPENING', 'INCREASE']);

export default function StockAdjustmentsPage() {
  const { t } = useTranslation();
  const { hasPermission } = useAuth();
  const { settings } = useSettings();
  const notify = useNotify();
  const unitLabel = useUnitLabel();
  const queryClient = useQueryClient();
  const [range, setRange] = useState({ preset: 'MONTH', ...resolveRange('MONTH') });
  const [type, setType] = useState('');
  const [search, setSearch] = useState('');
  const [pagination, setPagination] = useState({ page: 0, pageSize: 20 });
  const [open, setOpen] = useState(false);
  const q = useDebounce(search, 300);
  const showCost = hasPermission('VIEW_COST_PRICES');

  const params = { from: range.from, to: range.to, type: type || undefined, q: q || undefined, page: pagination.page, size: pagination.pageSize };
  const data = useQuery({ queryKey: ['inventory', 'adjustments', params], queryFn: () => inventoryService.adjustments(params), placeholderData: keepPreviousData });

  const columns = useMemo(
    () => [
      { field: 'adjustmentNo', headerName: t('inventory.reference'), width: 160, renderCell: (p) => <Ltr>{p.value}</Ltr> },
      { field: 'businessDate', headerName: t('inventory.date'), width: 120, renderCell: (p) => <Ltr>{formatDate(p.value)}</Ltr> },
      { field: 'productName', headerName: t('inventory.product'), flex: 1, minWidth: 180, renderCell: (p) => <UserText>{p.value}</UserText> },
      {
        field: 'type',
        headerName: t('inventory.adjustmentType'),
        width: 200,
        renderCell: (p) => <Chip size="small" variant="outlined" color={INBOUND.has(p.value) ? 'success' : p.value === 'DAMAGE' ? 'error' : 'warning'} label={t(`inventory.adjustmentTypes.${p.value}`)} />,
      },
      {
        field: 'quantity',
        headerName: t('inventory.quantity'),
        width: 130,
        align: 'right',
        headerAlign: 'right',
        renderCell: (p) => <QtyText value={INBOUND.has(p.row.type) ? p.value : -Number(p.value)} signed unit={unitLabel({ code: p.row.unitCode, name: p.row.unitName })} />,
      },
      ...(showCost
        ? [{ field: 'totalCost', headerName: t('inventory.totalCost'), width: 150, align: 'right', headerAlign: 'right', renderCell: (p) => <MoneyText amount={p.value} currency={settings.baseCurrency} /> }]
        : []),
      {
        field: 'reason',
        headerName: t('inventory.reason'),
        flex: 1.2,
        minWidth: 180,
        renderCell: (p) => (
          <Tooltip title={p.value}>
            <Typography variant="body2" noWrap>
              <UserText>{p.value}</UserText>
            </Typography>
          </Tooltip>
        ),
      },
      { field: 'createdByName', headerName: t('inventory.by'), width: 150, renderCell: (p) => <UserText>{p.value}</UserText> },
    ],
    [t, unitLabel, showCost, settings.baseCurrency],
  );

  return (
    <>
      <PageHeader
        title={t('inventory.adjustmentsTitle')}
        subtitle={t('inventory.adjustmentsSubtitle')}
        actions={
          <Button variant="contained" startIcon={<AddOutlined />} onClick={() => setOpen(true)}>
            {t('inventory.newAdjustment')}
          </Button>
        }
      />
      <Card>
        <FilterBar
          search={search}
          onSearchChange={setSearch}
          onReset={() => {
            setSearch('');
            setType('');
            setRange({ preset: 'MONTH', ...resolveRange('MONTH') });
          }}
        >
          <DateRangeFilter value={range} onChange={setRange} />
          <TextField select label={t('inventory.adjustmentType')} value={type} onChange={(e) => setType(e.target.value)} sx={{ width: 210 }} fullWidth={false}>
            <MenuItem value="">{t('inventory.allTypes')}</MenuItem>
            {ADJUSTMENT_TYPES.map((x) => (
              <MenuItem key={x} value={x}>
                {t(`inventory.adjustmentTypes.${x}`)}
              </MenuItem>
            ))}
          </TextField>
        </FilterBar>
        <Box sx={{ px: 1, pb: 1 }}>
          <DataTable server rows={data.data?.content} rowCount={data.data?.totalElements} columns={columns} loading={data.isFetching} paginationModel={pagination} onPaginationModelChange={setPagination} />
        </Box>
      </Card>
      <AdjustmentDialog
        open={open}
        onClose={() => setOpen(false)}
        onSaved={(saved) => {
          setOpen(false);
          notify.success(t('inventory.saved', { number: saved.adjustmentNo }));
          queryClient.invalidateQueries({ queryKey: ['inventory'] });
          queryClient.invalidateQueries({ queryKey: ['products'] });
          queryClient.invalidateQueries({ queryKey: ['product-search'] });
        }}
      />
    </>
  );
}
