import api, { unwrap } from './apiClient';

export const settingsService = {
  /** Public, unauthenticated: business name, logo URL, default language and theme (used by the login page). */
  getPublic: () => unwrap(api.get('/settings/public')),
  getBusiness: () => unwrap(api.get('/settings/business')),
  updateBusiness: (body) => unwrap(api.put('/settings/business', body)),
  uploadLogo: (file) => {
    const form = new FormData();
    form.append('file', file);
    return unwrap(api.post('/settings/business/logo', form, { headers: { 'Content-Type': 'multipart/form-data' } }));
  },
  removeLogo: () => unwrap(api.delete('/settings/business/logo')),
  currencies: () => unwrap(api.get('/currencies')),
};
