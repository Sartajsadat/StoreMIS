import api, { unwrap } from './apiClient';

export const expenseService = {
  categories: (params) => unwrap(api.get('/expense-categories', { params })),
  createCategory: (body) => unwrap(api.post('/expense-categories', body)),
  updateCategory: (id, body) => unwrap(api.put(`/expense-categories/${id}`, body)),
  setCategoryActive: (id, active) => unwrap(api.patch(`/expense-categories/${id}/status`, { active })),
  list: (params) => unwrap(api.get('/expenses', { params })),
  totals: (params) => unwrap(api.get('/expenses/totals', { params })),
  get: (id) => unwrap(api.get(`/expenses/${id}`)),
  create: (body) => unwrap(api.post('/expenses', body)),
  void: (id, reason) => unwrap(api.post(`/expenses/${id}/void`, { reason })),
  uploadAttachment: (id, file) => {
    const form = new FormData();
    form.append('file', file);
    return unwrap(api.post(`/expenses/${id}/attachment`, form, { headers: { 'Content-Type': 'multipart/form-data' } }));
  },
  /** Opens the attached receipt in a new tab (the file needs the user's token, so it is fetched first). */
  openAttachment: async (id) => {
    const res = await api.get(`/expenses/${id}/attachment`, { responseType: 'blob' });
    const url = URL.createObjectURL(res.data);
    window.open(url, '_blank', 'noopener');
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  },
};
