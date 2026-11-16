import api, { unwrap } from './apiClient';

export const catalogService = {
  categories: (active) => unwrap(api.get('/categories', { params: { active } })),
  createCategory: (body) => unwrap(api.post('/categories', body)),
  updateCategory: (id, body) => unwrap(api.put(`/categories/${id}`, body)),
  setCategoryActive: (id, active) => unwrap(api.patch(`/categories/${id}/status`, { active })),
  units: (active) => unwrap(api.get('/units', { params: { active } })),
  createUnit: (body) => unwrap(api.post('/units', body)),
  updateUnit: (id, body) => unwrap(api.put(`/units/${id}`, body)),
  setUnitActive: (id, active) => unwrap(api.patch(`/units/${id}/status`, { active })),
};
