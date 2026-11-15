import { canAccess, hasPermission, PERMISSIONS } from './permissions';
import { NAV, filterNav, flattenNav, findNavTrail } from '../layouts/navConfig';
import { resolveRange } from './dateRange';
import dayjs from 'dayjs';

const admin = { role: 'ADMIN', permissions: [] };
const cashier = { role: 'ENTRY_USER', permissions: [PERMISSIONS.RECEIVE_CUSTOMER_PAYMENTS] };

describe('permissions', () => {
  it('gives the admin everything', () => {
    expect(canAccess(admin, { adminOnly: true })).toBe(true);
    expect(hasPermission(admin, PERMISSIONS.ADJUST_STOCK)).toBe(true);
  });

  it('limits entry users to baseline + granted toggles', () => {
    expect(canAccess(cashier, undefined)).toBe(true);
    expect(canAccess(cashier, { adminOnly: true })).toBe(false);
    expect(canAccess(cashier, { anyOf: [PERMISSIONS.RECEIVE_CUSTOMER_PAYMENTS] })).toBe(true);
    expect(canAccess(cashier, { anyOf: [PERMISSIONS.RECORD_PURCHASES] })).toBe(false);
    expect(canAccess(null, undefined)).toBe(false);
  });

  it('builds a reduced sidebar for entry users', () => {
    const keys = filterNav(NAV, (a) => canAccess(cashier, a)).map((g) => g.key);
    expect(keys).toContain('sales');
    expect(keys).not.toContain('reports');
    expect(keys).not.toContain('userManagement');
    expect(keys).not.toContain('purchases');
    const settings = filterNav(NAV, (a) => canAccess(cashier, a)).find((g) => g.key === 'settings');
    expect(settings.children.map((c) => c.key)).toEqual(['appearance']);
  });

  it('has unique paths and resolves breadcrumbs', () => {
    const paths = flattenNav().map((i) => i.path);
    expect(new Set(paths).size).toBe(paths.length);
    expect(findNavTrail('/inventory/low-stock').group.key).toBe('inventory');
  });
});

describe('date ranges', () => {
  it('starts the week on Saturday', () => {
    const wednesday = dayjs('2026-11-18');
    expect(resolveRange('WEEK', {}, wednesday)).toEqual({ from: '2026-11-14', to: '2026-11-18' });
    expect(resolveRange('MONTH', {}, wednesday).from).toBe('2026-11-01');
  });
});
