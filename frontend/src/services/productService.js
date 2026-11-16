import api, { unwrap } from './apiClient';

export const productService = {
  list: (params) => unwrap(api.get('/products', { params })),
  search: (q, limit = 20) => unwrap(api.get('/products/search', { params: { q, limit } })),
  get: (id) => unwrap(api.get(`/products/${id}`)),
  create: (body) => unwrap(api.post('/products', body)),
  update: (id, body) => unwrap(api.put(`/products/${id}`, body)),
  setActive: (id, active) => unwrap(api.patch(`/products/${id}/status`, { active })),
  uploadImage: (id, file) => {
    const form = new FormData();
    form.append('file', file);
    return unwrap(api.post(`/products/${id}/image`, form, { headers: { 'Content-Type': 'multipart/form-data' } }));
  },
  removeImage: (id) => unwrap(api.delete(`/products/${id}/image`)),
};
