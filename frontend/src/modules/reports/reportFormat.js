import { formatDate, formatDateTime, formatMoney, formatNumber } from '../../utils/format';

/** Header label of a report column. */
export const columnLabel = (t, key) => t(`reports.columns.${key}`, key);

/** Currency of a money cell: the row's currency field, else the report's base currency. */
export const cellCurrency = (col, row, base) => (col.currencyField ? row?.[col.currencyField] : null) || base;

/** Text of a cell exactly as it is shown on screen and in the printed report. */
export function cellText(col, row, { t, base }) {
  const v = row?.[col.key];
  if (v === null || v === undefined || v === '') return '';
  switch (col.type) {
    case 'MONEY':
      return formatMoney(v, cellCurrency(col, row, base));
    case 'QTY':
      return `${formatNumber(v, { maxDecimals: 3 })}${col.unitField && row[col.unitField] ? ` ${row[col.unitField]}` : ''}`;
    case 'NUMBER':
      return formatNumber(v, { maxDecimals: 3 });
    case 'PERCENT':
      return `${formatNumber(v, { maxDecimals: 1 })}%`;
    case 'DATE':
      return formatDate(v);
    case 'DATETIME':
      return formatDateTime(v);
    case 'ENUM':
      // Profit and loss: an expense line names its category (seeded codes are translated).
      if (col.key === 'line' && row.category) return `    ${t(`expenses.categoryCodes.${row.category}`, row.category)}`;
      if (col.enumPrefix === 'expenses.categoryCodes') return t(`${col.enumPrefix}.${v}`, String(v));
      return t(`${col.enumPrefix}.${v}`, String(v));
    case 'BALANCES':
      return balancesText(v);
    default:
      return String(v);
  }
}

export function balancesText(map) {
  return Object.entries(map ?? {})
    .map(([cur, amount]) => formatMoney(amount, cur))
    .join(' · ');
}

/** Excel cell: real numbers for amounts and quantities (so they can be summed), text otherwise. */
export function excelCell(col, row, ctx) {
  const v = row?.[col.key];
  if (v === null || v === undefined || v === '') return null;
  switch (col.type) {
    case 'MONEY':
      return { v: Number(v), fmt: 'money' };
    case 'QTY':
      return { v: Number(v), fmt: 'qty' };
    case 'NUMBER':
      return { v: Number(v), fmt: 'int' };
    case 'PERCENT':
      return { v: Number(v), fmt: 'pct' };
    default:
      return cellText(col, row, ctx);
  }
}

/** Money and quantity columns also say which currency / unit they are in, since Excel cells hold only the number. */
function excelHeader(col, t, base) {
  const label = columnLabel(t, col.key);
  if (col.type === 'MONEY' && !col.currencyField) return `${label} (${base})`;
  return label;
}

/**
 * Rows for the Excel file: title, period, header, data and a totals line. Columns whose currency comes from the
 * row get an extra "currency" column so the numbers stay numeric.
 */
export function excelRows({ result, title, t, extraLines = [] }) {
  const base = result.baseCurrency;
  const ctx = { t, base };
  const cols = [];
  for (const col of result.columns) {
    cols.push({ col, kind: 'value' });
    if (col.type === 'MONEY' && col.currencyField && !cols.some((c) => c.kind === 'currency' && c.field === col.currencyField)) {
      cols.push({ kind: 'currency', field: col.currencyField });
    }
  }
  const header = cols.map((c) => ({ v: c.kind === 'currency' ? t('reports.columns.currency') : excelHeader(c.col, t, base), header: true }));
  const data = result.rows.map((row) => cols.map((c) => (c.kind === 'currency' ? row[c.field] ?? '' : excelCell(c.col, row, ctx))));
  const totals = result.totals ?? {};
  const hasTotals = result.columns.some((c) => typeof totals[c.key] !== 'object' && totals[c.key] !== undefined && totals[c.key] !== null);
  const totalRow = hasTotals
    ? cols.map((c, i) => {
        if (i === 0) return { v: t('reports.total'), bold: true };
        if (c.kind === 'currency' || (c.col.type === 'MONEY' && c.col.currencyField)) return null;
        const v = totals[c.col.key];
        if (v === null || v === undefined || typeof v === 'object') return null;
        return { ...excelCell(c.col, totals, ctx), bold: true };
      })
    : null;
  const rows = [
    [{ v: title, title: true }],
    [`${formatDate(result.from)} — ${formatDate(result.to)}`],
    ...extraLines.map((l) => [l]),
    [],
    header,
    ...data,
  ];
  const boldRows = [];
  if (totalRow) {
    boldRows.push(rows.length);
    rows.push(totalRow);
  }
  const widths = cols.map((c) => (c.kind === 'currency' ? 8 : { MONEY: 16, QTY: 12, DATE: 12, DATETIME: 17, NUMBER: 10, PERCENT: 9, USER_TEXT: 30, BALANCES: 28 }[c.col.type] ?? 18));
  return { rows, widths, boldRows };
}
