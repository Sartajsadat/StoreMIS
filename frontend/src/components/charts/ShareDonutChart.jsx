import { Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import { useChartTheme } from './chartTheme';

/**
 * Part-to-whole for a handful of categories (<= 6; fold the rest into "Other" before passing data).
 * data: [{ name, value }] with translated names. Slices are separated by a 2px surface gap.
 */
export default function ShareDonutChart({ data, tooltipFormatter }) {
  const c = useChartTheme();
  return (
    <ResponsiveContainer width="100%" height="100%">
      <PieChart>
        <Pie data={data} dataKey="value" nameKey="name" innerRadius="58%" outerRadius="85%" paddingAngle={1} stroke={c.surface} strokeWidth={2} isAnimationActive={false}>
          {data.map((d, i) => (
            <Cell key={d.name} fill={c.series[i % c.series.length]} />
          ))}
        </Pie>
        <Tooltip {...c.tooltip} formatter={tooltipFormatter} />
        <Legend {...c.legend} layout="vertical" align={c.rtl ? 'left' : 'right'} verticalAlign="middle" />
      </PieChart>
    </ResponsiveContainer>
  );
}
