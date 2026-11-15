export const ROLES = { ADMIN: 'ADMIN', ENTRY_USER: 'ENTRY_USER' };

// Toggle permissions an Admin can grant to an Entry User (see docs/phase-1/02-modules-and-roles.md).
export const PERMISSIONS = {
  RECEIVE_CUSTOMER_PAYMENTS: 'RECEIVE_CUSTOMER_PAYMENTS',
  GIVE_DISCOUNT: 'GIVE_DISCOUNT',
  RECORD_PURCHASES: 'RECORD_PURCHASES',
  PAY_SUPPLIERS: 'PAY_SUPPLIERS',
  RECORD_EXPENSES: 'RECORD_EXPENSES',
  PROCESS_SALES_RETURNS: 'PROCESS_SALES_RETURNS',
  VIEW_COST_PRICES: 'VIEW_COST_PRICES',
  ADJUST_STOCK: 'ADJUST_STOCK',
  VIEW_ALL_SALES: 'VIEW_ALL_SALES',
};

export const isAdmin = (user) => user?.role === ROLES.ADMIN;

export const hasPermission = (user, code) => {
  if (!user) return false;
  if (isAdmin(user)) return true;
  return Array.isArray(user.permissions) && user.permissions.includes(code);
};

/**
 * access: undefined            -> any signed-in user
 *         { adminOnly: true }  -> Admin only
 *         { anyOf: [codes] }   -> Admin, or Entry User holding at least one of the permissions
 */
export function canAccess(user, access) {
  if (!user) return false;
  if (!access) return true;
  if (isAdmin(user)) return true;
  if (access.adminOnly) return false;
  if (access.anyOf) return access.anyOf.some((code) => hasPermission(user, code));
  return true;
}
