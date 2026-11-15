import { Component } from 'react';
import { withTranslation } from 'react-i18next';
import { Button } from '@mui/material';
import ErrorOutline from '@mui/icons-material/ErrorOutline';
import EmptyState from './EmptyState';

class ErrorBoundaryBase extends Component {
  constructor(props) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error, info) {
    console.error('UI error', error, info?.componentStack);
  }

  render() {
    const { t, children } = this.props;
    if (!this.state.error) return children;
    return (
      <EmptyState
        icon={ErrorOutline}
        title={t('errors.boundaryTitle')}
        description={t('errors.boundaryText')}
        action={
          <Button variant="contained" onClick={() => this.setState({ error: null })}>
            {t('common.retry')}
          </Button>
        }
      />
    );
  }
}

const ErrorBoundary = withTranslation()(ErrorBoundaryBase);
export default ErrorBoundary;
