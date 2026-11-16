import { describe, expect, it } from 'vitest';
import { cellText, excelRows } from './reportFormat';

const t = (key, fallback) => ({ 'reports.columns.amount': 'Amount', 'reports.columns.currency': 'Currency', 'reports.total': 'Total', 'status.PAID': 'Paid' }[key] ?? fallback ?? key);

const result = {
  from: '2026-11-01',
  to: '2026-11-16',
  baseCurrency: 'AFN',
  columns: [
    { key: 'name', type: 'USER_TEXT' },
    { key: 'amount', type: 'MONEY', currencyField: 'currency' },
    { key: 'base', type: 'MONEY' },
    { key: 'qty', type: 'QTY', unitField: 'unit' },
    { key: 'status', type: 'ENUM', enumPrefix: 'status' },
  ],
  rows: [{ name: 'TV', amount: '100.5', currency: 'USD', base: '7035', qty: '2', unit: 'Piece', status: 'PAID' }],
  totals: { base: '7035' },
};

describe('reportFormat', () => {
  it('formats cells by type', () => {
    const ctx = { t, base: 'AFN' };
    const row = result.rows[0];
    expect(cellText(result.columns[1], row, ctx)).toBe('100.50 USD');
    expect(cellText(result.columns[2], row, ctx)).toBe('7,035 AFN');
    expect(cellText(result.columns[3], row, ctx)).toBe('2 Piece');
    expect(cellText(result.columns[4], row, ctx)).toBe('Paid');
  });

  it('builds Excel rows with numeric amounts, a currency column and totals', () => {
    const { rows, boldRows } = excelRows({ result, title: 'Report', t });
    const header = rows[3];
    expect(header.map((h) => h.v)).toEqual(['name', 'Amount', 'Currency', 'base (AFN)', 'qty', 'status']);
    expect(rows[4][1]).toEqual({ v: 100.5, fmt: 'money' });
    expect(rows[4][2]).toBe('USD');
    const total = rows[boldRows[0]];
    expect(total[0].v).toBe('Total');
    expect(total[3]).toMatchObject({ v: 7035, bold: true });
  });
});
