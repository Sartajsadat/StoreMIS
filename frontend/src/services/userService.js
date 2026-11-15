import api, { unwrap } from './apiClient';

export const userService = {
  list: (params) => unwrap(api.get('/users', { params })),
  get: (id) => unwrap(api.get(`/users/${id}`)),
  create: (body) => unwrap(api.post('/users', body)),
  update: (id, body) => unwrap(api.put(`/users/${id}`, body)),
  setActive: (id, active) => unwrap(api.patch(`/users/${id}/status`, { active })),
  resetPassword: (id, newPassword) => unwrap(api.post(`/users/${id}/reset-password`, { newPassword })),
  permissions: () => unwrap(api.get('/permissions')),
};
