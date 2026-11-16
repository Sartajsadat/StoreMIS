import { Box, Card, Skeleton, Typography } from '@mui/material';
import { alpha } from '@mui/material/styles';
import TrendingUp from '@mui/icons-material/TrendingUp';
import TrendingDown from '@mui/icons-material/TrendingDown';
import Ltr from './Ltr';

/**
 * Dashboard KPI tile. `value` is pre-formatted text (e.g. formatMoney(...)), so currencies are never mixed.
 * `trend` is a signed percentage string/number; `trendLabel` explains the comparison.
 */
export default function KpiCard({ label, value, icon: Icon, color = 'primary', caption, trend, trendLabel, loading, lowerIsBetter = false }) {
  const numericTrend = trend === undefined || trend === null ? null : Number(trend);
  const up = numericTrend !== null && numericTrend >= 0;
  const good = lowerIsBetter ? !up || numericTrend === 0 : up;
  return (
    <Card sx={{ p: 2.5, height: '100%' }}>
      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 2 }}>
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 500 }} noWrap>
            {label}
          </Typography>
          <Typography variant="h5" sx={{ mt: 1, fontVariantNumeric: 'tabular-nums' }}>
            {loading ? <Skeleton width={120} /> : <Ltr>{value}</Ltr>}
          </Typography>
        </Box>
        {Icon && (
          <Box
            sx={(theme) => ({
              width: 44,
              height: 44,
              borderRadius: 2.5,
              display: 'grid',
              placeItems: 'center',
              flexShrink: 0,
              color: theme.palette[color].main,
              bgcolor: alpha(theme.palette[color].main, theme.palette.mode === 'light' ? 0.1 : 0.2),
            })}
          >
            <Icon />
          </Box>
        )}
      </Box>
      {(numericTrend !== null || caption) && (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, mt: 1.5, flexWrap: 'wrap' }}>
          {numericTrend !== null && (
            <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.25, color: good ? 'success.main' : 'error.main', fontWeight: 600, fontSize: '0.8rem' }}>
              {up ? <TrendingUp sx={{ fontSize: 16 }} /> : <TrendingDown sx={{ fontSize: 16 }} />}
              <Ltr>{`${up ? '+' : ''}${numericTrend}%`}</Ltr>
            </Box>
          )}
          <Typography variant="caption" color="text.secondary">
            {numericTrend !== null ? trendLabel : caption}
          </Typography>
        </Box>
      )}
    </Card>
  );
}
