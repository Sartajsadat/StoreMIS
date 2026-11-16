import Decimal from 'decimal.js';
import { dec } from '../../utils/money';

const round2 = (d) => d.toDecimalPlaces(2, Decimal.ROUND_HALF_UP);

/** Mirrors the backend SaleCalculator for the live POS preview (the server result is authoritative). */
export function calculateSale(lines, overallDiscount) {
  let subtotal = new Decimal(0);
  let itemDiscounts = new Decimal(0);
  const totals = lines.map((l) => {
    const gross = round2(dec(l.quantity).times(dec(l.unitPrice)));
    const discount = round2(dec(l.discount));
    subtotal = subtotal.plus(gross);
    itemDiscounts = itemDiscounts.plus(discount);
    return { gross, discount, lineTotal: gross.minus(discount), invalid: discount.greaterThan(gross) };
  });
  const net = subtotal.minus(itemDiscounts);
  const overall = round2(dec(overallDiscount));
  return { lines: totals, subtotal, itemDiscounts, overall, grandTotal: Decimal.max(net.minus(overall), 0), overallInvalid: overall.greaterThan(net) };
}

/** How much of the invoice (sale currency) a payment line covers. Rates are base units per currency unit. */
export function covers(payment, saleCurrency, saleRate) {
  const payRate = payment.currency === saleCurrency ? dec(saleRate) : dec(payment.rate);
  if (dec(saleRate).isZero()) return new Decimal(0);
  return round2(dec(payment.amount).times(payRate).dividedBy(dec(saleRate)));
}
