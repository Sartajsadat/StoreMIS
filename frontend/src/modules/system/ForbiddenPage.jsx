import { Button, Card } from '@mui/material';
import LockOutlined from '@mui/icons-material/LockOutlined';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import EmptyState from '../../components/feedback/EmptyState';

export default function ForbiddenPage() {
  const { t } = useTranslation();
  return (
    <Card>
      <EmptyState
        icon={LockOutlined}
        title={t('errors.forbiddenTitle')}
        description={t('errors.forbiddenText')}
        action={
          <Button component={Link} to="/" variant="contained">
            {t('common.goHome')}
          </Button>
        }
      />
    </Card>
  );
}
