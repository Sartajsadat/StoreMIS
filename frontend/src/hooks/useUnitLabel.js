import { useCallback } from 'react';
import { useTranslation } from 'react-i18next';

/** System units (code set) are translated; units created by the admin show their own name. */
export function useUnitLabel() {
  const { t } = useTranslation();
  return useCallback((unit) => {
    if (!unit) return '';
    const code = unit.code ?? unit.unitCode;
    const name = unit.name ?? unit.unitName;
    return code ? t(`unitNames.${code}`, name) : name;
  }, [t]);
}
