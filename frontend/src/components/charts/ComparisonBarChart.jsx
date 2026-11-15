import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { compactNumber, useChartTheme } from './chartTheme';

/** Magnitude comparison across categories. Thin bars with 4px rounded ends and a 2px gap between groups' bars. */
export default function ComparisonBarChart({ data, xKey, series, stacked, valueFormatter = compactNumber, tooltipFormatter }) {
  const c = useChartTheme();
  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={data} margin={{ top: 8, right: 12, left: 4, bottom: 0 }} barGap={2} barCategoryGap="30%">
        <CartesianGrid {...c.grid} />
        <XAxis dataKey={xKey} {...c.xAxis} />
        <YAxis {...c.yAxis} tickFormatter={valueFormatter} />
        <Tooltip {...c.tooltip} cursor={{ fill: 'rgba(127,127,127,0.08)' }} formatter={tooltipFormatter} />
        {series.length > 1 && <Legend {...c.legend} />}
        {series.map((s, i) => (
          <Bar
            key={s.key}
            dataKey={s.key}
            name={s.label}
            fill={c.series[i % c.series.length]}
            radius={stacked && i < series.length - 1 ? 0 : [4, 4, 0, 0]}
            maxBarSize={28}
            isAnimationActive={false}
            stackId={stacked ? 'stack' : undefined}
            stroke={stacked ? c.surface : undefined}
            strokeWidth={stacked ? 2 : 0}
          />
        ))}
      </BarChart>
    </ResponsiveContainer>
  );
}
