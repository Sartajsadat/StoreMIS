import { Button, Card } from '@mui/material';
import SearchOffOutlined from '@mui/icons-material/SearchOffOutlined';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import EmptyState from '../../components/feedback/EmptyState';

export default function NotFoundPage() {
  const { t } = useTranslation();
  return (
    <Card>
      <EmptyState
        icon={SearchOffOutlined}
        title={t('errors.notFoundTitle')}
        description={t('errors.notFoundText')}
        action={
          <Button component={Link} to="/" variant="contained">
            {t('common.goHome')}
          </Button>
        }
      />
    </Card>
  );
}
