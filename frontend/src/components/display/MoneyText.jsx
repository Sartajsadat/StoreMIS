import { Typography } from '@mui/material';
import { formatMoney } from '../../utils/format';
import Ltr from './Ltr';

/** Formatted amount with its currency code, e.g. "10,000 AFN". Never mixes currencies. */
export default function MoneyText({ amount, currency = 'AFN', variant = 'inherit', color, fontWeight, alwaysDecimals }) {
  return (
    <Typography component="span" variant={variant} color={color} sx={{ fontWeight, fontVariantNumeric: 'tabular-nums' }}>
      <Ltr>{formatMoney(amount, currency, { alwaysDecimals })}</Ltr>
    </Typography>
  );
}
