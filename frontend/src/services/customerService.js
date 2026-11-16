import api, { unwrap } from './apiClient';

export const customerService = {
  list: (params) => unwrap(api.get('/customers', { params })),
  get: (id) => unwrap(api.get(`/customers/${id}`)),
  walkIn: () => unwrap(api.get('/customers/walk-in')),
  create: (body) => unwrap(api.post('/customers', body)),
  update: (id, body) => unwrap(api.put(`/customers/${id}`, body)),
  setActive: (id, active) => unwrap(api.patch(`/customers/${id}/status`, { active })),
  openingBalance: (id, body) => unwrap(api.post(`/customers/${id}/opening-balance`, body)),
  statement: (id, params) => unwrap(api.get(`/customers/${id}/statement`, { params })),
  receivables: () => unwrap(api.get('/customers/receivables')),
};
