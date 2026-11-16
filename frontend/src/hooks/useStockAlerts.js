import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { inventoryService } from '../services/inventoryService';
import { useAuth } from '../contexts/AuthContext';
import { formatNumber } from '../utils/format';

/** Low / out-of-stock products for the notification bell, refreshed every two minutes. */
export function useStockAlerts() {
  const { t } = useTranslation();
  const { isAuthenticated } = useAuth();
  const query = useQuery({
    queryKey: ['inventory', 'alerts'],
    queryFn: () => inventoryService.alerts(10),
    enabled: isAuthenticated,
    refetchInterval: 2 * 60 * 1000,
    staleTime: 60 * 1000,
  });
  return (query.data?.items ?? []).map((i) => ({
    id: i.productId,
    title: i.name,
    subtitle:
      i.status === 'OUT_OF_STOCK'
        ? t('inventory.alertOut')
        : t('inventory.alertLine', { qty: formatNumber(i.quantity), min: formatNumber(i.minStockLevel) }),
    severity: i.status === 'OUT_OF_STOCK' ? 'error' : 'warning',
    to: '/inventory/low-stock',
  }));
}
