import api, { unwrap } from './apiClient';

export const supplierPaymentService = {
  list: (params) => unwrap(api.get('/supplier-payments', { params })),
  get: (id) => unwrap(api.get(`/supplier-payments/${id}`)),
  create: (body) => unwrap(api.post('/supplier-payments', body)),
  reverse: (id, reason) => unwrap(api.post(`/supplier-payments/${id}/reverse`, { reason })),
};
