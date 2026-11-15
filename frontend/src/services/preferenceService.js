import api, { unwrap } from './apiClient';

export const preferenceService = {
  get: () => unwrap(api.get('/me/preferences')),
  update: (prefs) => unwrap(api.put('/me/preferences', prefs)),
};
