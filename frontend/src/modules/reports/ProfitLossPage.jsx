import { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Box, Card, CardContent, CardHeader, Grid, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material';
import PaymentsOutlined from '@mui/icons-material/PaymentsOutlined';
import TrendingUpOutlined from '@mui/icons-material/TrendingUpOutlined';
import PercentOutlined from '@mui/icons-material/PercentOutlined';
import AccountBalanceOutlined from '@mui/icons-material/AccountBalanceOutlined';
import PageHeader from '../../components/display/PageHeader';
import KpiCard from '../../components/display/KpiCard';
import MoneyText from '../../components/display/MoneyText';
import Ltr from '../../components/display/Ltr';
import PageLoader from '../../components/feedback/PageLoader';
import DateRangeFilter from '../../components/data/DateRangeFilter';
import ExportButtons from '../../components/data/ExportButtons';
import { reportService } from '../../services/reportService';
import { useNotify } from '../../contexts/NotificationContext';
import { errorMessage } from '../../services/apiClient';
import { resolveRange } from '../../utils/dateRange';
import { formatDate, formatMoney, formatNumber } from '../../utils/format';
import { downloadXlsx } from '../../utils/xlsx';
import { cellText, excelRows } from './reportFormat';

/** Percentage change, or null when the previous figure is zero. */
export function percentChange(value, previous) {
  const p = Number(previous);
  if (!p) return null;
  return Math.round(((Number(value) - p) / Math.abs(p)) * 1000) / 10;
}

/** Profit and loss statement for a period, compared with the period just before it. */
export default function ProfitLossPage() {
  const { t } = useTranslation();
  const notify = useNotify();
  const [range, setRange] = useState({ preset: 'MONTH', ...resolveRange('MONTH') });
  const params = { from: range.from, to: range.to };
  const q = useQuery({ queryKey: ['reports', 'profit-loss', params], queryFn: () => reportService.run('profit-loss', params), placeholderData: keepPreviousData });
  const r = q.data;
  const s = r?.meta?.summary;
  const base = r?.baseCurrency;
  const title = t('reports.names.profit-loss');
  const lineCol = r?.columns?.[0];
  const prev = r ? `${formatDate(r.meta.previousFrom)} — ${formatDate(r.meta.previousTo)}` : '';

  const exportExcel = () => {
    try {
      const { rows, boldRows } = excelRows({ result: r, title, t });
      r.rows.forEach((row, i) => row.kind !== 'ITEM' && row.kind !== 'DETAIL' && boldRows.push(i + 4));
      downloadXlsx(`profit-loss_${r.from}_${r.to}`, { sheetName: title, rows, widths: [36, 18, 18], rtl: document.documentElement.dir === 'rtl', boldRows });
    } catch (e) {
      notify.error(errorMessage(t, e));
    }
  };
  const openPrint = (pdf) => window.open(`/print/reports/profit-loss?${new URLSearchParams({ ...params, autoprint: '1', ...(pdf && { pdf: '1' }) })}`, '_blank', 'noopener,width=1000,height=900');

  return (
    <>
      <PageHeader title={title} subtitle={t('reports.descriptions.profit-loss')} actions={<ExportButtons disabled={!r} onExcel={exportExcel} onPdf={() => openPrint(true)} onPrint={() => openPrint(false)} />} />
      <Card sx={{ mb: 3, p: 2 }}>
        <DateRangeFilter value={range} onChange={setRange} />
      </Card>
      {!r ? (
        <PageLoader />
      ) : (
        <Stack spacing={3}>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
              <KpiCard label={t('reports.pl.NET_SALES')} value={formatMoney(s.netSales, base)} icon={PaymentsOutlined} caption={t('reports.invoicesCount', { count: s.invoices })} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
              <KpiCard label={t('reports.pl.GROSS_PROFIT')} value={formatMoney(s.grossProfit, base)} icon={TrendingUpOutlined} color="success" />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
              <KpiCard label={t('reports.grossMargin')} value={`${formatNumber(r.meta.grossMargin, { maxDecimals: 1 })}%`} icon={PercentOutlined} color="info" />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
              <KpiCard
                label={t('reports.pl.NET_PROFIT')}
                value={formatMoney(s.netProfit, base)}
                icon={AccountBalanceOutlined}
                color={Number(s.netProfit) < 0 ? 'error' : 'primary'}
                trend={percentChange(s.netProfit, r.meta.previousNetProfit)}
                trendLabel={t('common.vsPrevious')}
              />
            </Grid>
          </Grid>
          <Grid container spacing={3}>
            <Grid size={{ xs: 12, lg: 8 }}>
              <Card>
                <CardHeader title={t('reports.statement')} subheader={`${formatDate(r.from)} — ${formatDate(r.to)} · ${base}`} />
                <Box sx={{ overflowX: 'auto' }}>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell />
                        <TableCell align="right">{t('reports.thisPeriod')}</TableCell>
                        <TableCell align="right">
                          {t('reports.previousPeriod')}
                          <Typography variant="caption" component="div" color="text.secondary">
                            <Ltr>{prev}</Ltr>
                          </Typography>
                        </TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {r.rows.map((row, i) => {
                        const strong = row.kind === 'SUBTOTAL' || row.kind === 'TOTAL';
                        return (
                          <TableRow key={i} sx={{ bgcolor: row.kind === 'TOTAL' ? 'action.selected' : row.kind === 'SUBTOTAL' ? 'action.hover' : undefined }}>
                            <TableCell sx={{ fontWeight: strong ? 700 : 400, pl: row.kind === 'DETAIL' ? 5 : 2, color: row.kind === 'DETAIL' ? 'text.secondary' : undefined }}>
                              {cellText(lineCol, row, { t, base }).trim()}
                            </TableCell>
                            <TableCell align="right">
                              <MoneyText amount={row.amount} currency={base} fontWeight={strong ? 700 : undefined} color={Number(row.amount) < 0 && row.kind === 'TOTAL' ? 'error.main' : undefined} variant={row.kind === 'TOTAL' ? 'h6' : undefined} />
                            </TableCell>
                            <TableCell align="right">
                              <MoneyText amount={row.previous} currency={base} color="text.secondary" />
                            </TableCell>
                          </TableRow>
                        );
                      })}
                    </TableBody>
                  </Table>
                </Box>
                <CardContent>
                  <Typography variant="caption" color="text.secondary">
                    {t('reports.plNote')}
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
            <Grid size={{ xs: 12, lg: 4 }}>
              <Stack spacing={3}>
                <Card>
                  <CardHeader title={t('reports.salesByCurrency')} subheader={t('reports.originalAmounts')} />
                  <CardContent sx={{ pt: 0 }}>
                    {Object.keys(s.salesByCurrency).length === 0 && <Typography color="text.secondary">{t('common.noData')}</Typography>}
                    {Object.entries(s.salesByCurrency).map(([cur, v]) => (
                      <Row key={cur} label={t(`currencies.${cur}`, cur)} value={<MoneyText amount={v} currency={cur} fontWeight={600} />} />
                    ))}
                  </CardContent>
                </Card>
                <Card>
                  <CardHeader title={t('reports.charts.salesMix')} subheader={base} />
                  <CardContent sx={{ pt: 0 }}>
                    {Object.entries(s.netSalesByType).map(([type, v]) => (
                      <Row key={type} label={t(`saleTypes.${type}`)} value={<MoneyText amount={v} currency={base} fontWeight={600} />} />
                    ))}
                  </CardContent>
                </Card>
                <Card>
                  <CardHeader title={t('reports.lossDetails')} subheader={base} />
                  <CardContent sx={{ pt: 0 }}>
                    <Row label={t('reports.pl.DAMAGE')} value={<MoneyText amount={s.damage} currency={base} />} />
                    <Row label={t('reports.pl.DAMAGED_RETURNS')} value={<MoneyText amount={s.damagedReturns} currency={base} />} />
                    <Row label={t('reports.pl.ADJUSTMENTS_OUT')} value={<MoneyText amount={s.adjustmentsOut} currency={base} />} />
                    <Row label={t('reports.pl.ADJUSTMENTS_IN')} value={<MoneyText amount={Number(s.adjustmentsIn) ? `-${s.adjustmentsIn}` : '0'} currency={base} />} />
                  </CardContent>
                </Card>
              </Stack>
            </Grid>
          </Grid>
        </Stack>
      )}
    </>
  );
}

function Row({ label, value }) {
  return (
    <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', py: 0.5 }}>
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
      {value}
    </Stack>
  );
}
