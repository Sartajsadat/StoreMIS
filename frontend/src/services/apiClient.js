import axios from 'axios';
import i18n from '../i18n';
import { tokenStore } from './tokenStore';

export const API_BASE = '/api/v1';

const api = axios.create({ baseURL: API_BASE, timeout: 30000, withCredentials: true });

api.interceptors.request.use((config) => {
  const token = tokenStore.get();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  config.headers['Accept-Language'] = i18n.language;
  return config;
});

// One shared refresh request, however many calls fail with 401 at the same moment.
let refreshPromise = null;
export function refreshAccessToken() {
  if (!refreshPromise) {
    refreshPromise = axios
      .post(`${API_BASE}/auth/refresh`, null, { withCredentials: true })
      .then((res) => {
        const token = res.data?.data?.accessToken;
        tokenStore.set(token);
        return res.data.data;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

const AUTH_PATHS = ['/auth/login', '/auth/refresh', '/auth/logout'];

api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config;
    const status = error.response?.status;
    const isAuthCall = AUTH_PATHS.some((p) => original?.url?.endsWith(p));
    if (status === 401 && original && !original._retried && !isAuthCall) {
      original._retried = true;
      try {
        await refreshAccessToken();
        return api(original);
      } catch {
        tokenStore.clear();
        tokenStore.emitSessionExpired();
      }
    }
    return Promise.reject(normalizeError(error));
  },
);

/** Converts any Axios error into { status, code, params, fieldErrors }. */
export function normalizeError(error) {
  if (error?.isNormalized) return error;
  const status = error?.response?.status ?? 0;
  const body = error?.response?.data;
  const apiError = body?.error;
  return {
    isNormalized: true,
    status,
    code: apiError?.code ?? (status === 0 ? 'NETWORK' : status === 403 ? 'FORBIDDEN' : status === 404 ? 'NOT_FOUND' : 'GENERIC'),
    params: apiError?.params ?? {},
    fieldErrors: apiError?.fieldErrors ?? [],
  };
}

/** Translates a normalized error to a message in the current language. */
export function errorMessage(t, error) {
  const e = normalizeError(error);
  if (e.code === 'NETWORK') return t('errors.network');
  const key = `errors.${e.code}`;
  return i18n.exists(key) ? t(key, e.params) : t('errors.generic');
}

/** Unwraps the { success, data } envelope. */
export const unwrap = (promise) => promise.then((res) => res.data?.data);

export default api;
