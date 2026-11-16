import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useParams, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Alert, Box, Button, GlobalStyles, Stack } from '@mui/material';
import PrintOutlined from '@mui/icons-material/PrintOutlined';
import PageLoader from '../../components/feedback/PageLoader';
import UserText from '../../components/display/UserText';
import Ltr from '../../components/display/Ltr';
import { reportService } from '../../services/reportService';
import { useSettings } from '../../contexts/SettingsContext';
import { useAuth } from '../../contexts/AuthContext';
import { formatDate, formatDateTime, formatMoney, formatNumber } from '../../utils/format';
import { balancesText, cellText, columnLabel } from '../reports/reportFormat';

const NUMERIC = ['MONEY', 'QTY', 'NUMBER', 'PERCENT'];

/**
 * Printable report (A4 landscape) in the current language. "Export PDF" opens this page: the browser's
 * "Save as PDF" keeps Dari and Pashto text shaped correctly. Prints automatically with ?autoprint=1.
 */
export default function ReportPrintPage() {
  const { key } = useParams();
  const [params] = useSearchParams();
  const { t } = useTranslation();
  const { settings } = useSettings();
  const { user } = useAuth();
  const query = Object.fromEntries([...params.entries()].filter(([k]) => !['autoprint', 'pdf'].includes(k)));
  const q = useQuery({ queryKey: ['reports', 'print', key, query], queryFn: () => reportService.run(key, { ...query, page: 0, size: 10000 }) });
  const r = q.data;

  useEffect(() => {
    if (r && params.get('autoprint') === '1') {
      const timer = setTimeout(() => window.print(), 500);
      return () => clearTimeout(timer);
    }
    return undefined;
  }, [r, params]);

  if (q.isLoading || !r) return <PageLoader fullScreen />;
  const ctx = { t, base: r.baseCurrency };
  const totals = r.totals ?? {};
  const scalarTotal = (col) => {
    const v = totals[col.key];
    if (v === null || v === undefined || typeof v === 'object') return '';
    if (col.type === 'MONEY' && col.currencyField) return '';
    return cellText(col, totals, ctx);
  };
  const hasTotalRow = r.columns.some((c) => scalarTotal(c) !== '');
  const isPl = key === 'profit-loss';
  const snapshot = !params.get('from');

  return (
    <Box sx={{ bgcolor: '#e5e7eb', minHeight: '100vh', py: 3, '@media print': { bgcolor: '#fff', py: 0 } }}>
      <GlobalStyles styles={{ '@page': { size: isPl ? 'A4' : 'A4 landscape', margin: '10mm' }, body: { background: '#fff' } }} />
      <Stack spacing={1} sx={{ alignItems: 'center', mb: 2, '@media print': { display: 'none' } }}>
        {params.get('pdf') === '1' && <Alert severity="info">{t('reports.pdfHint')}</Alert>}
        <Stack direction="row" spacing={1}>
          <Button variant="contained" startIcon={<PrintOutlined />} onClick={() => window.print()}>
            {t('common.print')}
          </Button>
          <Button variant="outlined" onClick={() => window.close()} sx={{ bgcolor: '#fff' }}>
            {t('common.close')}
          </Button>
        </Stack>
      </Stack>
      <Box
        sx={{
          width: isPl ? '210mm' : '297mm',
          maxWidth: '100%',
          mx: 'auto',
          bgcolor: '#fff',
          color: '#000',
          p: '8mm',
          boxShadow: '0 2px 12px rgba(0,0,0,.15)',
          fontSize: 11,
          lineHeight: 1.4,
          '@media print': { boxShadow: 'none', p: 0, width: '100%' },
          '& table': { width: '100%', borderCollapse: 'collapse' },
          '& th, & td': { py: '4px', px: '6px', textAlign: 'start', verticalAlign: 'top', borderBottom: '1px solid #ddd' },
          '& thead th': { borderBottom: '1.5px solid #000', fontWeight: 700, bgcolor: '#f3f4f6' },
          '& .num': { textAlign: 'end', whiteSpace: 'nowrap' },
          '& tfoot td': { fontWeight: 700, borderTop: '1.5px solid #000', borderBottom: 'none' },
          '& thead': { display: 'table-header-group' },
          '& tr': { breakInside: 'avoid' },
        }}
      >
        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'flex-start', mb: 2 }}>
          <Box>
            <Box sx={{ fontWeight: 800, fontSize: '1.5em' }}>{t(`reports.names.${key}`)}</Box>
            {settings.businessName && (
              <Box sx={{ fontSize: '1.1em' }}>
                <UserText>{settings.businessName}</UserText>
              </Box>
            )}
          </Box>
          <Box sx={{ textAlign: 'end' }}>
            <div>
              {snapshot ? t('reports.asOf') : t('reports.period')}: <Ltr>{snapshot ? formatDate(new Date()) : `${formatDate(r.from)} — ${formatDate(r.to)}`}</Ltr>
            </div>
            <div>
              {t('reports.printedBy')}: <UserText>{user?.fullName}</UserText> · <Ltr>{formatDateTime(new Date())}</Ltr>
            </div>
            <div>
              {t('reports.amountsIn', { currency: r.baseCurrency })}
            </div>
          </Box>
        </Stack>
        {r.totalRows > r.rows.length && <Box sx={{ mb: 1, fontWeight: 700 }}>{t('reports.truncated', { count: r.rows.length })}</Box>}
        <table>
          <thead>
            <tr>
              {r.columns.map((c) => (
                <th key={c.key} className={NUMERIC.includes(c.type) ? 'num' : undefined}>
                  {isPl && c.key === 'line' ? '' : columnLabel(t, c.key)}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {r.rows.length === 0 && (
              <tr>
                <td colSpan={r.columns.length}>{t('common.noData')}</td>
              </tr>
            )}
            {r.rows.map((row, i) => (
              <tr key={i} style={isPl && row.kind !== 'ITEM' && row.kind !== 'DETAIL' ? { fontWeight: 700 } : undefined}>
                {r.columns.map((c) => (
                  <td key={c.key} className={NUMERIC.includes(c.type) ? 'num' : undefined} style={isPl && c.key === 'line' && row.kind === 'DETAIL' ? { paddingInlineStart: 24 } : undefined}>
                    {['CODE', 'DATE', 'DATETIME', ...NUMERIC].includes(c.type) ? <Ltr>{cellText(c, row, ctx)}</Ltr> : cellText(c, row, ctx).trim()}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
          {hasTotalRow && (
            <tfoot>
              <tr>
                {r.columns.map((c, i) => (
                  <td key={c.key} className={NUMERIC.includes(c.type) ? 'num' : undefined}>
                    {i === 0 ? t('reports.total') : <Ltr>{scalarTotal(c)}</Ltr>}
                  </td>
                ))}
              </tr>
            </tfoot>
          )}
        </table>
        <ExtraTotals totals={totals} />
      </Box>
    </Box>
  );
}

/** Per-currency and other non-column totals below the table. */
function ExtraTotals({ totals }) {
  const { t } = useTranslation();
  const lines = [];
  for (const [k, v] of Object.entries(totals)) {
    if (k === 'byCurrency' && v) {
      for (const [cur, m] of Object.entries(v)) {
        lines.push(`${cur}: ${Object.entries(m).map(([f, a]) => `${columnLabel(t, f)} ${formatMoney(a, cur)}`).join(' · ')}`);
      }
    } else if (v && typeof v === 'object') {
      lines.push(`${t(`reports.totals.${k}`, k)}: ${balancesText(v) || '—'}`);
    } else if (k === 'count') {
      lines.push(`${t('reports.totals.count')}: ${formatNumber(v)}`);
    }
  }
  if (lines.length === 0) return null;
  return (
    <Box sx={{ mt: 2 }}>
      {lines.map((l) => (
        <div key={l}>{l}</div>
      ))}
    </Box>
  );
}
