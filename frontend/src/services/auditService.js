import api, { unwrap } from './apiClient';

export const AUDIT_MODULES = ['AUTH', 'USERS', 'SETTINGS', 'PRODUCTS', 'CATEGORIES', 'UNITS', 'INVENTORY', 'PURCHASES', 'SUPPLIERS', 'SALES', 'CUSTOMERS', 'PAYMENTS', 'RETURNS', 'CASH', 'EXPENSES', 'CURRENCY'];
export const AUDIT_ACTIONS = ['LOGIN', 'LOGIN_FAILED', 'LOGOUT', 'PASSWORD_CHANGE', 'PASSWORD_RESET', 'CREATE', 'UPDATE', 'ACTIVATE', 'DEACTIVATE', 'CONFIRM', 'CANCEL', 'REVERSE', 'VOID', 'PAYMENT', 'ADJUST', 'RETURN', 'CLOSE', 'REOPEN', 'SETTINGS_CHANGE', 'UPLOAD'];

export const auditService = {
  search: (params) => unwrap(api.get('/audit-logs', { params })),
};
