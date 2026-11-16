import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Box, Button, Drawer, IconButton, List, ListItem, ListItemText, Stack, Tooltip, Typography } from '@mui/material';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import { salesService } from '../../services/salesService';
import MoneyText from '../../components/display/MoneyText';
import UserText from '../../components/display/UserText';
import Ltr from '../../components/display/Ltr';
import EmptyState from '../../components/feedback/EmptyState';
import { formatDateTime } from '../../utils/format';

export default function HeldSalesDrawer({ open, onClose, onResume, onDeleted }) {
  const { t } = useTranslation();
  const drafts = useQuery({ queryKey: ['sales', 'drafts'], queryFn: salesService.drafts, enabled: open });
  return (
    <Drawer anchor="right" open={open} onClose={onClose} slotProps={{ paper: { sx: { width: 380, maxWidth: '100vw' } } }}>
      <Box sx={{ p: 2, borderBottom: 1, borderColor: 'divider' }}>
        <Typography variant="h6">{t('pos.held')}</Typography>
      </Box>
      {(drafts.data ?? []).length === 0 ? (
        <EmptyState title={t('pos.noHeld')} dense />
      ) : (
        <List>
          {drafts.data.map((d) => (
            <ListItem
              key={d.id}
              divider
              secondaryAction={
                <Stack direction="row" spacing={0.5}>
                  <Button size="small" variant="contained" onClick={() => onResume(d)}>
                    {t('pos.resume')}
                  </Button>
                  <Tooltip title={t('common.delete')}>
                    <IconButton
                      size="small"
                      onClick={async () => {
                        await salesService.deleteDraft(d.id);
                        drafts.refetch();
                        onDeleted?.();
                      }}
                      aria-label={t('common.delete')}
                    >
                      <DeleteOutline fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </Stack>
              }
            >
              <ListItemText
                primary={d.walkIn ? t('customers.walkIn') : <UserText>{d.customerName}</UserText>}
                secondary={
                  <>
                    <MoneyText amount={d.grandTotal} currency={d.currency} /> · {t('pos.items', { count: d.items.length })} · <Ltr>{formatDateTime(d.saleDate)}</Ltr>
                  </>
                }
                sx={{ mr: 14 }}
              />
            </ListItem>
          ))}
        </List>
      )}
    </Drawer>
  );
}
