import { dec, roundMoney } from '../../utils/money';

/** Estimated value of the chosen lines (the server makes the last part of a line add up exactly). */
export function returnTotal(doc, lines) {
  if (!doc) return dec(0);
  return doc.items.reduce((acc, item) => {
    const q = lines[item.itemId]?.qty;
    if (!q || !(Number(q) > 0)) return acc;
    return acc.plus(roundMoney(dec(item.unitNetPrice).times(dec(q)), doc.currency));
  }, dec(0));
}

/** How a return is settled: first what is still owed, the rest refunded or kept as credit. */
export function settlement(total, balanceDue) {
  const t = dec(total);
  const due = dec(balanceDue ?? 0);
  const reduction = t.lt(due) ? t : due;
  return { reduction, rest: t.minus(reduction) };
}
