import dayjs from 'dayjs';
import { dec, CURRENCY_DECIMALS } from './money';

// Latin digits by default so invoice numbers, amounts and phone numbers stay readable in every language.
const numberLocale = (digitStyle) => (digitStyle === 'PERSIAN' ? 'fa-AF' : 'en-US');

export function formatNumber(value, { minDecimals = 0, maxDecimals = 3, digitStyle = 'LATIN' } = {}) {
  if (value === null || value === undefined || value === '') return '';
  const d = dec(value);
  return new Intl.NumberFormat(numberLocale(digitStyle), {
    minimumFractionDigits: minDecimals,
    maximumFractionDigits: maxDecimals,
  }).format(d.toNumber());
}

/** "10,000 AFN" / "650.50 USD". Shows decimals only when the amount has them. */
export function formatMoney(value, currency = 'AFN', { digitStyle = 'LATIN', alwaysDecimals = false } = {}) {
  if (value === null || value === undefined || value === '') return '';
  const decimals = CURRENCY_DECIMALS[currency] ?? 2;
  const d = dec(value).toDecimalPlaces(decimals);
  const hasFraction = !d.isInteger();
  const text = new Intl.NumberFormat(numberLocale(digitStyle), {
    minimumFractionDigits: alwaysDecimals || hasFraction ? decimals : 0,
    maximumFractionDigits: decimals,
  }).format(d.toNumber());
  return `${text} ${currency}`;
}

export function formatDate(value, pattern = 'YYYY-MM-DD') {
  if (!value) return '';
  const d = dayjs(value);
  return d.isValid() ? d.format(pattern) : '';
}

export const formatDateTime = (value) => formatDate(value, 'YYYY-MM-DD HH:mm');
