import api, { unwrap } from './apiClient';

export const supplierService = {
  list: (params) => unwrap(api.get('/suppliers', { params })),
  get: (id) => unwrap(api.get(`/suppliers/${id}`)),
  create: (body) => unwrap(api.post('/suppliers', body)),
  update: (id, body) => unwrap(api.put(`/suppliers/${id}`, body)),
  setActive: (id, active) => unwrap(api.patch(`/suppliers/${id}/status`, { active })),
  openingBalance: (id, body) => unwrap(api.post(`/suppliers/${id}/opening-balance`, body)),
  statement: (id, params) => unwrap(api.get(`/suppliers/${id}/statement`, { params })),
  payables: () => unwrap(api.get('/suppliers/payables')),
};
