import { useMemo, useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Box, Card, Chip, FormControlLabel, Grid, MenuItem, Stack, Switch, TextField, Typography } from '@mui/material';
import DataTable from '../../components/data/DataTable';
import FilterBar from '../../components/data/FilterBar';
import DateRangeFilter from '../../components/data/DateRangeFilter';
import ExportButtons from '../../components/data/ExportButtons';
import MoneyText from '../../components/display/MoneyText';
import QtyText from '../../components/display/QtyText';
import UserText from '../../components/display/UserText';
import Ltr from '../../components/display/Ltr';
import StatusChip from '../../components/display/StatusChip';
import ChartCard from '../../components/charts/ChartCard';
import TrendLineChart from '../../components/charts/TrendLineChart';
import ComparisonBarChart from '../../components/charts/ComparisonBarChart';
import ShareDonutChart from '../../components/charts/ShareDonutChart';
import { reportService } from '../../services/reportService';
import { catalogService } from '../../services/catalogService';
import { expenseService } from '../../services/expenseService';
import { CASH_TXN_TYPES } from '../../services/cashService';
import { errorMessage } from '../../services/apiClient';
import { useCashAccounts, useCurrencies } from '../../hooks/useCurrency';
import { useDebounce } from '../../hooks/useDebounce';
import { useNotify } from '../../contexts/NotificationContext';
import { resolveRange } from '../../utils/dateRange';
import { formatDate, formatMoney, formatNumber } from '../../utils/format';
import { downloadXlsx } from '../../utils/xlsx';
import { categoryLabel } from '../finance/expenseUtils';
import { REPORTS } from './reportCatalog';
import { balancesText, cellText, columnLabel, excelRows } from './reportFormat';

const MOVEMENT_TYPES = ['OPENING', 'PURCHASE', 'PURCHASE_CANCEL', 'PURCHASE_RETURN', 'SALE', 'SALE_CANCEL', 'SALE_RETURN', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'DAMAGE'];
const EXPORT_LIMIT = 10000;

/** Request parameters of a report from its filter state. */
export function reportParams(key, state) {
  const def = REPORTS[key];
  const f = state.filters ?? {};
  const p = { q: state.q || undefined };
  if (!def.noDates) {
    p.from = state.range.from;
    p.to = state.range.to;
  }
  if (f.category) p.categoryId = f.category;
  if (f.expenseCategory) p.categoryId = f.expenseCategory;
  if (f.account) p.accountId = f.account;
  if (f.currency) p.currency = f.currency;
  if (f.bestSort) p.type = f.bestSort;
  if (f.movementType) p.type = f.movementType;
  if (f.cashType) p.type = f.cashType;
  if (f.customerType) p.type = f.customerType;
  for (const s of ['paymentStatus', 'purchasePaymentStatus', 'stockStatus', 'expenseStatus']) if (f[s]) p.status = f[s];
  if (f.nonZero) p.nonZero = true;
  return p;
}

function initialRange(def) {
  if (!def.preset) return resolveRange('MONTH');
  if (def.preset === 'CUSTOM') return { preset: 'CUSTOM', ...def.custom() };
  return { preset: def.preset, ...resolveRange(def.preset) };
}

/** One report: filters, totals, optional chart, the table and Excel / PDF / print. */
export default function ReportView({ reportKey }) {
  const { t } = useTranslation();
  const notify = useNotify();
  const def = REPORTS[reportKey];
  const [range, setRange] = useState(() => initialRange(def));
  const [search, setSearch] = useState('');
  const [filters, setFilters] = useState(def.defaults ?? {});
  const [pagination, setPagination] = useState({ page: 0, pageSize: 50 });
  const [exporting, setExporting] = useState(false);
  const q = useDebounce(search, 300);
  const base = reportParams(reportKey, { range, q, filters });
  const params = def.paged === false ? base : { ...base, page: pagination.page, size: pagination.pageSize };
  const report = useQuery({ queryKey: ['reports', reportKey, params], queryFn: () => reportService.run(reportKey, params), placeholderData: keepPreviousData });
  const r = report.data;
  const title = t(`reports.names.${reportKey}`);
  const ctx = { t, base: r?.baseCurrency };

  const columns = useMemo(
    () =>
      (r?.columns ?? []).map((col) => ({
        field: col.key,
        headerName: columnLabel(t, col.key),
        minWidth: { MONEY: 130, QTY: 110, DATE: 110, USER_TEXT: 170, BALANCES: 190, PERCENT: 90, NUMBER: 90 }[col.type] ?? 120,
        flex: col.type === 'USER_TEXT' ? 1 : undefined,
        align: ['MONEY', 'QTY', 'NUMBER', 'PERCENT'].includes(col.type) ? 'right' : 'left',
        headerAlign: ['MONEY', 'QTY', 'NUMBER', 'PERCENT'].includes(col.type) ? 'right' : 'left',
        sortable: def.paged === false,
        renderCell: (p) => <Cell col={col} row={p.row} ctx={ctx} />,
      })),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [r?.columns, t, r?.baseCurrency],
  );
  const rows = useMemo(() => (r?.rows ?? []).map((row, i) => ({ ...row, _rid: `${pagination.page}-${i}` })), [r?.rows, pagination.page]);

  const fetchAll = () => reportService.run(reportKey, { ...base, page: 0, size: EXPORT_LIMIT });
  const exportExcel = async () => {
    setExporting(true);
    try {
      const all = await fetchAll();
      if (all.totalRows > all.rows.length) notify.warning?.(t('reports.truncated', { count: all.rows.length }));
      const { rows: xr, widths, boldRows } = excelRows({ result: all, title, t });
      const rtl = document.documentElement.dir === 'rtl';
      const period = def.noDates ? formatDate(new Date()) : `${all.from}_${all.to}`;
      downloadXlsx(`${reportKey}_${period}`, { sheetName: title, rows: xr, widths, rtl, boldRows });
    } catch (e) {
      notify.error(errorMessage(t, e));
    } finally {
      setExporting(false);
    }
  };
  const openPrint = (pdf) => {
    const qs = new URLSearchParams({ ...Object.fromEntries(Object.entries(base).filter(([, v]) => v !== undefined && v !== '')), autoprint: '1', ...(pdf && { pdf: '1' }) });
    window.open(`/print/reports/${reportKey}?${qs}`, '_blank', 'noopener,width=1100,height=900');
  };
  const setFilter = (k, v) => {
    setFilters((f) => ({ ...f, [k]: v }));
    setPagination((p) => ({ ...p, page: 0 }));
  };

  return (
    <Stack spacing={3}>
      <Card>
        <FilterBar
          search={def.noSearch ? undefined : search}
          onSearchChange={def.noSearch ? undefined : (v) => (setSearch(v), setPagination((p) => ({ ...p, page: 0 })))}
          onReset={() => {
            setSearch('');
            setFilters(def.defaults ?? {});
            setRange(initialRange(def));
          }}
          actions={<ExportButtons disabled={!r || exporting} onExcel={exportExcel} onPdf={() => openPrint(true)} onPrint={() => openPrint(false)} />}
        >
          {!def.noDates && <DateRangeFilter value={range} onChange={(v) => (setRange(v), setPagination((p) => ({ ...p, page: 0 })))} />}
          {def.filters.map((f) => (
            <Filter key={f} name={f} value={filters[f]} onChange={(v) => setFilter(f, v)} />
          ))}
        </FilterBar>
        {def.noDates && (
          <Typography variant="caption" color="text.secondary" sx={{ px: 2, display: 'block', mt: -1, mb: 1 }}>
            {t('reports.asOfToday')}
          </Typography>
        )}
      </Card>
      {r && <Totals result={r} />}
      {r && def.accounts && <AccountsSummary rows={r.meta?.byAccount ?? []} />}
      {r && def.chart && <ReportChart kind={def.chart} result={r} reportKey={reportKey} />}
      <Card>
        <Box sx={{ px: 1, py: 1 }}>
          <DataTable
            server={def.paged !== false}
            rows={rows}
            rowCount={r?.totalRows}
            getRowId={(row) => row._rid}
            columns={columns}
            loading={report.isFetching}
            paginationModel={def.paged === false ? undefined : pagination}
            onPaginationModelChange={def.paged === false ? undefined : setPagination}
            pageSizeOptions={[25, 50, 100]}
          />
        </Box>
      </Card>
    </Stack>
  );
}

function Cell({ col, row, ctx }) {
  const v = row[col.key];
  if (v === null || v === undefined || v === '') return '';
  switch (col.type) {
    case 'MONEY':
      return <MoneyText amount={v} currency={(col.currencyField && row[col.currencyField]) || ctx.base} color={Number(v) < 0 ? 'error.main' : undefined} />;
    case 'QTY':
      return <QtyText value={v} unit={col.unitField ? row[col.unitField] : undefined} />;
    case 'USER_TEXT':
      return <UserText>{v}</UserText>;
    case 'CODE':
    case 'DATE':
    case 'DATETIME':
    case 'NUMBER':
    case 'PERCENT':
      return <Ltr>{cellText(col, row, ctx)}</Ltr>;
    case 'ENUM':
      return col.enumPrefix === 'status' ? <StatusChip status={v} /> : cellText(col, row, ctx);
    case 'BALANCES':
      return <Balances map={v} />;
    default:
      return cellText(col, row, ctx);
  }
}

function Balances({ map }) {
  const entries = Object.entries(map ?? {});
  if (entries.length === 0) return '';
  return (
    <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', gap: 0.5 }}>
      {entries.map(([cur, v]) => (
        <Chip key={cur} size="small" variant="outlined" color={Number(v) < 0 ? 'success' : 'default'} label={<Ltr>{formatMoney(Math.abs(Number(v)), cur)}</Ltr>} />
      ))}
    </Stack>
  );
}

/** Totals over all matching rows, shown as small cards. */
function Totals({ result }) {
  const { t } = useTranslation();
  const items = [];
  const colOf = (k) => result.columns.find((c) => c.key === k);
  for (const [k, v] of Object.entries(result.totals ?? {})) {
    if (v === null || v === undefined) continue;
    const label = t(`reports.totals.${k}`, columnLabel(t, k));
    if (k === 'byCurrency') {
      for (const [cur, m] of Object.entries(v)) {
        for (const [field, amount] of Object.entries(m)) items.push({ label: `${columnLabel(t, field)} (${cur})`, value: formatMoney(amount, cur) });
      }
    } else if (typeof v === 'object') {
      items.push({ label, value: balancesText(v) || '—' });
    } else {
      const col = colOf(k);
      const type = col?.type ?? (k === 'count' ? 'NUMBER' : 'MONEY');
      if (type === 'MONEY' && col?.currencyField) continue;
      items.push({ label, value: type === 'MONEY' ? formatMoney(v, result.baseCurrency) : type === 'PERCENT' ? `${formatNumber(v, { maxDecimals: 1 })}%` : formatNumber(v) });
    }
  }
  if (items.length === 0) return null;
  return (
    <Grid container spacing={2}>
      {items.map((i) => (
        <Grid key={i.label} size={{ xs: 6, md: 4, lg: 2.4 }}>
          <Card sx={{ p: 2, height: '100%' }}>
            <Typography variant="caption" color="text.secondary" component="div">
              {i.label}
            </Typography>
            <Typography variant="h6" sx={{ mt: 0.5 }}>
              <Ltr>{i.value}</Ltr>
            </Typography>
          </Card>
        </Grid>
      ))}
    </Grid>
  );
}

function AccountsSummary({ rows }) {
  const { t } = useTranslation();
  return (
    <Grid container spacing={2}>
      {rows.map((a) => (
        <Grid key={a.accountId} size={{ xs: 12, md: 6, lg: 4 }}>
          <Card sx={{ p: 2 }}>
            <Typography variant="subtitle2">{a.accountName}</Typography>
            {['opening', 'totalIn', 'totalOut', 'closing'].map((k) => (
              <Stack key={k} direction="row" sx={{ justifyContent: 'space-between', mt: 0.5 }}>
                <Typography variant="body2" color="text.secondary">
                  {t(`reports.totals.${k}`)}
                </Typography>
                <MoneyText amount={a[k]} currency={a.currency} fontWeight={k === 'closing' ? 700 : undefined} color={k === 'totalIn' ? 'success.main' : k === 'totalOut' ? 'error.main' : undefined} />
              </Stack>
            ))}
          </Card>
        </Grid>
      ))}
    </Grid>
  );
}

function ReportChart({ kind, result, reportKey }) {
  const { t } = useTranslation();
  const base = result.baseCurrency;
  const money = (v) => formatMoney(v, base);
  const num = (row, k) => Number(row[k] ?? 0);
  if (kind === 'trend' || kind === 'bars') {
    const unit = result.meta?.unit;
    const data = result.rows.map((row) => ({
      period: unit === 'month' ? formatDate(row.period, 'YYYY-MM') : unit === 'year' ? formatDate(row.period, 'YYYY') : formatDate(row.period, 'MM-DD'),
      netSales: num(row, 'netSales'),
      grossProfit: num(row, 'grossProfit'),
    }));
    const series = [
      { key: 'netSales', label: t('reports.columns.netSales') },
      { key: 'grossProfit', label: t('reports.columns.grossProfit') },
    ];
    const table = { columns: [{ key: 'period', label: t('reports.columns.period') }, ...series.map((s) => ({ ...s, format: money }))], rows: data };
    return (
      <ChartCard title={t(`reports.names.${reportKey}`)} subheader={base} table={table} empty={data.length === 0}>
        {kind === 'trend' && data.length > 1 ? (
          <TrendLineChart data={data} xKey="period" series={series} tooltipFormatter={money} />
        ) : (
          <ComparisonBarChart data={data} xKey="period" series={series} tooltipFormatter={money} />
        )}
      </ChartCard>
    );
  }
  if (kind === 'type') {
    const data = result.rows.map((row) => ({ name: t(`saleTypes.${row.saleType}`), value: Math.max(num(row, 'netSales'), 0) })).filter((d) => d.value > 0);
    return (
      <ChartCard title={t('reports.charts.salesMix')} subheader={base} empty={data.length === 0} table={{ columns: [{ key: 'name', label: t('reports.columns.saleType') }, { key: 'value', label: t('reports.columns.netSales'), format: money }], rows: data }}>
        <ShareDonutChart data={data} tooltipFormatter={money} />
      </ChartCard>
    );
  }
  if (kind === 'byCategory') {
    const list = (result.meta?.byCategory ?? []).map((c) => ({ name: categoryLabel(t, { code: /^[A-Z_]+$/.test(c.category) ? c.category : null, name: c.category }), value: Number(c.amount) }));
    const data = list.length > 6 ? [...list.slice(0, 5), { name: t('reports.other'), value: list.slice(5).reduce((s, x) => s + x.value, 0) }] : list;
    return (
      <ChartCard title={t('reports.charts.expensesByCategory')} subheader={base} empty={data.length === 0} table={{ columns: [{ key: 'name', label: t('reports.columns.category') }, { key: 'value', label: t('reports.columns.baseAmount'), format: money }], rows: data }}>
        <ShareDonutChart data={data} tooltipFormatter={money} />
      </ChartCard>
    );
  }
  if (kind === 'top') {
    const data = result.rows.slice(0, 10).map((row) => ({ name: row.productName, netSales: num(row, 'netSales'), grossProfit: num(row, 'grossProfit') }));
    const series = [
      { key: 'netSales', label: t('reports.columns.netSales') },
      { key: 'grossProfit', label: t('reports.columns.grossProfit') },
    ];
    return (
      <ChartCard title={t('reports.charts.topProducts')} subheader={base} empty={data.length === 0} table={{ columns: [{ key: 'name', label: t('reports.columns.productName') }, ...series.map((s) => ({ ...s, format: money }))], rows: data }}>
        <ComparisonBarChart data={data} xKey="name" series={series} tooltipFormatter={money} />
      </ChartCard>
    );
  }
  return null;
}

/** One filter control; options are translated. */
function Filter({ name, value, onChange }) {
  const { t } = useTranslation();
  const currencies = useCurrencies();
  const accounts = useCashAccounts();
  const categories = useQuery({ queryKey: ['categories', 'all'], queryFn: () => catalogService.categories(), enabled: name === 'category' });
  const expenseCats = useQuery({ queryKey: ['expense-categories', 'all'], queryFn: () => expenseService.categories({}), enabled: name === 'expenseCategory' });
  if (name === 'nonZero') {
    return <FormControlLabel control={<Switch checked={Boolean(value)} onChange={(e) => onChange(e.target.checked)} />} label={t('reports.filters.nonZero')} />;
  }
  const options = {
    category: (categories.data ?? []).map((c) => [c.id, c.name]),
    expenseCategory: (expenseCats.data ?? []).map((c) => [c.id, categoryLabel(t, c)]),
    account: (accounts.data ?? []).map((a) => [a.id, a.name]),
    currency: currencies.map((c) => [c, c]),
    bestSort: [
      ['QUANTITY', t('reports.filters.byQuantity')],
      ['REVENUE', t('reports.filters.byRevenue')],
    ],
    movementType: MOVEMENT_TYPES.map((m) => [m, t(`inventory.movementTypes.${m}`)]),
    cashType: CASH_TXN_TYPES.map((m) => [m, t(`cash.types.${m}`)]),
    customerType: ['RETAIL', 'WHOLESALE'].map((m) => [m, t(`saleTypes.${m}`)]),
    paymentStatus: ['POSTED', 'REVERSED'].map((m) => [m, t(`status.${m}`)]),
    purchasePaymentStatus: ['UNPAID', 'PARTIALLY_PAID', 'PAID'].map((m) => [m, t(`status.${m}`)]),
    stockStatus: ['IN_STOCK', 'LOW_STOCK', 'OUT_OF_STOCK'].map((m) => [m, t(`status.${m}`)]),
    expenseStatus: [
      ['POSTED', t('status.POSTED')],
      ['VOIDED', t('status.VOIDED')],
      ['ALL', t('common.all')],
    ],
  }[name];
  const allLabel = name === 'bestSort' ? null : t('common.all');
  return (
    <TextField select label={t(`reports.filters.${name}`)} value={value ?? (name === 'bestSort' ? 'QUANTITY' : name === 'expenseStatus' ? 'POSTED' : '')} onChange={(e) => onChange(e.target.value)} sx={{ width: 170 }} fullWidth={false}>
      {allLabel && name !== 'expenseStatus' && <MenuItem value="">{allLabel}</MenuItem>}
      {(options ?? []).map(([v, label]) => (
        <MenuItem key={v} value={v}>
          {label}
        </MenuItem>
      ))}
    </TextField>
  );
}
