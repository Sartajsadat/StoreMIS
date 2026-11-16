import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Box, Tab, Tabs } from '@mui/material';
import PageHeader from '../../components/display/PageHeader';
import ReportView from './ReportView';
import { REPORT_GROUPS } from './reportCatalog';

/** A menu entry of the Reports section: its reports as tabs (the chosen one is kept in the URL, ?r=key). */
export default function ReportsPage({ group }) {
  const { t } = useTranslation();
  const keys = REPORT_GROUPS[group];
  const [params, setParams] = useSearchParams();
  const current = keys.includes(params.get('r')) ? params.get('r') : keys[0];
  return (
    <>
      <PageHeader title={t(`nav.${group}`)} subtitle={t(`reports.descriptions.${current}`)} />
      {keys.length > 1 && (
        <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
          <Tabs value={current} onChange={(_, v) => setParams({ r: v }, { replace: true })} variant="scrollable" allowScrollButtonsMobile>
            {keys.map((k) => (
              <Tab key={k} value={k} label={t(`reports.names.${k}`)} />
            ))}
          </Tabs>
        </Box>
      )}
      <ReportView key={current} reportKey={current} />
    </>
  );
}

export const SalesReportsPage = () => <ReportsPage group="salesReports" />;
export const PurchaseReportsPage = () => <ReportsPage group="purchaseReports" />;
export const InventoryReportsPage = () => <ReportsPage group="inventoryReports" />;
export const CustomerBalancesPage = () => <ReportsPage group="customerBalances" />;
export const SupplierBalancesPage = () => <ReportsPage group="supplierBalances" />;
export const CashReportsPage = () => <ReportsPage group="cashReports" />;
