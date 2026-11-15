import { useTheme } from '@mui/material/styles';

/** Shared Recharts styling derived from the MUI theme: recessive grid/axes, text tokens, fixed series order. */
export function useChartTheme() {
  const theme = useTheme();
  const rtl = theme.direction === 'rtl';
  const axisTick = { fill: theme.palette.text.secondary, fontSize: 12, fontFamily: theme.typography.fontFamily };
  return {
    rtl,
    series: theme.palette.chart.series,
    grid: { stroke: theme.palette.chart.grid, strokeDasharray: '0', vertical: false },
    xAxis: { tick: axisTick, axisLine: { stroke: theme.palette.divider }, tickLine: false, reversed: rtl },
    yAxis: { tick: axisTick, axisLine: false, tickLine: false, tickMargin: 8, orientation: rtl ? 'right' : 'left', width: 64 },
    tooltip: {
      contentStyle: {
        background: theme.palette.background.paper,
        border: `1px solid ${theme.palette.divider}`,
        borderRadius: 8,
        boxShadow: '0 8px 24px rgba(16,24,40,0.14)',
        color: theme.palette.text.primary,
        fontFamily: theme.typography.fontFamily,
        fontSize: 13,
        direction: rtl ? 'rtl' : 'ltr',
        textAlign: rtl ? 'right' : 'left',
      },
      labelStyle: { color: theme.palette.text.secondary, marginBottom: 4 },
      itemStyle: { color: theme.palette.text.primary, padding: 0 },
      cursor: { stroke: theme.palette.text.disabled, strokeWidth: 1 },
    },
    legend: {
      wrapperStyle: { fontSize: 13, color: theme.palette.text.secondary, fontFamily: theme.typography.fontFamily, direction: rtl ? 'rtl' : 'ltr' },
      iconType: 'circle',
      iconSize: 10,
    },
    surface: theme.palette.background.paper,
  };
}

/** Compact axis numbers: 12,500 -> 12.5K. Latin digits keep axes readable in every language. */
export const compactNumber = (v) => new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 }).format(v);
