import api, { unwrap } from './apiClient';

export const customerPaymentService = {
  list: (params) => unwrap(api.get('/customer-payments', { params })),
  get: (id) => unwrap(api.get(`/customer-payments/${id}`)),
  create: (body) => unwrap(api.post('/customer-payments', body)),
  reverse: (id, reason) => unwrap(api.post(`/customer-payments/${id}/reverse`, { reason })),
};
