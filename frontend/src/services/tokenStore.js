// The access token lives in memory only. The refresh token is an httpOnly cookie set by the
// backend, so scripts can never read it; a page reload silently exchanges it for a new access token.
let accessToken = null;
const listeners = new Set();

export const tokenStore = {
  get: () => accessToken,
  set: (token) => {
    accessToken = token;
  },
  clear: () => {
    accessToken = null;
  },
  onSessionExpired: (fn) => {
    listeners.add(fn);
    return () => listeners.delete(fn);
  },
  emitSessionExpired: () => listeners.forEach((fn) => fn()),
};
