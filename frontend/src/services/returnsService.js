import api, { unwrap } from './apiClient';

/** kind: 'sales' | 'purchase' */
const base = (kind) => (kind === 'sales' ? '/sales-returns' : '/purchase-returns');
const doc = (kind) => (kind === 'sales' ? '/sales' : '/purchases');

export const returnsService = {
  returnable: (kind, documentId) => unwrap(api.get(`${doc(kind)}/${documentId}/returnable-items`)),
  lookup: (kind, documentNo) => unwrap(api.get(`${base(kind)}/lookup`, { params: { documentNo } })),
  create: (kind, body) => unwrap(api.post(base(kind), body)),
  list: (kind, params) => unwrap(api.get(base(kind), { params })),
  get: (kind, id) => unwrap(api.get(`${base(kind)}/${id}`)),
  forDocument: (kind, documentId) => unwrap(api.get(`${doc(kind)}/${documentId}/returns`)),
};
