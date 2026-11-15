import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { compactNumber, useChartTheme } from './chartTheme';

/**
 * Change over time. series: [{ key, label }] (labels already translated). Colours follow the series order, never rank.
 * A legend appears only for two or more series.
 */
export default function TrendLineChart({ data, xKey, series, valueFormatter = compactNumber, tooltipFormatter }) {
  const c = useChartTheme();
  return (
    <ResponsiveContainer width="100%" height="100%">
      <LineChart data={data} margin={{ top: 8, right: 12, left: 4, bottom: 0 }}>
        <CartesianGrid {...c.grid} />
        <XAxis dataKey={xKey} {...c.xAxis} />
        <YAxis {...c.yAxis} tickFormatter={valueFormatter} />
        <Tooltip {...c.tooltip} formatter={tooltipFormatter} />
        {series.length > 1 && <Legend {...c.legend} />}
        {series.map((s, i) => (
          <Line
            key={s.key}
            type="monotone"
            dataKey={s.key}
            name={s.label}
            stroke={c.series[i % c.series.length]}
            strokeWidth={2}
            dot={false}
            isAnimationActive={false}
            activeDot={{ r: 5, strokeWidth: 2, stroke: c.surface }}
          />
        ))}
      </LineChart>
    </ResponsiveContainer>
  );
}
