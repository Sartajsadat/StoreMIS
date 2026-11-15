import api, { unwrap } from './apiClient';

export const settingsService = {
  /** Public, unauthenticated: business name, logo URL, default language and theme (used by the login page). */
  getPublic: () => unwrap(api.get('/settings/public')),
};
