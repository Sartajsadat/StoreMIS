import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Avatar, Box, ButtonBase, Divider, ListItemIcon, Menu, MenuItem, Typography } from '@mui/material';
import LogoutOutlined from '@mui/icons-material/LogoutOutlined';
import KeyOutlined from '@mui/icons-material/KeyOutlined';
import PaletteOutlined from '@mui/icons-material/PaletteOutlined';
import { useAuth } from '../../contexts/AuthContext';

const initials = (name = '') =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0])
    .join('')
    .toUpperCase();

export default function UserMenu({ compact }) {
  const { t } = useTranslation();
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [anchor, setAnchor] = useState(null);
  const name = user?.fullName || user?.username || '';

  const go = (path) => {
    setAnchor(null);
    navigate(path);
  };

  return (
    <>
      <ButtonBase
        onClick={(e) => setAnchor(e.currentTarget)}
        sx={{ borderRadius: 2, px: 0.75, py: 0.5, gap: 1.25, '&:hover': { bgcolor: 'action.hover' } }}
        aria-label={t('topbar.account')}
        aria-haspopup="menu"
      >
        <Avatar sx={{ width: 36, height: 36, bgcolor: (th) => (th.palette.mode === 'light' ? 'secondary.main' : 'primary.main'), color: '#fff', fontSize: '0.9rem', fontWeight: 700 }}>{initials(name)}</Avatar>
        {!compact && (
          <Box sx={{ textAlign: 'start', lineHeight: 1.2 }}>
            <Typography variant="body2" sx={{ fontWeight: 600, maxWidth: 160 }} noWrap>
              {name}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {user?.role ? t(`roles.${user.role}`) : ''}
            </Typography>
          </Box>
        )}
      </ButtonBase>
      <Menu anchorEl={anchor} open={Boolean(anchor)} onClose={() => setAnchor(null)} slotProps={{ paper: { sx: { minWidth: 220 } } }}>
        <Box sx={{ px: 2, py: 1 }}>
          <Typography variant="subtitle2" noWrap>
            {name}
          </Typography>
          <Typography variant="caption" color="text.secondary" dir="ltr" component="div" sx={{ textAlign: 'start' }}>
            {user?.username}
          </Typography>
        </Box>
        <Divider />
        <MenuItem onClick={() => go('/settings/appearance')}>
          <ListItemIcon>
            <PaletteOutlined fontSize="small" />
          </ListItemIcon>
          {t('nav.appearance')}
        </MenuItem>
        <MenuItem onClick={() => go('/change-password')}>
          <ListItemIcon>
            <KeyOutlined fontSize="small" />
          </ListItemIcon>
          {t('nav.changePassword')}
        </MenuItem>
        <Divider />
        <MenuItem
          onClick={async () => {
            setAnchor(null);
            await logout();
            navigate('/login', { replace: true, state: { loggedOut: true } });
          }}
          sx={{ color: 'error.main' }}
        >
          <ListItemIcon sx={{ color: 'inherit' }}>
            <LogoutOutlined fontSize="small" />
          </ListItemIcon>
          {t('topbar.logout')}
        </MenuItem>
      </Menu>
    </>
  );
}
