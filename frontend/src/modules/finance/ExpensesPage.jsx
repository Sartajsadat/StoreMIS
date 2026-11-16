import { useMemo, useRef, useState } from 'react';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Box, Button, Card, IconButton, MenuItem, Stack, TextField, Tooltip, Typography } from '@mui/material';
import AddOutlined from '@mui/icons-material/AddOutlined';
import BlockOutlined from '@mui/icons-material/BlockOutlined';
import CategoryOutlined from '@mui/icons-material/CategoryOutlined';
import AttachFileOutlined from '@mui/icons-material/AttachFileOutlined';
import ReceiptLongOutlined from '@mui/icons-material/ReceiptLongOutlined';
import PageHeader from '../../components/display/PageHeader';
import DataTable from '../../components/data/DataTable';
import FilterBar from '../../components/data/FilterBar';
import DateRangeFilter from '../../components/data/DateRangeFilter';
import MoneyText from '../../components/display/MoneyText';
import StatusChip from '../../components/display/StatusChip';
import UserText from '../../components/display/UserText';
import Ltr from '../../components/display/Ltr';
import KpiCard from '../../components/display/KpiCard';
import ReasonDialog from '../../components/feedback/ReasonDialog';
import ExpenseDialog from './ExpenseDialog';
import ExpenseCategoriesDialog from './ExpenseCategoriesDialog';
import { categoryLabel } from './expenseUtils';
import { expenseService } from '../../services/expenseService';
import { errorMessage } from '../../services/apiClient';
import { useAuth } from '../../contexts/AuthContext';
import { useNotify } from '../../contexts/NotificationContext';
import { useDebounce } from '../../hooks/useDebounce';
import { resolveRange } from '../../utils/dateRange';
import { formatDate, formatMoney } from '../../utils/format';

export default function ExpensesPage() {
  const { t } = useTranslation();
  const notify = useNotify();
  const { isAdmin, user } = useAuth();
  const queryClient = useQueryClient();
  const [range, setRange] = useState({ preset: 'MONTH', ...resolveRange('MONTH') });
  const [search, setSearch] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [status, setStatus] = useState('');
  const [pagination, setPagination] = useState({ page: 0, pageSize: 20 });
  const [newOpen, setNewOpen] = useState(false);
  const [categoriesOpen, setCategoriesOpen] = useState(false);
  const [voidTarget, setVoidTarget] = useState(null);
  const [busy, setBusy] = useState(false);
  const fileInput = useRef(null);
  const [attachTarget, setAttachTarget] = useState(null);
  const q = useDebounce(search, 300);
  const params = { from: range.from, to: range.to, q: q || undefined, categoryId: categoryId || undefined, status: status || undefined, page: pagination.page, size: pagination.pageSize };
  const list = useQuery({ queryKey: ['expenses', params], queryFn: () => expenseService.list(params), placeholderData: keepPreviousData });
  const totals = useQuery({ queryKey: ['expenses', 'totals', range.from, range.to], queryFn: () => expenseService.totals({ from: range.from, to: range.to }), enabled: isAdmin });
  const categories = useQuery({ queryKey: ['expense-categories', 'all'], queryFn: () => expenseService.categories({}) });
  const refresh = () => ['expenses', 'cash-accounts', 'cash-transactions', 'cash-closing'].forEach((k) => queryClient.invalidateQueries({ queryKey: [k] }));

  const openAttachment = async (row) => {
    try {
      await expenseService.openAttachment(row.id);
    } catch (e) {
      notify.error(errorMessage(t, e));
    }
  };

  const columns = useMemo(
    () => [
      { field: 'expenseNo', headerName: t('expenses.number'), width: 145, renderCell: (p) => <Ltr>{p.value}</Ltr> },
      { field: 'expenseDate', headerName: t('expenses.date'), width: 105, renderCell: (p) => <Ltr>{formatDate(p.value)}</Ltr> },
      { field: 'categoryName', headerName: t('expenses.category'), width: 125, renderCell: (p) => categoryLabel(t, p.row) },
      { field: 'description', headerName: t('expenses.description'), flex: 1, minWidth: 130, renderCell: (p) => <UserText>{p.value}</UserText> },
      { field: 'amount', headerName: t('expenses.amount'), width: 130, align: 'right', headerAlign: 'right', renderCell: (p) => <MoneyText amount={p.value} currency={p.row.currency} fontWeight={600} /> },
      { field: 'cashAccountName', headerName: t('expenses.paidFrom'), width: 110 },
      { field: 'createdByName', headerName: t('expenses.recordedBy'), width: 120, renderCell: (p) => <UserText>{p.value}</UserText> },
      { field: 'status', headerName: t('common.status'), width: 100, renderCell: (p) => <StatusChip status={p.value} /> },
      {
        field: 'actions',
        headerName: '',
        width: 90,
        sortable: false,
        renderCell: (p) => (
          <Stack direction="row" spacing={0.5}>
            {p.row.hasAttachment ? (
              <Tooltip title={t('expenses.viewReceipt')}>
                <IconButton size="small" onClick={() => openAttachment(p.row)} aria-label={t('expenses.viewReceipt')}>
                  <ReceiptLongOutlined fontSize="small" />
                </IconButton>
              </Tooltip>
            ) : (
              p.row.status === 'POSTED' &&
              (isAdmin || p.row.createdBy === user?.id) && (
                <Tooltip title={t('expenses.attach')}>
                  <IconButton
                    size="small"
                    aria-label={t('expenses.attach')}
                    onClick={() => {
                      setAttachTarget(p.row);
                      fileInput.current?.click();
                    }}
                  >
                    <AttachFileOutlined fontSize="small" />
                  </IconButton>
                </Tooltip>
              )
            )}
            {isAdmin && p.row.status === 'POSTED' && (
              <Tooltip title={t('expenses.void')}>
                <IconButton size="small" color="error" onClick={() => setVoidTarget(p.row)} aria-label={t('expenses.void')}>
                  <BlockOutlined fontSize="small" />
                </IconButton>
              </Tooltip>
            )}
          </Stack>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, isAdmin, user?.id],
  );

  const byCurrency = totals.data?.byCurrency ?? {};
  return (
    <>
      <PageHeader
        title={t('expenses.title')}
        subtitle={t('expenses.subtitle')}
        actions={
          <>
            {isAdmin && (
              <Button variant="outlined" startIcon={<CategoryOutlined />} onClick={() => setCategoriesOpen(true)}>
                {t('expenses.categories')}
              </Button>
            )}
            <Button variant="contained" startIcon={<AddOutlined />} onClick={() => setNewOpen(true)}>
              {t('expenses.new')}
            </Button>
          </>
        }
      />
      {isAdmin && totals.data && (
        <Box sx={{ mb: 3, maxWidth: 420 }}>
          <KpiCard
            label={t('expenses.periodTotal')}
            value={formatMoney(totals.data.baseTotal, totals.data.baseCurrency)}
            caption={Object.entries(byCurrency).map(([c, v]) => formatMoney(v, c)).join(' · ') || t('expenses.none')}
          />
        </Box>
      )}
      <Card>
        <FilterBar
          search={search}
          onSearchChange={setSearch}
          onReset={() => {
            setSearch('');
            setCategoryId('');
            setStatus('');
            setRange({ preset: 'MONTH', ...resolveRange('MONTH') });
          }}
        >
          <DateRangeFilter value={range} onChange={setRange} />
          <TextField select size="small" label={t('expenses.category')} value={categoryId} onChange={(e) => setCategoryId(e.target.value)} sx={{ width: 170 }} fullWidth={false}>
            <MenuItem value="">{t('common.all')}</MenuItem>
            {(categories.data ?? []).map((c) => (
              <MenuItem key={c.id} value={c.id}>
                {categoryLabel(t, c)}
              </MenuItem>
            ))}
          </TextField>
          <TextField select size="small" label={t('common.status')} value={status} onChange={(e) => setStatus(e.target.value)} sx={{ width: 140 }} fullWidth={false}>
            <MenuItem value="">{t('common.all')}</MenuItem>
            <MenuItem value="POSTED">{t('status.POSTED')}</MenuItem>
            <MenuItem value="VOIDED">{t('status.VOIDED')}</MenuItem>
          </TextField>
        </FilterBar>
        {!isAdmin && (
          <Typography variant="caption" color="text.secondary" sx={{ px: 2 }}>
            {t('expenses.ownOnly')}
          </Typography>
        )}
        <Box sx={{ px: 1, pb: 1 }}>
          <DataTable server rows={list.data?.content} rowCount={list.data?.totalElements} columns={columns} loading={list.isFetching} paginationModel={pagination} onPaginationModelChange={setPagination} />
        </Box>
      </Card>
      <input
        ref={fileInput}
        hidden
        type="file"
        accept="image/png,image/jpeg,image/webp,application/pdf"
        onChange={async (e) => {
          const file = e.target.files?.[0];
          e.target.value = '';
          if (!file || !attachTarget) return;
          try {
            await expenseService.uploadAttachment(attachTarget.id, file);
            notify.success(t('expenses.attached'));
            refresh();
          } catch (err) {
            notify.error(errorMessage(t, err));
          }
        }}
      />
      <ExpenseDialog
        open={newOpen}
        onClose={() => setNewOpen(false)}
        onSaved={(e, attachError) => {
          setNewOpen(false);
          notify.success(t('expenses.saved', { number: e.expenseNo }));
          if (attachError) notify.error(attachError);
          refresh();
        }}
      />
      <ExpenseCategoriesDialog open={categoriesOpen} onClose={() => setCategoriesOpen(false)} />
      <ReasonDialog
        open={Boolean(voidTarget)}
        title={t('expenses.voidTitle', { number: voidTarget?.expenseNo ?? '' })}
        message={t('expenses.voidHint', { account: voidTarget?.cashAccountName ?? '' })}
        confirmLabel={t('expenses.void')}
        loading={busy}
        onClose={() => setVoidTarget(null)}
        onConfirm={async (reason) => {
          setBusy(true);
          try {
            await expenseService.void(voidTarget.id, reason);
            notify.success(t('expenses.voided'));
            setVoidTarget(null);
            refresh();
          } catch (e) {
            notify.error(errorMessage(t, e));
          } finally {
            setBusy(false);
          }
        }}
      />
    </>
  );
}
