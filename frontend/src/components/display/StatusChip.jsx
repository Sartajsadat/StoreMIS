import { Chip } from '@mui/material';
import { alpha } from '@mui/material/styles';
import { useTranslation } from 'react-i18next';

const COLOR_BY_STATUS = {
  ACTIVE: 'success',
  INACTIVE: 'default',
  DRAFT: 'default',
  CONFIRMED: 'info',
  CANCELLED: 'error',
  UNPAID: 'error',
  PARTIALLY_PAID: 'warning',
  PAID: 'success',
  POSTED: 'success',
  REVERSED: 'warning',
  VOIDED: 'error',
  CLOSED: 'info',
  REOPENED: 'warning',
  LOW_STOCK: 'warning',
  OUT_OF_STOCK: 'error',
  IN_STOCK: 'success',
};

/** Tinted status badge. The label always accompanies the colour (never colour alone). */
export default function StatusChip({ status, size = 'small' }) {
  const { t } = useTranslation();
  const color = COLOR_BY_STATUS[status] ?? 'default';
  return (
    <Chip
      size={size}
      label={t(`status.${status}`, status)}
      sx={(theme) => {
        const main = color === 'default' ? theme.palette.text.secondary : theme.palette[color].main;
        return {
          color: theme.palette.mode === 'light' && color !== 'default' ? theme.palette[color].dark : main,
          bgcolor: alpha(main, theme.palette.mode === 'light' ? 0.12 : 0.22),
          border: `1px solid ${alpha(main, 0.3)}`,
          height: 24,
        };
      }}
    />
  );
}
