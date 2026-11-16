import api, { unwrap } from './apiClient';

export const MOVEMENT_TYPES = ['OPENING', 'PURCHASE', 'PURCHASE_CANCEL', 'PURCHASE_RETURN', 'SALE', 'SALE_CANCEL', 'SALE_RETURN', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'DAMAGE'];
export const ADJUSTMENT_TYPES = ['OPENING', 'INCREASE', 'DECREASE', 'DAMAGE'];

export const inventoryService = {
  stock: (params) => unwrap(api.get('/inventory/stock', { params })),
  summary: () => unwrap(api.get('/inventory/summary')),
  alerts: (limit = 10) => unwrap(api.get('/inventory/alerts', { params: { limit } })),
  movements: (params) => unwrap(api.get('/inventory/movements', { params })),
  adjustments: (params) => unwrap(api.get('/inventory/adjustments', { params })),
  createAdjustment: (body) => unwrap(api.post('/inventory/adjustments', body)),
};
