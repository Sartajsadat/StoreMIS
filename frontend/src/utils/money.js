import Decimal from 'decimal.js';

// All money math on the client goes through decimal.js — never JavaScript floats.
// The API sends monetary values as strings (e.g. "10000.00") to keep them exact.
Decimal.set({ precision: 34, rounding: Decimal.ROUND_HALF_UP });

export const CURRENCY_DECIMALS = { AFN: 2, USD: 2 };

export const dec = (value) => {
  if (value === null || value === undefined || value === '') return new Decimal(0);
  try {
    return new Decimal(value);
  } catch {
    return new Decimal(0);
  }
};

export const add = (...values) => values.reduce((acc, v) => acc.plus(dec(v)), new Decimal(0));
export const sub = (a, b) => dec(a).minus(dec(b));
export const mul = (a, b) => dec(a).times(dec(b));
export const div = (a, b) => (dec(b).isZero() ? new Decimal(0) : dec(a).dividedBy(dec(b)));
export const sum = (items, pick = (x) => x) => items.reduce((acc, it) => acc.plus(dec(pick(it))), new Decimal(0));

export const roundMoney = (value, currency = 'AFN') =>
  dec(value).toDecimalPlaces(CURRENCY_DECIMALS[currency] ?? 2, Decimal.ROUND_HALF_UP);

/** Serialises a value for the API as a fixed-point string. */
export const toApiAmount = (value, currency = 'AFN') => roundMoney(value, currency).toFixed(CURRENCY_DECIMALS[currency] ?? 2);
