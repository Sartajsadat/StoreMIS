import { calculateSale, covers } from './saleMath';

describe('POS math', () => {
  it('totals lines with discounts', () => {
    const r = calculateSale([{ quantity: '2', unitPrice: '5000', discount: '200' }, { quantity: '1', unitPrice: '1500', discount: '' }], '300');
    expect(r.subtotal.toFixed(2)).toBe('11500.00');
    expect(r.grandTotal.toFixed(2)).toBe('11000.00');
  });

  it('converts a USD payment into an AFN invoice at the payment rate', () => {
    expect(covers({ currency: 'USD', amount: '100', rate: '70' }, 'AFN', '1').toFixed(2)).toBe('7000.00');
    expect(covers({ currency: 'AFN', amount: '7000', rate: '1' }, 'AFN', '1').toFixed(2)).toBe('7000.00');
  });

  it('zero is not a positive amount (no "change 0" or "credit 0")', () => {
    const r = calculateSale([{ quantity: '1', unitPrice: '5000' }], '');
    const remaining = r.grandTotal.minus(covers({ currency: 'AFN', amount: '5000' }, 'AFN', '1'));
    expect(remaining.gt(0)).toBe(false);
    expect(remaining.lt(0)).toBe(false);
  });
});
