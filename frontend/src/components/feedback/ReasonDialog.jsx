import { useEffect, useState } from 'react';
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, TextField } from '@mui/material';
import { useTranslation } from 'react-i18next';

/** Asks for a mandatory reason (cancellations, reversals, voids). The reason is stored in the audit log. */
export default function ReasonDialog({ open, title, message, confirmLabel, color = 'error', loading, minLength = 3, onConfirm, onClose }) {
  const { t } = useTranslation();
  const [reason, setReason] = useState('');
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    if (open) {
      setReason('');
      setTouched(false);
    }
  }, [open]);

  const tooShort = reason.trim().length < minLength;
  const submit = () => {
    setTouched(true);
    if (!tooShort) onConfirm(reason.trim());
  };

  return (
    <Dialog open={open} onClose={loading ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{title ?? t('dialogs.reasonTitle')}</DialogTitle>
      <DialogContent>
        {message && <DialogContentText sx={{ mb: 2 }}>{message}</DialogContentText>}
        <TextField
          autoFocus
          multiline
          minRows={3}
          label={t('dialogs.reasonLabel')}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          onBlur={() => setTouched(true)}
          error={touched && tooShort}
          helperText={touched && tooShort ? t('validation.minLength', { min: minLength }) : t('dialogs.reasonHint')}
          slotProps={{ htmlInput: { maxLength: 300 } }}
        />
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={loading} color="inherit">
          {t('common.cancel')}
        </Button>
        <Button onClick={submit} variant="contained" color={color} loading={loading}>
          {confirmLabel ?? t('common.confirm')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
