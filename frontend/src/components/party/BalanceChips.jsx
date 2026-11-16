import { Chip, Stack, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { formatMoney } from '../../utils/format';
import Ltr from '../display/Ltr';

/**
 * Balances per currency, never added together. For suppliers a positive amount is what we owe;
 * a negative amount is the supplier's credit with us.
 */
export default function BalanceChips({ balances, size = 'small', emptyText }) {
  const { t } = useTranslation();
  const entries = Object.entries(balances ?? {}).filter(([, v]) => Number(v) !== 0);
  if (entries.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        {emptyText ?? t('suppliers.settled')}
      </Typography>
    );
  }
  return (
    <Stack direction="row" spacing={0.75} sx={{ flexWrap: 'wrap', alignItems: 'center' }}>
      {entries.map(([cur, v]) => {
        const negative = Number(v) < 0;
        return (
          <Chip
            key={cur}
            size={size}
            color={negative ? 'success' : 'warning'}
            variant="outlined"
            label={<Ltr>{formatMoney(String(v).replace('-', ''), cur)}</Ltr>}
            title={negative ? t('suppliers.credit') : t('suppliers.owed')}
          />
        );
      })}
    </Stack>
  );
}
