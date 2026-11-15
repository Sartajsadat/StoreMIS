import { add, mul, sub, sum, roundMoney, toApiAmount, div } from './money';
import { formatMoney } from './format';
import { normalizeDecimalInput } from '../components/form/FormMoneyField';

describe('money utilities', () => {
  it('adds without floating-point drift', () => {
    expect(add('0.1', '0.2').toString()).toBe('0.3');
    expect(sum([{ a: '1000.10' }, { a: '2000.20' }, { a: '0.70' }], (x) => x.a).toFixed(2)).toBe('3001.00');
  });

  it('computes the spec weighted-average example', () => {
    const value = add(mul(10, 300), mul(10, 400));
    expect(div(value, 20).toString()).toBe('350');
    expect(sub(500, 350).toString()).toBe('150');
  });

  it('rounds half up to currency decimals and serialises as strings', () => {
    expect(roundMoney('10.005', 'AFN').toFixed(2)).toBe('10.01');
    expect(toApiAmount(4000, 'AFN')).toBe('4000.00');
  });

  it('formats money with Latin digits and the currency code', () => {
    expect(formatMoney('10000', 'AFN')).toBe('10,000 AFN');
    expect(formatMoney('650.5', 'USD')).toBe('650.50 USD');
    expect(formatMoney('6000', 'AFN', { alwaysDecimals: true })).toBe('6,000.00 AFN');
  });

  it('normalises Persian digits and separators typed in amount fields', () => {
    expect(normalizeDecimalInput('۱۲٬۵۰۰٫۷۵', 2)).toBe('12500.75');
    expect(normalizeDecimalInput('1,234.5678', 2)).toBe('1234.56');
    expect(normalizeDecimalInput('12.3.4', 3)).toBe('12.34');
    expect(normalizeDecimalInput('7.5', 0)).toBe('7');
  });
});
