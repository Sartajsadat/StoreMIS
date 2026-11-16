import { useMemo, useRef, useState } from 'react';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Avatar, Box, Button, Card, IconButton, ListItemIcon, Menu, MenuItem, Stack, TextField, Typography } from '@mui/material';
import AddOutlined from '@mui/icons-material/AddOutlined';
import MoreVert from '@mui/icons-material/MoreVert';
import EditOutlined from '@mui/icons-material/EditOutlined';
import ImageOutlined from '@mui/icons-material/ImageOutlined';
import HideImageOutlined from '@mui/icons-material/HideImageOutlined';
import ToggleOnOutlined from '@mui/icons-material/ToggleOnOutlined';
import ToggleOffOutlined from '@mui/icons-material/ToggleOffOutlined';
import Inventory2Outlined from '@mui/icons-material/Inventory2Outlined';
import PageHeader from '../../components/display/PageHeader';
import DataTable from '../../components/data/DataTable';
import FilterBar from '../../components/data/FilterBar';
import StatusChip from '../../components/display/StatusChip';
import MoneyText from '../../components/display/MoneyText';
import QtyText from '../../components/display/QtyText';
import UserText from '../../components/display/UserText';
import Ltr from '../../components/display/Ltr';
import ConfirmDialog from '../../components/feedback/ConfirmDialog';
import ProductFormDialog from './ProductFormDialog';
import { productService } from '../../services/productService';
import { catalogService } from '../../services/catalogService';
import { errorMessage } from '../../services/apiClient';
import { useNotify } from '../../contexts/NotificationContext';
import { useAuth } from '../../contexts/AuthContext';
import { useSettings } from '../../contexts/SettingsContext';
import { useDebounce } from '../../hooks/useDebounce';
import { useUnitLabel } from '../../hooks/useUnitLabel';

export default function ProductListPage() {
  const { t } = useTranslation();
  const notify = useNotify();
  const { isAdmin, hasPermission } = useAuth();
  const { settings } = useSettings();
  const unitLabel = useUnitLabel();
  const queryClient = useQueryClient();
  const fileInput = useRef(null);
  const [search, setSearch] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [active, setActive] = useState('true');
  const [stockStatus, setStockStatus] = useState('');
  const [pagination, setPagination] = useState({ page: 0, pageSize: 20 });
  const [sortModel, setSortModel] = useState([]);
  const [menu, setMenu] = useState({ anchor: null, product: null });
  const [dialog, setDialog] = useState({ type: null, product: null });
  const [imageTarget, setImageTarget] = useState(null);
  const q = useDebounce(search, 300);
  const showCost = hasPermission('VIEW_COST_PRICES');

  const params = {
    q: q || undefined,
    categoryId: categoryId || undefined,
    active: active === '' ? undefined : active,
    status: stockStatus || undefined,
    page: pagination.page,
    size: pagination.pageSize,
    sort: sortModel[0] ? `${sortModel[0].field},${sortModel[0].sort}` : undefined,
  };
  const products = useQuery({ queryKey: ['products', params], queryFn: () => productService.list(params), placeholderData: keepPreviousData });
  const categories = useQuery({ queryKey: ['categories', 'all'], queryFn: () => catalogService.categories() });
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['products'] });
    queryClient.invalidateQueries({ queryKey: ['inventory'] });
  };
  const closeMenu = () => setMenu({ anchor: null, product: null });

  const onImage = async (file) => {
    if (!file || !imageTarget) return;
    try {
      await productService.uploadImage(imageTarget.id, file);
      notify.success(t('products.imageUpdated'));
      refresh();
    } catch (e) {
      notify.error(errorMessage(t, e));
    } finally {
      setImageTarget(null);
      if (fileInput.current) fileInput.current.value = '';
    }
  };

  const columns = useMemo(
    () => [
      {
        field: 'name',
        headerName: t('products.name'),
        flex: 1.6,
        minWidth: 240,
        renderCell: (p) => (
          <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', height: '100%' }}>
            <Avatar variant="rounded" src={p.row.imageUrl ?? undefined} sx={{ width: 40, height: 40, bgcolor: 'action.hover', color: 'text.secondary' }}>
              <Inventory2Outlined fontSize="small" />
            </Avatar>
            <Box sx={{ minWidth: 0, lineHeight: 1.3 }}>
              <Typography variant="body2" noWrap sx={{ fontWeight: 600 }}>
                <UserText>{p.row.name}</UserText>
              </Typography>
              <Typography variant="caption" color="text.secondary" noWrap component="div">
                {p.row.code && <Ltr>{p.row.code}</Ltr>}
                {p.row.code && p.row.brand ? ' · ' : ''}
                {p.row.brand}
              </Typography>
            </Box>
          </Stack>
        ),
      },
      { field: 'category', headerName: t('products.category'), flex: 1, minWidth: 140, valueGetter: (v) => v?.name, renderCell: (p) => <UserText>{p.value}</UserText> },
      { field: 'unit', headerName: t('products.unit'), width: 110, sortable: false, valueGetter: (v) => unitLabel(v) },
      {
        field: 'quantity',
        headerName: t('products.stock'),
        width: 130,
        align: 'right',
        headerAlign: 'right',
        valueGetter: (_v, row) => row.stock?.quantity,
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
          ]
        : []),
      { field: 'stockStatus', headerName: t('products.status'), width: 150, sortable: false, valueGetter: (_v, row) => row.stock?.status, renderCell: (p) => <StatusChip status={p.value} /> },
      { field: 'active', headerName: t('common.status'), width: 120, sortable: false, renderCell: (p) => <StatusChip status={p.value ? 'ACTIVE' : 'INACTIVE'} /> },
      ...(isAdmin
        ? [
            {
              field: 'actions',
              headerName: '',
              width: 60,
              sortable: false,
              renderCell: (p) => (
                <IconButton size="small" aria-label={t('common.actions')} onClick={(e) => setMenu({ anchor: e.currentTarget, product: p.row })}>
                  <MoreVert fontSize="small" />
                </IconButton>
              ),
            },
          ]
        : []),
    ],
    [t, unitLabel, showCost, isAdmin, settings.baseCurrency],
  );

  const target = dialog.product;

  return (
    <>
      <PageHeader
        title={t('products.title')}
        subtitle={t('products.subtitle')}
        actions={
          isAdmin && (
            <Button variant="contained" startIcon={<AddOutlined />} onClick={() => setDialog({ type: 'form', product: null })}>
              {t('products.add')}
            </Button>
          )
        }
      />
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
            setActive('true');
            setStockStatus('');
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
          <TextField select label={t('products.status')} value={stockStatus} onChange={(e) => setStockStatus(e.target.value)} sx={{ width: 160 }} fullWidth={false}>
            <MenuItem value="">{t('products.allStock')}</MenuItem>
            {['IN_STOCK', 'LOW_STOCK', 'OUT_OF_STOCK'].map((s) => (
              <MenuItem key={s} value={s}>
                {t(`status.${s}`)}
              </MenuItem>
            ))}
          </TextField>
          <TextField select label={t('common.status')} value={active} onChange={(e) => setActive(e.target.value)} sx={{ width: 130 }} fullWidth={false}>
            <MenuItem value="true">{t('products.activeOnly')}</MenuItem>
            <MenuItem value="false">{t('products.inactiveOnly')}</MenuItem>
            <MenuItem value="">{t('products.allProducts')}</MenuItem>
          </TextField>
        </FilterBar>
        <Box sx={{ px: 1, pb: 1 }}>
          <DataTable
            server
            rows={products.data?.content}
            rowCount={products.data?.totalElements}
            columns={columns}
            loading={products.isFetching}
            paginationModel={pagination}
            onPaginationModelChange={setPagination}
            sortModel={sortModel}
            onSortModelChange={setSortModel}
            rowHeight={60}
          />
        </Box>
      </Card>

      <Menu anchorEl={menu.anchor} open={Boolean(menu.anchor)} onClose={closeMenu}>
        <MenuItem
          onClick={() => {
            setDialog({ type: 'form', product: menu.product });
            closeMenu();
          }}
        >
          <ListItemIcon>
            <EditOutlined fontSize="small" />
          </ListItemIcon>
          {t('common.edit')}
        </MenuItem>
        <MenuItem
          onClick={() => {
            setImageTarget(menu.product);
            closeMenu();
            fileInput.current?.click();
          }}
        >
          <ListItemIcon>
            <ImageOutlined fontSize="small" />
          </ListItemIcon>
          {t('products.changeImage')}
        </MenuItem>
        {menu.product?.imageUrl && (
          <MenuItem
            onClick={async () => {
              const p = menu.product;
              closeMenu();
              try {
                await productService.removeImage(p.id);
                refresh();
              } catch (e) {
                notify.error(errorMessage(t, e));
              }
            }}
          >
            <ListItemIcon>
              <HideImageOutlined fontSize="small" />
            </ListItemIcon>
            {t('products.removeImage')}
          </MenuItem>
        )}
        {menu.product && (
          <MenuItem
            onClick={() => {
              setDialog({ type: 'status', product: menu.product });
              closeMenu();
            }}
            sx={{ color: menu.product.active ? 'error.main' : 'success.main' }}
          >
            <ListItemIcon sx={{ color: 'inherit' }}>{menu.product.active ? <ToggleOffOutlined fontSize="small" /> : <ToggleOnOutlined fontSize="small" />}</ListItemIcon>
            {menu.product.active ? t('products.deactivate') : t('products.activate')}
          </MenuItem>
        )}
      </Menu>
      <input ref={fileInput} type="file" accept="image/png,image/jpeg,image/webp" hidden onChange={(e) => onImage(e.target.files?.[0])} />

      <ProductFormDialog
        open={dialog.type === 'form'}
        product={target}
        onClose={() => setDialog({ type: null, product: null })}
        save={(body) => (target ? productService.update(target.id, body) : productService.create(body))}
        onSaved={() => {
          notify.success(t('products.saved'));
          setDialog({ type: null, product: null });
          refresh();
        }}
      />
      <ConfirmDialog
        open={dialog.type === 'status'}
        title={target?.active ? t('products.deactivate') : t('products.activate')}
        message={target?.active ? t('products.deactivateHint') : target?.name}
        color={target?.active ? 'error' : 'success'}
        confirmLabel={target?.active ? t('products.deactivate') : t('products.activate')}
        onClose={() => setDialog({ type: null, product: null })}
        onConfirm={async () => {
          try {
            await productService.setActive(target.id, !target.active);
            notify.success(t('products.statusChanged'));
            refresh();
          } catch (e) {
            notify.error(errorMessage(t, e));
          } finally {
            setDialog({ type: null, product: null });
          }
        }}
      />
    </>
  );
}
