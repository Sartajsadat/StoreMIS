import api, { unwrap } from './apiClient';

export const sequenceService = {
  list: () => unwrap(api.get('/settings/sequences')),
  update: (type, prefix) => unwrap(api.put(`/settings/sequences/${type}`, { prefix })),
};
