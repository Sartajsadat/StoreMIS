import api, { unwrap } from './apiClient';

export const salesService = {
  confirm: (body) => unwrap(api.post('/sales/confirm', body)),
  drafts: () => unwrap(api.get('/sales/drafts')),
  createDraft: (body) => unwrap(api.post('/sales/drafts', body)),
  updateDraft: (id, body) => unwrap(api.put(`/sales/drafts/${id}`, body)),
  deleteDraft: (id) => unwrap(api.delete(`/sales/drafts/${id}`)),
  confirmDraft: (id, body) => unwrap(api.post(`/sales/drafts/${id}/confirm`, body)),
  list: (params) => unwrap(api.get('/sales', { params })),
  get: (id) => unwrap(api.get(`/sales/${id}`)),
  receipt: (id) => unwrap(api.get(`/sales/${id}/receipt`)),
  open: (customerId) => unwrap(api.get('/sales/open', { params: { customerId } })),
  customerTotals: (customerId) => unwrap(api.get('/sales/customer-totals', { params: { customerId } })),
  priceHint: (productId, customerId) => unwrap(api.get('/sales/price-hint', { params: { productId, customerId } })),
  cancel: (id, body) => unwrap(api.post(`/sales/${id}/cancel`, body)),
};
