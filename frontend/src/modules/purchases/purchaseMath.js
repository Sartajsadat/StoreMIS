import Decimal from 'decimal.js';
import { dec } from '../../utils/money';

/** Mirrors the backend PurchaseCalculator for the live preview (the server result is authoritative). */
export function calculatePurchase(lines, overallDiscount) {
  let subtotal = new Decimal(0);
  let itemDiscounts = new Decimal(0);
  const totals = lines.map((l) => {
    const gross = dec(l.quantity).times(dec(l.unitCost)).toDecimalPlaces(2, Decimal.ROUND_HALF_UP);
    const discount = dec(l.discount).toDecimalPlaces(2, Decimal.ROUND_HALF_UP);
    subtotal = subtotal.plus(gross);
    itemDiscounts = itemDiscounts.plus(discount);
    return { gross, discount, lineTotal: gross.minus(discount), invalid: discount.greaterThan(gross) };
  });
  const net = subtotal.minus(itemDiscounts);
  const overall = dec(overallDiscount).toDecimalPlaces(2, Decimal.ROUND_HALF_UP);
  return {
    lines: totals,
    subtotal,
    itemDiscounts,
    overall,
    grandTotal: Decimal.max(net.minus(overall), 0),
    overallInvalid: overall.greaterThan(net),
  };
}
