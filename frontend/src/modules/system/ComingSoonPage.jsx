import { Card } from '@mui/material';
import ConstructionOutlined from '@mui/icons-material/ConstructionOutlined';
import { useTranslation } from 'react-i18next';
import EmptyState from '../../components/feedback/EmptyState';

/** Shown for menu entries whose screens are delivered in a later development phase. */
export default function ComingSoonPage({ item }) {
  const { t } = useTranslation();
  return (
    <Card>
      <EmptyState
        icon={ConstructionOutlined}
        title={t('comingSoon.title', { module: t(item.labelKey) })}
        description={t('comingSoon.description', { phase: item.phase, phaseName: t(`comingSoon.phases.${item.phase}`) })}
      />
    </Card>
  );
}
