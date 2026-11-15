import { Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';
import Ltr from '../../components/display/Ltr';
import { formatDateTime } from '../../utils/format';

const show = (v) => (v === null || v === undefined || v === '' ? '—' : Array.isArray(v) ? v.join(', ') : String(v));

/** Shows what changed. Field names are technical identifiers; values are the stored data. */
export default function AuditDetailsDialog({ log, onClose }) {
  const { t } = useTranslation();
  const details = log?.details ?? {};
  const changes = Object.entries(details).filter(([, v]) => v && typeof v === 'object' && !Array.isArray(v) && ('from' in v || 'to' in v));
  const plain = Object.entries(details).filter(([k]) => !changes.some(([c]) => c === k));
  return (
    <Dialog open={Boolean(log)} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>{t('audit.details')}</DialogTitle>
      <DialogContent dividers>
        {log && (
          <Stack spacing={0.5} sx={{ mb: 2 }}>
            <Typography variant="body2">
              {t(`audit.modules.${log.module}`)} · {t(`audit.actions.${log.action}`)}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              <Ltr>{formatDateTime(log.occurredAt)}</Ltr> · {log.username ?? t('audit.system')} {log.reference && <Ltr>· {log.reference}</Ltr>}
            </Typography>
          </Stack>
        )}
        {changes.length === 0 && plain.length === 0 ? (
          <Typography color="text.secondary">{t('audit.noDetails')}</Typography>
        ) : (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>{t('audit.field')}</TableCell>
                <TableCell>{t('audit.from')}</TableCell>
                <TableCell>{t('audit.to')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {changes.map(([field, v]) => (
                <TableRow key={field}>
                  <TableCell>
                    <Ltr>{field}</Ltr>
                  </TableCell>
                  <TableCell>{show(v.from)}</TableCell>
                  <TableCell>{show(v.to)}</TableCell>
                </TableRow>
              ))}
              {plain.map(([field, v]) => (
                <TableRow key={field}>
                  <TableCell>
                    <Ltr>{field}</Ltr>
                  </TableCell>
                  <TableCell colSpan={2}>{show(v)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('common.close')}</Button>
      </DialogActions>
    </Dialog>
  );
}
