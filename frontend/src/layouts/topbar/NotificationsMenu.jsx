import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Badge, Box, IconButton, List, ListItemButton, ListItemIcon, ListItemText, Popover, Tooltip, Typography } from '@mui/material';
import WarningAmberOutlined from '@mui/icons-material/WarningAmberOutlined';
import ErrorOutline from '@mui/icons-material/ErrorOutline';
import NotificationsNoneOutlined from '@mui/icons-material/NotificationsNoneOutlined';
import { useNavigate } from 'react-router-dom';
import EmptyState from '../../components/feedback/EmptyState';

/**
 * Bell menu. `items` are alerts already translated by their producer:
 * [{ id, title, subtitle, to, severity }]. Low-stock alerts feed this from Phase 4.
 */
export default function NotificationsMenu({ items = [] }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [anchor, setAnchor] = useState(null);

  return (
    <>
      <Tooltip title={t('topbar.notifications')}>
        <IconButton color="inherit" onClick={(e) => setAnchor(e.currentTarget)} aria-label={t('topbar.notifications')}>
          <Badge color="error" badgeContent={items.length} max={99}>
            <NotificationsNoneOutlined />
          </Badge>
        </IconButton>
      </Tooltip>
      <Popover
        open={Boolean(anchor)}
        anchorEl={anchor}
        onClose={() => setAnchor(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
        transformOrigin={{ vertical: 'top', horizontal: 'center' }}
        slotProps={{ paper: { sx: { width: 340, maxWidth: 'calc(100vw - 32px)', borderRadius: 3 } } }}
      >
        <Box sx={{ px: 2, py: 1.5, borderBottom: 1, borderColor: 'divider', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Typography variant="subtitle1">{items.length ? t('inventory.alertsTitle') : t('topbar.notifications')}</Typography>
          {items.length > 0 && (
            <Typography
              variant="body2"
              color="primary"
              sx={{ cursor: 'pointer', fontWeight: 600 }}
              onClick={() => {
                setAnchor(null);
                navigate('/inventory/low-stock');
              }}
            >
              {t('inventory.viewAll')}
            </Typography>
          )}
        </Box>
        {items.length === 0 ? (
          <EmptyState icon={NotificationsNoneOutlined} title={t('topbar.noNotifications')} description={t('topbar.noNotificationsHint')} dense />
        ) : (
          <List dense sx={{ maxHeight: 380, overflowY: 'auto' }}>
            {items.map((n) => (
              <ListItemButton
                key={n.id}
                onClick={() => {
                  setAnchor(null);
                  if (n.to) navigate(n.to);
                }}
              >
                <ListItemIcon sx={{ minWidth: 36 }}>
                  {n.severity === 'error' ? <ErrorOutline color="error" fontSize="small" /> : <WarningAmberOutlined color="warning" fontSize="small" />}
                </ListItemIcon>
                <ListItemText primary={n.title} secondary={n.subtitle} />
              </ListItemButton>
            ))}
          </List>
        )}
      </Popover>
    </>
  );
}
