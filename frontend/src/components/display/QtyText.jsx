import { formatNumber } from '../../utils/format';
import Ltr from './Ltr';

/** Quantity with up to 3 decimals and an optional unit label, e.g. "12.5 m". */
export default function QtyText({ value, unit, signed = false, color }) {
  if (value === null || value === undefined || value === '') return null;
  const n = Number(value);
  const text = `${signed && n > 0 ? '+' : ''}${formatNumber(value, { maxDecimals: 3 })}`;
  return (
    <span style={{ color, fontVariantNumeric: 'tabular-nums' }}>
      <Ltr>{text}</Ltr>
      {unit ? ` ${unit}` : ''}
    </span>
  );
}
