import { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Box, Button, Card, CardHeader, Chip, Grid, List, ListItemButton, ListItemText, Stack, Table, TableBody, TableCell, TableRow, Typography } from '@mui/material';
import PaymentsOutlined from '@mui/icons-material/PaymentsOutlined';
import TrendingUpOutlined from '@mui/icons-material/TrendingUpOutlined';
import ReceiptLongOutlined from '@mui/icons-material/ReceiptLongOutlined';
import AccountBalanceOutlined from '@mui/icons-material/AccountBalanceOutlined';
import AccountBalanceWalletOutlined from '@mui/icons-material/AccountBalanceWalletOutlined';
import PeopleAltOutlined from '@mui/icons-material/PeopleAltOutlined';
import LocalShippingOutlined from '@mui/icons-material/LocalShippingOutlined';
import Inventory2Outlined from '@mui/icons-material/Inventory2Outlined';
import WarningAmberOutlined from '@mui/icons-material/WarningAmberOutlined';
import PointOfSaleOutlined from '@mui/icons-material/PointOfSaleOutlined';
import ShoppingCartOutlined from '@mui/icons-material/ShoppingCartOutlined';
import PauseCircleOutline from '@mui/icons-material/PauseCircleOutline';
import PageHeader from '../../components/display/PageHeader';
import KpiCard from '../../components/display/KpiCard';
import MoneyText from '../../components/display/MoneyText';
import QtyText from '../../components/display/QtyText';
import StatusChip from '../../components/display/StatusChip';
import UserText from '../../components/display/UserText';
import Ltr from '../../components/display/Ltr';
import SectionCard from '../../components/display/SectionCard';
import DateRangeFilter from '../../components/data/DateRangeFilter';
import ChartCard from '../../components/charts/ChartCard';
import TrendLineChart from '../../components/charts/TrendLineChart';
import ComparisonBarChart from '../../components/charts/ComparisonBarChart';
import ShareDonutChart from '../../components/charts/ShareDonutChart';
import { dashboardService } from '../../services/reportService';
import { useAuth } from '../../contexts/AuthContext';
import { resolveRange } from '../../utils/dateRange';
import { formatDate, formatDateTime, formatMoney, formatNumber } from '../../utils/format';

/** Admins see the business at a glance; everyone else sees their own day. */
export default function DashboardPage() {
  const { isAdmin } = useAuth();
  return isAdmin ? <AdminDashboard /> : <MyDay />;
}

const moneyList = (map) =>
  Object.entries(map ?? {})
    .map(([c, v]) => formatMoney(v, c))
    .join(' · ');

function AdminDashboard() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [range, setRange] = useState({ preset: 'TODAY', ...resolveRange('TODAY') });
  const params = { from: range.from, to: range.to };
  const summary = useQuery({ queryKey: ['dashboard', 'summary', params], queryFn: () => dashboardService.summary(params), placeholderData: keepPreviousData });
  const charts = useQuery({ queryKey: ['dashboard', 'charts', params], queryFn: () => dashboardService.charts(params), placeholderData: keepPreviousData });
  const recent = useQuery({ queryKey: ['dashboard', 'recent'], queryFn: dashboardService.recent });
  const s = summary.data;
  const c = charts.data;
  const base = s?.baseCurrency ?? 'AFN';
  const money = (v) => formatMoney(v, base);
  const k = s?.kpi;
  const loading = !s;
  const vs = t('dashboard.vsPrevious');

  const series = (c?.series ?? []).map((p) => ({
    period: formatDate(p.period, c.unit === 'month' ? 'YYYY-MM' : 'MM-DD'),
    netSales: Number(p.netSales),
    grossProfit: Number(p.grossProfit),
    expenses: Number(p.expenses),
    purchases: Number(p.purchases),
  }));
  const trendSeries = [
    { key: 'netSales', label: t('reports.columns.netSales') },
    { key: 'grossProfit', label: t('reports.columns.grossProfit') },
  ];
  const typeData = Object.entries(c?.byType ?? {})
    .map(([type, v]) => ({ name: t(`saleTypes.${type}`), value: Math.max(Number(v), 0) }))
    .filter((d) => d.value > 0);
  const expList = Object.entries(c?.expensesByCategory ?? {}).map(([cat, v]) => ({ name: t(`expenses.categoryCodes.${cat}`, cat), value: Number(v) }));
  const expData = expList.length > 6 ? [...expList.slice(0, 5), { name: t('reports.other'), value: expList.slice(5).reduce((a, x) => a + x.value, 0) }] : expList;
  const top = (c?.topProducts ?? []).map((p) => ({ name: p.productName, netSales: Number(p.netSales) }));

  return (
    <>
      <PageHeader
        title={t('dashboard.title')}
        subtitle={t('dashboard.subtitle')}
        actions={
          <Button variant="contained" startIcon={<PointOfSaleOutlined />} onClick={() => navigate('/sales/new')}>
            {t('nav.newSale')}
          </Button>
        }
      />
      <Card sx={{ p: 2, mb: 3 }}>
        <DateRangeFilter value={range} onChange={setRange} />
      </Card>
      <Grid container spacing={2.5}>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <KpiCard label={t('dashboard.netSales')} value={k && money(k.netSales.value)} icon={PaymentsOutlined} trend={k?.netSales.change} trendLabel={vs} loading={loading} caption={k && t('reports.invoicesCount', { count: k.invoices.value })} />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <KpiCard label={t('dashboard.grossProfit')} value={k && money(k.grossProfit.value)} icon={TrendingUpOutlined} color="success" trend={k?.grossProfit.change} trendLabel={vs} loading={loading} caption={s && `${t('reports.grossMargin')}: ${formatNumber(s.grossMargin, { maxDecimals: 1 })}%`} />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <KpiCard label={t('dashboard.expenses')} value={k && money(k.expenses.value)} icon={ReceiptLongOutlined} color="warning" trend={k?.expenses.change} trendLabel={vs} loading={loading} lowerIsBetter />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <KpiCard label={t('dashboard.netProfit')} value={k && money(k.netProfit.value)} icon={AccountBalanceOutlined} color={k && Number(k.netProfit.value) < 0 ? 'error' : 'info'} trend={k?.netProfit.change} trendLabel={vs} loading={loading} />
        </Grid>
        {(s?.cash ?? []).map((a) => (
          <Grid key={a.id} size={{ xs: 12, sm: 6, lg: 3 }}>
            <KpiCard label={a.name} value={formatMoney(a.balance, a.currency)} icon={AccountBalanceWalletOutlined} color="secondary" caption={t('dashboard.cashNow')} />
          </Grid>
        ))}
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <KpiCard label={t('dashboard.receivables')} value={s && (moneyList(s.receivables.owed) || formatMoney(0, base))} icon={PeopleAltOutlined} color="info" caption={t('dashboard.customersOwe')} loading={loading} />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <KpiCard label={t('dashboard.payables')} value={s && (moneyList(s.payables.owed) || formatMoney(0, base))} icon={LocalShippingOutlined} color="warning" caption={t('dashboard.weOwe')} loading={loading} />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <KpiCard label={t('dashboard.stockValue')} value={s && money(s.stock.value)} icon={Inventory2Outlined} caption={s && t('dashboard.productsCount', { count: s.stock.products })} loading={loading} />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <Box sx={{ cursor: 'pointer', height: '100%' }} onClick={() => navigate('/inventory/low-stock')}>
            <KpiCard
              label={t('dashboard.stockAlerts')}
              value={s ? `${formatNumber(s.stock.lowStock)} / ${formatNumber(s.stock.outOfStock)}` : ''}
              icon={WarningAmberOutlined}
              color={s && Number(s.stock.outOfStock) > 0 ? 'error' : 'warning'}
              caption={t('dashboard.lowOut')}
              loading={loading}
            />
          </Box>
        </Grid>
      </Grid>

      <Grid container spacing={3} sx={{ mt: 0.5 }}>
        <Grid size={{ xs: 12, lg: 8 }}>
          <ChartCard
            title={t('dashboard.salesTrend')}
            subheader={base}
            empty={series.length === 0}
            table={{ columns: [{ key: 'period', label: t('reports.columns.period') }, ...trendSeries.map((x) => ({ ...x, format: money })), { key: 'expenses', label: t('dashboard.expenses'), format: money }, { key: 'purchases', label: t('dashboard.purchases'), format: money }], rows: series }}
          >
            {series.length > 1 ? <TrendLineChart data={series} xKey="period" series={trendSeries} tooltipFormatter={money} /> : <ComparisonBarChart data={series} xKey="period" series={trendSeries} tooltipFormatter={money} />}
          </ChartCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6, lg: 4 }}>
          <ChartCard title={t('reports.charts.salesMix')} subheader={base} empty={typeData.length === 0} table={{ columns: [{ key: 'name', label: t('reports.columns.saleType') }, { key: 'value', label: t('reports.columns.netSales'), format: money }], rows: typeData }}>
            <ShareDonutChart data={typeData} tooltipFormatter={money} />
          </ChartCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6, lg: 4 }}>
          <ChartCard title={t('reports.charts.expensesByCategory')} subheader={base} empty={expData.length === 0} table={{ columns: [{ key: 'name', label: t('reports.columns.category') }, { key: 'value', label: t('reports.columns.baseAmount'), format: money }], rows: expData }}>
            <ShareDonutChart data={expData} tooltipFormatter={money} />
          </ChartCard>
        </Grid>
        <Grid size={{ xs: 12, lg: 8 }}>
          <ChartCard title={t('reports.charts.topProducts')} subheader={base} empty={top.length === 0} table={{ columns: [{ key: 'name', label: t('reports.columns.productName') }, { key: 'netSales', label: t('reports.columns.netSales'), format: money }], rows: top }}>
            <ComparisonBarChart data={top} xKey="name" series={[{ key: 'netSales', label: t('reports.columns.netSales') }]} tooltipFormatter={money} />
          </ChartCard>
        </Grid>
      </Grid>

      <Grid container spacing={3} sx={{ mt: 0.5 }}>
        <Grid size={{ xs: 12, lg: 6 }}>
          <RecentSales title={t('dashboard.recentSales')} rows={recent.data?.sales} onOpen={(id) => navigate(`/sales/${id}`)} action={<Button size="small" onClick={() => navigate('/sales')}>{t('inventory.viewAll')}</Button>} />
        </Grid>
        <Grid size={{ xs: 12, lg: 6 }}>
          <Card sx={{ height: '100%' }}>
            <CardHeader title={t('dashboard.lowStock')} action={<Button size="small" onClick={() => navigate('/inventory/low-stock')}>{t('inventory.viewAll')}</Button>} />
            {(recent.data?.lowStock ?? []).length === 0 ? (
              <Typography color="text.secondary" sx={{ px: 2, pb: 2 }}>
                {t('dashboard.noLowStock')}
              </Typography>
            ) : (
              <Table size="small">
                <TableBody>
                  {recent.data.lowStock.map((p) => (
                    <TableRow key={p.productId}>
                      <TableCell>
                        <UserText>{p.productName}</UserText>
                      </TableCell>
                      <TableCell align="right">
                        <QtyText value={p.quantity} unit={p.unitName} color={Number(p.quantity) <= 0 ? '#dc2626' : undefined} />
                      </TableCell>
                      <TableCell align="right">
                        <Chip size="small" label={t(Number(p.quantity) <= 0 ? 'status.OUT_OF_STOCK' : 'status.LOW_STOCK')} color={Number(p.quantity) <= 0 ? 'error' : 'warning'} variant="outlined" />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Card>
        </Grid>
        <Grid size={{ xs: 12, lg: 6 }}>
          <Card sx={{ height: '100%' }}>
            <CardHeader title={t('dashboard.recentPurchases')} />
            <Table size="small">
              <TableBody>
                {(recent.data?.purchases ?? []).map((p) => (
                  <TableRow key={p.id} hover sx={{ cursor: 'pointer' }} onClick={() => navigate(`/purchases/${p.id}`)}>
                    <TableCell>
                      <Ltr>{p.purchaseNo}</Ltr>
                    </TableCell>
                    <TableCell>
                      <UserText>{p.supplierName}</UserText>
                    </TableCell>
                    <TableCell align="right">
                      <MoneyText amount={p.grandTotal} currency={p.currency} />
                    </TableCell>
                    <TableCell align="right">
                      <StatusChip status={p.paymentStatus} />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            {(recent.data?.purchases ?? []).length === 0 && (
              <Typography color="text.secondary" sx={{ px: 2, pb: 2 }}>
                {t('common.noData')}
              </Typography>
            )}
          </Card>
        </Grid>
        <Grid size={{ xs: 12, lg: 6 }}>
          <SectionCard title={t('dashboard.activity')} action={<Button size="small" onClick={() => navigate('/users/activity-logs')}>{t('inventory.viewAll')}</Button>}>
            <List dense disablePadding>
              {(recent.data?.activity ?? []).map((a) => (
                <ListItemButton key={a.id} disableGutters onClick={() => navigate('/users/activity-logs')}>
                  <ListItemText
                    primary={
                      <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                        <Chip size="small" label={t(`audit.actions.${a.action}`, a.action)} />
                        <span>{t(`audit.modules.${a.module}`, a.module)}</span>
                        {a.reference && <Ltr>{a.reference}</Ltr>}
                      </Stack>
                    }
                    secondary={
                      <>
                        <UserText>{a.userName}</UserText> · <Ltr>{formatDateTime(a.at)}</Ltr>
                      </>
                    }
                  />
                </ListItemButton>
              ))}
            </List>
          </SectionCard>
        </Grid>
      </Grid>
    </>
  );
}

function RecentSales({ title, rows, onOpen, action }) {
  const { t } = useTranslation();
  return (
    <Card sx={{ height: '100%' }}>
      <CardHeader title={title} action={action} />
      {(rows ?? []).length === 0 ? (
        <Typography color="text.secondary" sx={{ px: 2, pb: 2 }}>
          {t('dashboard.noSales')}
        </Typography>
      ) : (
        <Table size="small">
          <TableBody>
            {rows.map((s) => (
              <TableRow key={s.id} hover sx={{ cursor: 'pointer' }} onClick={() => onOpen(s.id)}>
                <TableCell>
                  <Ltr>{s.invoiceNo}</Ltr>
                  <Typography variant="caption" color="text.secondary" component="div">
                    <Ltr>{formatDateTime(s.date)}</Ltr>
                  </Typography>
                </TableCell>
                <TableCell>{s.walkIn ? t('customers.walkIn') : <UserText>{s.customerName}</UserText>}</TableCell>
                <TableCell align="right">
                  <MoneyText amount={s.grandTotal} currency={s.currency} fontWeight={600} />
                </TableCell>
                <TableCell align="right">
                  <StatusChip status={s.status === 'CANCELLED' ? 'CANCELLED' : s.paymentStatus} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </Card>
  );
}

/** Entry users: their sales and collections today, held carts and quick links. */
function MyDay() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { user, hasPermission } = useAuth();
  const q = useQuery({ queryKey: ['dashboard', 'my-day'], queryFn: dashboardService.myDay, refetchInterval: 60_000 });
  const d = q.data;
  return (
    <>
      <PageHeader
        title={t('dashboard.myDayTitle', { name: user?.fullName ?? '' })}
        subtitle={d && t('dashboard.myDaySubtitle', { date: formatDate(d.date) })}
        actions={
          <>
            {hasPermission('RECORD_PURCHASES') && (
              <Button variant="outlined" startIcon={<ShoppingCartOutlined />} onClick={() => navigate('/purchases/new')}>
                {t('nav.newPurchase')}
              </Button>
            )}
            <Button variant="contained" startIcon={<PointOfSaleOutlined />} onClick={() => navigate('/sales/new')}>
              {t('nav.newSale')}
            </Button>
          </>
        }
      />
      <Grid container spacing={2.5}>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <KpiCard label={t('dashboard.mySales')} value={d && (moneyList(d.salesByCurrency) || formatMoney(0, 'AFN'))} icon={PaymentsOutlined} caption={d && t('reports.invoicesCount', { count: d.salesCount })} loading={!d} />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <KpiCard label={t('dashboard.myCollections')} value={d && (moneyList(d.receivedByCurrency) || formatMoney(0, 'AFN'))} icon={AccountBalanceWalletOutlined} color="success" caption={t('dashboard.paymentsReceived')} loading={!d} />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <Box sx={{ cursor: 'pointer', height: '100%' }} onClick={() => navigate('/sales/new')}>
            <KpiCard label={t('dashboard.heldCarts')} value={d ? formatNumber(d.heldCarts) : ''} icon={PauseCircleOutline} color="secondary" caption={t('dashboard.heldHint')} loading={!d} />
          </Box>
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <Box sx={{ cursor: 'pointer', height: '100%' }} onClick={() => navigate('/inventory/low-stock')}>
            <KpiCard label={t('dashboard.stockAlerts')} value={d ? formatNumber(d.lowStock) : ''} icon={WarningAmberOutlined} color="warning" caption={t('nav.lowStock')} loading={!d} />
          </Box>
        </Grid>
        <Grid size={12}>
          <RecentSales title={t('dashboard.myRecentSales')} rows={d?.recentSales} onOpen={(id) => navigate(`/sales/${id}`)} />
        </Grid>
      </Grid>
    </>
  );
}
