import api, { unwrap } from './apiClient';

export const purchaseService = {
  list: (params) => unwrap(api.get('/purchases', { params })),
  open: (supplierId) => unwrap(api.get('/purchases/open', { params: { supplierId } })),
  get: (id) => unwrap(api.get(`/purchases/${id}`)),
  create: (body) => unwrap(api.post('/purchases', body)),
  update: (id, body) => unwrap(api.put(`/purchases/${id}`, body)),
  remove: (id) => unwrap(api.delete(`/purchases/${id}`)),
  confirm: (id, body) => unwrap(api.post(`/purchases/${id}/confirm`, body)),
  cancel: (id, body) => unwrap(api.post(`/purchases/${id}/cancel`, body)),
};
