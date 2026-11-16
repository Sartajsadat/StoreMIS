import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Box, ToggleButton, ToggleButtonGroup } from '@mui/material';
import PageHeader from '../../components/display/PageHeader';
import { StockTable } from './StockOverviewPage';

export default function LowStockPage() {
  const { t } = useTranslation();
  const [status, setStatus] = useState('LOW_STOCK');
  return (
    <>
      <PageHeader
        title={t('inventory.lowStockTitle')}
        subtitle={t('inventory.lowStockSubtitle')}
        actions={
          <ToggleButtonGroup size="small" exclusive value={status} onChange={(_e, v) => v && setStatus(v)}>
            <ToggleButton value="LOW_STOCK">{t('inventory.showLow')}</ToggleButton>
            <ToggleButton value="OUT_OF_STOCK">{t('inventory.showOut')}</ToggleButton>
          </ToggleButtonGroup>
        }
      />
      <Box>
        <StockTable key={status} fixedStatus={status} />
      </Box>
    </>
  );
}
