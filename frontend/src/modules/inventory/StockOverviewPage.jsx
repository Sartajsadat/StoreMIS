import { useMemo, useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Box, Card, Grid, MenuItem, TextField, Typography } from '@mui/material';
import Inventory2Outlined from '@mui/icons-material/Inventory2Outlined';
import WarningAmberOutlined from '@mui/icons-material/WarningAmberOutlined';
import RemoveShoppingCartOutlined from '@mui/icons-material/RemoveShoppingCartOutlined';
import AccountBalanceWalletOutlined from '@mui/icons-material/AccountBalanceWalletOutlined';
import PageHeader from '../../components/display/PageHeader';
import KpiCard from '../../components/display/KpiCard';
import DataTable from '../../components/data/DataTable';
import FilterBar from '../../components/data/FilterBar';
import StatusChip from '../../components/display/StatusChip';
import MoneyText from '../../components/display/MoneyText';
import QtyText from '../../components/display/QtyText';
import UserText from '../../components/display/UserText';
import Ltr from '../../components/display/Ltr';
import { inventoryService } from '../../services/inventoryService';
import { catalogService } from '../../services/catalogService';
import { useAuth } from '../../contexts/AuthContext';
import { useSettings } from '../../contexts/SettingsContext';
import { useDebounce } from '../../hooks/useDebounce';
import { useUnitLabel } from '../../hooks/useUnitLabel';
import { formatMoney, formatNumber } from '../../utils/format';

/** Shared table for Stock Overview and Low Stock. */
export function StockTable({ fixedStatus, statusOptions, defaultStatus = '' }) {
  const { t } = useTranslation();
  const { hasPermission } = useAuth();
  const { settings } = useSettings();
  const unitLabel = useUnitLabel();
  const [search, setSearch] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [status, setStatus] = useState(defaultStatus);
  const [pagination, setPagination] = useState({ page: 0, pageSize: 20 });
  const [sortModel, setSortModel] = useState([]);
  const q = useDebounce(search, 300);
  const showCost = hasPermission('VIEW_COST_PRICES');

  const params = {
    q: q || undefined,
    categoryId: categoryId || undefined,
    status: fixedStatus ?? (status || undefined),
    page: pagination.page,
    size: pagination.pageSize,
    sort: sortModel[0] ? `${sortModel[0].field},${sortModel[0].sort}` : fixedStatus || status ? 'quantity,asc' : undefined,
  };
  const stock = useQuery({ queryKey: ['inventory', 'stock', params], queryFn: () => inventoryService.stock(params), placeholderData: keepPreviousData });
  const categories = useQuery({ queryKey: ['categories', 'all'], queryFn: () => catalogService.categories() });

  const columns = useMemo(
    () => [
      {
        field: 'name',
        headerName: t('products.name'),
        flex: 1.5,
        minWidth: 220,
        renderCell: (p) => (
          <Box sx={{ lineHeight: 1.3, display: 'flex', flexDirection: 'column', justifyContent: 'center', height: '100%' }}>
            <Typography variant="body2" sx={{ fontWeight: 600 }} noWrap>
              <UserText>{p.row.name}</UserText>
            </Typography>
            {p.row.code && (
              <Typography variant="caption" color="text.secondary">
                <Ltr>{p.row.code}</Ltr>
              </Typography>
            )}
          </Box>
        ),
      },
      { field: 'category', headerName: t('products.category'), flex: 1, minWidth: 140, valueGetter: (v) => v?.name, renderCell: (p) => <UserText>{p.value}</UserText> },
      {
        field: 'quantity',
        headerName: t('products.stock'),
        width: 150,
        align: 'right',
        headerAlign: 'right',
        valueGetter: (_v, row) => row.stock?.quantity,
        renderCell: (p) => <QtyText value={p.value} unit={unitLabel(p.row.unit)} />,
      },
      {
        field: 'minStockLevel',
        headerName: t('inventory.minLevel'),
        width: 110,
        sortable: false,
        align: 'right',
        headerAlign: 'right',
        renderCell: (p) => <QtyText value={p.value} />,
      },
      ...(showCost
        ? [
            {
              field: 'avgCost',
              headerName: t('products.avgCost'),
              width: 140,
              sortable: false,
              align: 'right',
              headerAlign: 'right',
              valueGetter: (_v, row) => row.stock?.avgCost,
              renderCell: (p) => <MoneyText amount={p.value} currency={settings.baseCurrency} />,
            },
            {
              field: 'value',
              headerName: t('products.stockValue'),
              width: 150,
              align: 'right',
              headerAlign: 'right',
              valueGetter: (_v, row) => row.stock?.value,
              renderCell: (p) => <MoneyText amount={p.value} currency={settings.baseCurrency} />,
            },
          ]
        : []),
      { field: 'status', headerName: t('products.status'), width: 150, sortable: false, valueGetter: (_v, row) => row.stock?.status, renderCell: (p) => <StatusChip status={p.value} /> },
    ],
    [t, unitLabel, showCost, settings.baseCurrency],
  );

  return (
    <Card>
      <FilterBar
        search={search}
        searchPlaceholder={t('products.searchPlaceholder')}
        onSearchChange={(v) => {
          setSearch(v);
          setPagination((p) => ({ ...p, page: 0 }));
        }}
        onReset={() => {
          setSearch('');
          setCategoryId('');
          setStatus(defaultStatus);
        }}
      >
        <TextField select label={t('products.category')} value={categoryId} onChange={(e) => setCategoryId(e.target.value)} sx={{ width: 190 }} fullWidth={false}>
          <MenuItem value="">{t('products.allCategories')}</MenuItem>
          {(categories.data ?? []).map((c) => (
            <MenuItem key={c.id} value={c.id}>
              {c.name}
            </MenuItem>
          ))}
        </TextField>
        {!fixedStatus && (
          <TextField select label={t('products.status')} value={status} onChange={(e) => setStatus(e.target.value)} sx={{ width: 170 }} fullWidth={false}>
            {(statusOptions ?? ['', 'IN_STOCK', 'LOW_STOCK', 'OUT_OF_STOCK']).map((s) => (
              <MenuItem key={s || 'all'} value={s}>
                {s ? t(`status.${s}`) : t('products.allStock')}
              </MenuItem>
            ))}
          </TextField>
        )}
      </FilterBar>
      <Box sx={{ px: 1, pb: 1 }}>
        <DataTable
          server
          rows={stock.data?.content}
          rowCount={stock.data?.totalElements}
          columns={columns}
          loading={stock.isFetching}
          paginationModel={pagination}
          onPaginationModelChange={setPagination}
          sortModel={sortModel}
          onSortModelChange={setSortModel}
          rowHeight={56}
        />
      </Box>
    </Card>
  );
}

export default function StockOverviewPage() {
  const { t } = useTranslation();
  const { hasPermission } = useAuth();
  const summary = useQuery({ queryKey: ['inventory', 'summary'], queryFn: inventoryService.summary });
  const s = summary.data;
  return (
    <>
      <PageHeader title={t('inventory.overviewTitle')} subtitle={t('inventory.overviewSubtitle')} />
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <KpiCard label={t('inventory.activeProducts')} value={s ? formatNumber(s.activeProducts) : ''} loading={!s} icon={Inventory2Outlined} />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <KpiCard label={t('inventory.lowStock')} value={s ? formatNumber(s.lowStock) : ''} loading={!s} icon={WarningAmberOutlined} color="warning" />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <KpiCard label={t('inventory.outOfStock')} value={s ? formatNumber(s.outOfStock) : ''} loading={!s} icon={RemoveShoppingCartOutlined} color="error" />
        </Grid>
        {hasPermission('VIEW_COST_PRICES') && (
          <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
            <KpiCard
              label={t('inventory.inventoryValue')}
              value={s ? formatMoney(s.inventoryValue, s.baseCurrency) : ''}
              loading={!s}
              icon={AccountBalanceWalletOutlined}
              color="success"
              caption={t('inventory.inventoryValueCaption')}
            />
          </Grid>
        )}
      </Grid>
      <StockTable />
    </>
  );
}
