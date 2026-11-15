import api, { unwrap, refreshAccessToken } from './apiClient';
import { tokenStore } from './tokenStore';

export const authService = {
  async login(username, password) {
    const data = await unwrap(api.post('/auth/login', { username, password }));
    tokenStore.set(data.accessToken);
    return data; // { accessToken, expiresIn, user }
  },
  /** Restores a session from the refresh cookie; resolves to null if there is none. */
  async restore() {
    try {
      const data = await refreshAccessToken();
      return data?.user ?? (await authService.me());
    } catch {
      return null;
    }
  },
  me: () => unwrap(api.get('/auth/me')),
  async logout() {
    try {
      await api.post('/auth/logout');
    } finally {
      tokenStore.clear();
    }
  },
  changePassword: (currentPassword, newPassword) =>
    unwrap(api.post('/auth/change-password', { currentPassword, newPassword })),
};
