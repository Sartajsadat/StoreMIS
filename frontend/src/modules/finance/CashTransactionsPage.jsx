import { useTranslation } from 'react-i18next';
import { Card } from '@mui/material';
import PageHeader from '../../components/display/PageHeader';
import CashTransactionsTable from './CashTransactionsTable';

export default function CashTransactionsPage() {
  const { t } = useTranslation();
  return (
    <>
      <PageHeader title={t('cash.transactionsTitle')} subtitle={t('cash.transactionsSubtitle')} />
      <Card>
        <CashTransactionsTable />
      </Card>
    </>
  );
}
