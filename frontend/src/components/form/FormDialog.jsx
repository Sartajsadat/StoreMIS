import { Button, Dialog, DialogActions, DialogContent, DialogTitle, useMediaQuery } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useTranslation } from 'react-i18next';

/** Dialog that wraps a form. Pass handleSubmit(onValid) from react-hook-form as onSubmit. Full screen on phones. */
export default function FormDialog({ open, title, onClose, onSubmit, submitLabel, submitting, maxWidth = 'sm', children }) {
  const { t } = useTranslation();
  const theme = useTheme();
  const fullScreen = useMediaQuery(theme.breakpoints.down('sm'));
  return (
    <Dialog open={open} onClose={submitting ? undefined : onClose} maxWidth={maxWidth} fullWidth fullScreen={fullScreen}>
      <form onSubmit={onSubmit} noValidate>
        <DialogTitle>{title}</DialogTitle>
        <DialogContent dividers>{children}</DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={onClose} disabled={submitting} color="inherit">
            {t('common.cancel')}
          </Button>
          <Button type="submit" variant="contained" loading={submitting}>
            {submitLabel ?? t('common.save')}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
