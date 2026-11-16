import { describe, expect, it } from 'vitest';
import { returnTotal, settlement } from './returnMath';

const doc = { currency: 'AFN', items: [{ itemId: 1, unitNetPrice: '966.6667' }, { itemId: 2, unitNetPrice: '5000' }] };

describe('returnMath', () => {
  it('values lines at their net price', () => {
    expect(returnTotal(doc, { 1: { qty: '1' }, 2: { qty: '2' } }).toFixed(2)).toBe('10966.67');
    expect(returnTotal(doc, { 1: { qty: '' } }).toFixed(2)).toBe('0.00');
  });

  it('reduces what is owed first, then refunds the rest', () => {
    const s = settlement('5000', '4000');
    expect(s.reduction.toFixed(2)).toBe('4000.00');
    expect(s.rest.toFixed(2)).toBe('1000.00');
    expect(settlement('500', '4000').rest.toFixed(2)).toBe('0.00');
  });
});
