import api, { unwrap } from './apiClient';

export const currencyService = {
  currentRates: () => unwrap(api.get('/exchange-rates/current')),
  history: (currency, params) => unwrap(api.get('/exchange-rates', { params: { currency, ...params } })),
  setRate: (body) => unwrap(api.post('/exchange-rates', body)),
};
