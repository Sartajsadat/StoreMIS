import { useMemo, useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Box, Card, Chip, MenuItem, TextField, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import PageHeader from '../../components/display/PageHeader';
import DataTable from '../../components/data/DataTable';
import FilterBar from '../../components/data/FilterBar';
import DateRangeFilter from '../../components/data/DateRangeFilter';
import MoneyText from '../../components/display/MoneyText';
import QtyText from '../../components/display/QtyText';
import UserText from '../../components/display/UserText';
import Ltr from '../../components/display/Ltr';
import ProductPicker from '../../components/inventory/ProductPicker';
import { inventoryService, MOVEMENT_TYPES } from '../../services/inventoryService';
import { useAuth } from '../../contexts/AuthContext';
import { useSettings } from '../../contexts/SettingsContext';
import { useDebounce } from '../../hooks/useDebounce';
import { resolveRange } from '../../utils/dateRange';
import { formatDate, formatDateTime } from '../../utils/format';

export default function StockMovementsPage() {
  const { t } = useTranslation();
  const theme = useTheme();
  const { hasPermission } = useAuth();
  const { settings } = useSettings();
  const [range, setRange] = useState({ preset: 'MONTH', ...resolveRange('MONTH') });
  const [product, setProduct] = useState(null);
  const [type, setType] = useState('');
  const [search, setSearch] = useState('');
  const [pagination, setPagination] = useState({ page: 0, pageSize: 20 });
  const q = useDebounce(search, 300);
  const showCost = hasPermission('VIEW_COST_PRICES');

  const params = { from: range.from, to: range.to, productId: product?.id, type: type || undefined, q: q || undefined, page: pagination.page, size: pagination.pageSize };
  const data = useQuery({ queryKey: ['inventory', 'movements', params], queryFn: () => inventoryService.movements(params), placeholderData: keepPreviousData });

  const columns = useMemo(
    () => [
      {
        field: 'businessDate',
        headerName: t('inventory.date'),
        width: 150,
        renderCell: (p) => (
          <Box sx={{ lineHeight: 1.3, display: 'flex', flexDirection: 'column', justifyContent: 'center', height: '100%' }}>
            <Ltr>{formatDate(p.value)}</Ltr>
            <Typography variant="caption" color="text.secondary">
              <Ltr>{formatDateTime(p.row.createdAt).slice(11)}</Ltr>
            </Typography>
          </Box>
        ),
      },
      { field: 'referenceNo', headerName: t('inventory.reference'), width: 160, renderCell: (p) => <Ltr>{p.value}</Ltr> },
      { field: 'productName', headerName: t('inventory.product'), flex: 1, minWidth: 180, renderCell: (p) => <UserText>{p.value}</UserText> },
      {
        field: 'type',
        headerName: t('inventory.type'),
        width: 170,
        renderCell: (p) => <Chip size="small" variant="outlined" color={Number(p.row.quantity) > 0 ? 'success' : 'error'} label={t(`inventory.movementTypes.${p.value}`)} />,
      },
      {
        field: 'quantity',
        headerName: t('inventory.quantity'),
        width: 120,
        align: 'right',
        headerAlign: 'right',
        renderCell: (p) => <QtyText value={p.value} signed color={Number(p.value) > 0 ? theme.palette.success.main : theme.palette.error.main} />,
      },
      { field: 'quantityAfter', headerName: t('inventory.balanceAfter'), width: 130, align: 'right', headerAlign: 'right', renderCell: (p) => <QtyText value={p.value} /> },
      ...(showCost
        ? [
            { field: 'unitCost', headerName: t('inventory.unitCost'), width: 140, align: 'right', headerAlign: 'right', renderCell: (p) => <MoneyText amount={p.value} currency={settings.baseCurrency} /> },
            { field: 'totalCost', headerName: t('inventory.totalCost'), width: 150, align: 'right', headerAlign: 'right', renderCell: (p) => <MoneyText amount={p.value} currency={settings.baseCurrency} /> },
          ]
        : []),
      { field: 'createdByName', headerName: t('inventory.by'), width: 150, renderCell: (p) => <UserText>{p.value}</UserText> },
    ],
    [t, showCost, settings.baseCurrency, theme],
  );

  return (
    <>
      <PageHeader title={t('inventory.movementsTitle')} subtitle={t('inventory.movementsSubtitle')} />
      <Card>
        <FilterBar
          search={search}
          searchPlaceholder={t('inventory.reference')}
          onSearchChange={setSearch}
          onReset={() => {
            setSearch('');
            setType('');
            setProduct(null);
            setRange({ preset: 'MONTH', ...resolveRange('MONTH') });
          }}
        >
          <DateRangeFilter value={range} onChange={setRange} />
          <Box sx={{ width: 280 }}>
            <ProductPicker value={product} onChange={setProduct} />
          </Box>
          <TextField select label={t('inventory.type')} value={type} onChange={(e) => setType(e.target.value)} sx={{ width: 190 }} fullWidth={false}>
            <MenuItem value="">{t('inventory.allTypes')}</MenuItem>
            {MOVEMENT_TYPES.map((m) => (
              <MenuItem key={m} value={m}>
                {t(`inventory.movementTypes.${m}`)}
              </MenuItem>
            ))}
          </TextField>
        </FilterBar>
        <Box sx={{ px: 1, pb: 1 }}>
          <DataTable server rows={data.data?.content} rowCount={data.data?.totalElements} columns={columns} loading={data.isFetching} paginationModel={pagination} onPaginationModelChange={setPagination} rowHeight={56} />
        </Box>
      </Card>
    </>
  );
}
