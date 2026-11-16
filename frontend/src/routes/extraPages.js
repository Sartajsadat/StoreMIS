import { NAV } from '../layouts/navConfig';
import { PERMISSIONS as P } from '../utils/permissions';

const group = (key) => NAV.find((g) => g.key === key);

/**
 * Pages reachable from inside the app but not listed in the sidebar (detail and edit screens).
 * Used for routing, titles, breadcrumbs and access checks.
 */
export const EXTRA_PAGES = [
  { key: 'uiKit', labelKey: 'nav.uiKit', path: '/dev/ui-kit', group: null },
  { key: 'supplierDetail', labelKey: 'suppliers.details', path: '/suppliers/:id', group: group('suppliers'), access: { anyOf: [P.RECORD_PURCHASES, P.PAY_SUPPLIERS] } },
  { key: 'purchaseDetail', labelKey: 'purchases.detailTitle', path: '/purchases/:id', group: group('purchases'), access: { anyOf: [P.RECORD_PURCHASES, P.PAY_SUPPLIERS] } },
  { key: 'purchaseEdit', labelKey: 'purchases.editTitle', path: '/purchases/:id/edit', group: group('purchases'), access: { anyOf: [P.RECORD_PURCHASES] } },
];
