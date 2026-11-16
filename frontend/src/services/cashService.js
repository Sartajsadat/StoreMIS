import api, { unwrap } from './apiClient';

export const CASH_TXN_TYPES = ['OPENING_BALANCE', 'SALE_RECEIPT', 'CUSTOMER_COLLECTION', 'PURCHASE_PAYMENT', 'SUPPLIER_PAYMENT', 'EXPENSE', 'SALE_REFUND', 'SUPPLIER_REFUND', 'OWNER_DEPOSIT', 'OWNER_WITHDRAWAL', 'TRANSFER_IN', 'TRANSFER_OUT', 'EXCHANGE_IN', 'EXCHANGE_OUT', 'CLOSING_DIFFERENCE', 'REVERSAL'];

export const cashService = {
  accounts: () => unwrap(api.get('/cash-accounts')),
  openingBalance: (id, amount) => unwrap(api.post(`/cash-accounts/${id}/opening-balance`, { amount })),
  transactions: (params) => unwrap(api.get('/cash-transactions', { params })),
};
