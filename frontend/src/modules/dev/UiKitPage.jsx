import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { Button, Chip, Grid, Stack } from '@mui/material';
import PaymentsOutlined from '@mui/icons-material/PaymentsOutlined';
import TrendingUpOutlined from '@mui/icons-material/TrendingUpOutlined';
import WarningAmberOutlined from '@mui/icons-material/WarningAmberOutlined';
import AttachMoneyOutlined from '@mui/icons-material/AttachMoneyOutlined';
import PageHeader from '../../components/display/PageHeader';
import SectionCard from '../../components/display/SectionCard';
import KpiCard from '../../components/display/KpiCard';
import StatusChip from '../../components/display/StatusChip';
import MoneyText from '../../components/display/MoneyText';
import Ltr from '../../components/display/Ltr';
import DataTable from '../../components/data/DataTable';
import FilterBar from '../../components/data/FilterBar';
import DateRangeFilter from '../../components/data/DateRangeFilter';
import ExportButtons from '../../components/data/ExportButtons';
import ChartCard from '../../components/charts/ChartCard';
import TrendLineChart from '../../components/charts/TrendLineChart';
import ComparisonBarChart from '../../components/charts/ComparisonBarChart';
import ShareDonutChart from '../../components/charts/ShareDonutChart';
import ConfirmDialog from '../../components/feedback/ConfirmDialog';
import ReasonDialog from '../../components/feedback/ReasonDialog';
import FormDialog from '../../components/form/FormDialog';
import FormTextField from '../../components/form/FormTextField';
import FormSelect from '../../components/form/FormSelect';
import FormMoneyField from '../../components/form/FormMoneyField';
import FormSwitch from '../../components/form/FormSwitch';
import { useNotify } from '../../contexts/NotificationContext';
import { formatMoney } from '../../utils/format';
import { resolveRange } from '../../utils/dateRange';
import { msg } from '../../utils/validation';

// Sample data for the component gallery only (development builds). Names are user data and stay untranslated.
const DAILY = [
  { day: '11-09', afn: 42000, usd: 310 },
  { day: '11-10', afn: 51500, usd: 260 },
  { day: '11-11', afn: 38200, usd: 410 },
  { day: '11-12', afn: 64800, usd: 380 },
  { day: '11-13', afn: 58100, usd: 520 },
  { day: '11-14', afn: 71200, usd: 450 },
  { day: '11-15', afn: 66900, usd: 610 },
];
const SALES = [
  { id: 1, invoiceNo: 'INV-2026-000121', customer: 'Walk-in Customer', type: 'RETAIL', total: '8500.00', currency: 'AFN', status: 'PAID' },
  { id: 2, invoiceNo: 'INV-2026-000122', customer: 'Ahmad Electric Co.', type: 'WHOLESALE', total: '10000.00', currency: 'AFN', status: 'PARTIALLY_PAID' },
  { id: 3, invoiceNo: 'INV-2026-000123', customer: 'Karimi Traders', type: 'WHOLESALE', total: '640.50', currency: 'USD', status: 'UNPAID' },
  { id: 4, invoiceNo: 'INV-2026-000124', customer: 'Walk-in Customer', type: 'RETAIL', total: '2350.00', currency: 'AFN', status: 'CANCELLED' },
];

const formSchema = z.object({
  name: z.string().trim().min(2, msg('validation.minLength', { min: 2 })),
  code: z.string().optional(),
  saleType: z.enum(['RETAIL', 'WHOLESALE']),
  price: z
    .string()
    .min(1, msg('validation.required'))
    .refine((v) => Number(v) > 0, msg('validation.positive')),
  active: z.boolean(),
});

export default function UiKitPage() {
  const { t } = useTranslation();
  const notify = useNotify();
  const [range, setRange] = useState({ preset: 'MONTH', ...resolveRange('MONTH') });
  const [search, setSearch] = useState('');
  const [dialog, setDialog] = useState(null);
  const { control, handleSubmit, reset } = useForm({
    resolver: zodResolver(formSchema),
    defaultValues: { name: '', code: '', saleType: 'RETAIL', price: '', active: true },
  });

  const columns = useMemo(
    () => [
      { field: 'invoiceNo', headerName: t('uiKit.invoice'), flex: 1, minWidth: 170, renderCell: (p) => <Ltr>{p.value}</Ltr> },
      { field: 'customer', headerName: t('uiKit.customer'), flex: 1.2, minWidth: 160 },
      { field: 'type', headerName: t('uiKit.saleType'), width: 130, valueFormatter: (v) => t(`saleTypes.${v}`) },
      { field: 'total', headerName: t('common.total'), width: 150, align: 'right', headerAlign: 'right', renderCell: (p) => <MoneyText amount={p.value} currency={p.row.currency} /> },
      { field: 'status', headerName: t('common.status'), width: 160, renderCell: (p) => <StatusChip status={p.value} /> },
    ],
    [t],
  );
  const rows = SALES.filter((r) => !search || `${r.invoiceNo} ${r.customer}`.toLowerCase().includes(search.toLowerCase()));

  const dailySeries = [{ key: 'afn', label: 'AFN' }];
  const retailWholesale = [
    { month: '07', retail: 320000, wholesale: 410000 },
    { month: '08', retail: 295000, wholesale: 455000 },
    { month: '09', retail: 360000, wholesale: 390000 },
    { month: '10', retail: 410000, wholesale: 520000 },
    { month: '11', retail: 248000, wholesale: 301000 },
  ];
  const expenses = [
    { name: t('uiKit.rent'), value: 25000 },
    { name: t('uiKit.salary'), value: 42000 },
    { name: t('uiKit.electricity'), value: 6400 },
    { name: t('uiKit.transport'), value: 5200 },
    { name: t('uiKit.other'), value: 3100 },
  ];
  const afn = (v) => formatMoney(v, 'AFN');

  return (
    <>
      <PageHeader
        title={t('uiKit.title')}
        subtitle={t('uiKit.subtitle')}
        actions={
          <>
            <Chip label={t('uiKit.sampleData')} color="warning" variant="outlined" />
            <ExportButtons onPdf={() => {}} onExcel={() => {}} />
          </>
        }
      />
      <Stack spacing={3}>
        <SectionCard title={t('dateRange.label')}>
          <DateRangeFilter value={range} onChange={setRange} />
        </SectionCard>

        <Grid container spacing={3}>
          <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
            <KpiCard label={t('uiKit.todaySales')} value={formatMoney('66900', 'AFN')} icon={PaymentsOutlined} trend={8.4} trendLabel={t('common.vsPrevious')} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
            <KpiCard label={t('uiKit.grossProfit')} value={formatMoney('14250', 'AFN')} icon={TrendingUpOutlined} color="success" trend={-2.1} trendLabel={t('common.vsPrevious')} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
            <KpiCard label={t('uiKit.lowStockProducts')} value="7" icon={WarningAmberOutlined} color="warning" caption={t('nav.lowStock')} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
            <KpiCard label={t('uiKit.usdCash')} value={formatMoney('650.5', 'USD')} icon={AttachMoneyOutlined} color="info" caption={t('currencies.USD')} />
          </Grid>
        </Grid>

        <Grid container spacing={3}>
          <Grid size={{ xs: 12, lg: 6 }}>
            <ChartCard
              title={t('uiKit.chartLine')}
              subheader={t('currencies.AFN')}
              table={{ columns: [{ key: 'day', label: t('common.date') }, { key: 'afn', label: t('common.amount'), format: afn }], rows: DAILY }}
            >
              <TrendLineChart data={DAILY} xKey="day" series={dailySeries} tooltipFormatter={afn} />
            </ChartCard>
          </Grid>
          <Grid size={{ xs: 12, lg: 6 }}>
            <ChartCard
              title={t('uiKit.chartBar')}
              subheader={t('currencies.AFN')}
              table={{
                columns: [
                  { key: 'month', label: t('common.date') },
                  { key: 'retail', label: t('saleTypes.RETAIL'), format: afn },
                  { key: 'wholesale', label: t('saleTypes.WHOLESALE'), format: afn },
                ],
                rows: retailWholesale,
              }}
            >
              <ComparisonBarChart
                data={retailWholesale}
                xKey="month"
                series={[
                  { key: 'retail', label: t('saleTypes.RETAIL') },
                  { key: 'wholesale', label: t('saleTypes.WHOLESALE') },
                ]}
                tooltipFormatter={afn}
              />
            </ChartCard>
          </Grid>
          <Grid size={{ xs: 12, md: 6, lg: 5 }}>
            <ChartCard
              title={t('uiKit.chartPie')}
              subheader={t('currencies.AFN')}
              table={{ columns: [{ key: 'name', label: t('common.name') }, { key: 'value', label: t('common.amount'), format: afn }], rows: expenses }}
            >
              <ShareDonutChart data={expenses} tooltipFormatter={afn} />
            </ChartCard>
          </Grid>
          <Grid size={{ xs: 12, md: 6, lg: 7 }}>
            <SectionCard title={t('uiKit.statusBadges')}>
              <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 1 }}>
                {['PAID', 'PARTIALLY_PAID', 'UNPAID', 'DRAFT', 'CONFIRMED', 'CANCELLED', 'REVERSED', 'POSTED', 'LOW_STOCK', 'OUT_OF_STOCK', 'IN_STOCK', 'ACTIVE', 'INACTIVE'].map((s) => (
                  <StatusChip key={s} status={s} />
                ))}
              </Stack>
              <Stack direction="row" spacing={1} sx={{ mt: 3, flexWrap: 'wrap', gap: 1 }}>
                <Button variant="outlined" onClick={() => setDialog('confirm')}>
                  {t('uiKit.openConfirm')}
                </Button>
                <Button variant="outlined" color="error" onClick={() => setDialog('reason')}>
                  {t('uiKit.openReason')}
                </Button>
                <Button variant="contained" onClick={() => setDialog('form')}>
                  {t('uiKit.openForm')}
                </Button>
                <Button variant="text" onClick={() => notify.success(t('uiKit.notifyMessage'))}>
                  {t('uiKit.notifyDemo')}
                </Button>
              </Stack>
            </SectionCard>
          </Grid>
        </Grid>

        <SectionCard title={t('uiKit.table')} noPadding>
          <FilterBar search={search} onSearchChange={setSearch} onReset={() => setSearch('')} />
          <DataTable rows={rows} columns={columns} />
        </SectionCard>
      </Stack>

      <ConfirmDialog open={dialog === 'confirm'} message={t('dialogs.confirmDiscard')} onClose={() => setDialog(null)} onConfirm={() => setDialog(null)} />
      <ReasonDialog open={dialog === 'reason'} onClose={() => setDialog(null)} onConfirm={() => setDialog(null)} />
      <FormDialog
        open={dialog === 'form'}
        title={t('uiKit.form')}
        onClose={() => setDialog(null)}
        onSubmit={handleSubmit(() => {
          notify.success(t('notify.saved'));
          reset();
          setDialog(null);
        })}
      >
        <Stack spacing={2} sx={{ pt: 1 }}>
          <FormTextField name="name" control={control} label={t('uiKit.product')} />
          <FormTextField name="code" control={control} label={t('uiKit.productCode')} ltr helperText={t('common.optional')} />
          <FormSelect
            name="saleType"
            control={control}
            label={t('uiKit.saleType')}
            options={[
              { value: 'RETAIL', label: t('saleTypes.RETAIL') },
              { value: 'WHOLESALE', label: t('saleTypes.WHOLESALE') },
            ]}
          />
          <FormMoneyField name="price" control={control} label={t('uiKit.unitPrice')} currency="AFN" />
          <FormSwitch name="active" control={control} label={t('common.active')} />
        </Stack>
      </FormDialog>
    </>
  );
}
