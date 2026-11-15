import { useEffect, useMemo, useState } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  Box, Collapse, Drawer, List, ListItemButton, ListItemIcon, ListItemText, Menu, MenuItem, Tooltip, Typography,
} from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import ExpandMore from '@mui/icons-material/ExpandMore';
import ChevronRight from '@mui/icons-material/ChevronRight';
import FiberManualRecord from '@mui/icons-material/FiberManualRecord';
import { NAV, filterNav } from './navConfig';
import { useAuth } from '../contexts/AuthContext';
import { useSettings } from '../contexts/SettingsContext';
import BrandMark from '../components/display/BrandMark';
import DirectionalIcon from '../components/display/DirectionalIcon';

const isActivePath = (pathname, path) => pathname === path;
const groupHasActive = (group, pathname) => group.children?.some((c) => isActivePath(pathname, c.path));

function SidebarHeader({ collapsed }) {
  const { t } = useTranslation();
  const { settings } = useSettings();
  const name = settings.businessName || t('app.name');
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, px: collapsed ? 2 : 2.5, height: 72, flexShrink: 0 }}>
      <BrandMark logoUrl={settings.logoUrl} alt={name} />
      {!collapsed && (
        <Box sx={{ minWidth: 0 }}>
          <Typography noWrap sx={{ color: '#fff', fontWeight: 700, fontSize: '1rem', lineHeight: 1.3 }}>
            {name}
          </Typography>
          <Typography noWrap sx={{ color: 'sidebar.groupText', fontSize: '0.75rem' }}>
            {t('app.tagline')}
          </Typography>
        </Box>
      )}
    </Box>
  );
}

function itemSx(theme, { active, nested, collapsed }) {
  const sb = theme.palette.sidebar;
  return {
    mx: 1.5,
    my: 0.25,
    minHeight: nested ? 38 : 44,
    px: collapsed ? 1.5 : nested ? 1.5 : 1.75,
    justifyContent: collapsed ? 'center' : 'flex-start',
    color: active ? sb.textActive : sb.text,
    bgcolor: active && !nested ? sb.activeBg : 'transparent',
    '&:hover': { bgcolor: active && !nested ? sb.activeBg : sb.hoverBg, color: sb.textActive },
    '& .MuiListItemIcon-root': { color: 'inherit', minWidth: collapsed ? 0 : 38 },
    ...(nested && active && { color: sb.textActive, bgcolor: alpha(theme.palette.primary.main, 0.28) }),
  };
}

function SidebarContent({ collapsed, onNavigate }) {
  const { t } = useTranslation();
  const theme = useTheme();
  const { pathname } = useLocation();
  const { canAccess } = useAuth();
  const items = useMemo(() => filterNav(NAV, canAccess), [canAccess]);

  const [open, setOpen] = useState(() => Object.fromEntries(items.filter((i) => groupHasActive(i, pathname)).map((i) => [i.key, true])));
  const [flyout, setFlyout] = useState({ anchor: null, group: null });

  // Expand the group that contains the current page (e.g. after navigating from a link elsewhere).
  useEffect(() => {
    const active = items.find((i) => groupHasActive(i, pathname));
    if (active) setOpen((o) => (o[active.key] ? o : { ...o, [active.key]: true }));
  }, [pathname, items]);

  const renderLeaf = (item, { nested = false } = {}) => {
    const active = isActivePath(pathname, item.path);
    const Icon = item.icon;
    const button = (
      <ListItemButton
        key={item.key}
        component={NavLink}
        to={item.path}
        end
        onClick={onNavigate}
        sx={itemSx(theme, { active, nested, collapsed })}
        aria-current={active ? 'page' : undefined}
      >
        {Icon ? (
          <ListItemIcon>
            <Icon fontSize="small" />
          </ListItemIcon>
        ) : (
          <ListItemIcon sx={{ minWidth: 24 }}>
            <FiberManualRecord sx={{ fontSize: active ? 9 : 6, opacity: active ? 1 : 0.6 }} />
          </ListItemIcon>
        )}
        {!collapsed && <ListItemText primary={t(item.labelKey)} slotProps={{ primary: { fontSize: nested ? '0.86rem' : '0.92rem', fontWeight: active ? 600 : 500, noWrap: true } }} />}
      </ListItemButton>
    );
    return collapsed && !nested ? (
      <Tooltip key={item.key} title={t(item.labelKey)} placement={theme.direction === 'rtl' ? 'left' : 'right'}>
        {button}
      </Tooltip>
    ) : (
      button
    );
  };

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', bgcolor: 'sidebar.background' }}>
      <SidebarHeader collapsed={collapsed} />
      <Box component="nav" aria-label={t('app.name')} sx={{ flex: 1, overflowY: 'auto', overflowX: 'hidden', pb: 2 }}>
        <List disablePadding>
          {items.map((item) => {
            if (!item.children) return renderLeaf(item);
            const Icon = item.icon;
            const active = groupHasActive(item, pathname);
            const expanded = Boolean(open[item.key]);
            if (collapsed) {
              return (
                <Tooltip key={item.key} title={t(item.labelKey)} placement={theme.direction === 'rtl' ? 'left' : 'right'}>
                  <ListItemButton
                    sx={{ ...itemSx(theme, { active, collapsed: true }), ...(active && { bgcolor: theme.palette.sidebar.activeBg, color: '#fff' }) }}
                    onClick={(e) => setFlyout({ anchor: e.currentTarget, group: item })}
                    aria-haspopup="menu"
                  >
                    <ListItemIcon>
                      <Icon fontSize="small" />
                    </ListItemIcon>
                  </ListItemButton>
                </Tooltip>
              );
            }
            return (
              <Box key={item.key}>
                <ListItemButton
                  onClick={() => setOpen((o) => ({ ...o, [item.key]: !o[item.key] }))}
                  sx={{ ...itemSx(theme, { active: false, collapsed: false }), ...(active && { color: '#fff' }) }}
                  aria-expanded={expanded}
                >
                  <ListItemIcon>
                    <Icon fontSize="small" />
                  </ListItemIcon>
                  <ListItemText primary={t(item.labelKey)} slotProps={{ primary: { fontSize: '0.92rem', fontWeight: active ? 600 : 500, noWrap: true } }} />
                  {expanded ? <ExpandMore fontSize="small" /> : <DirectionalIcon icon={ChevronRight} fontSize="small" />}
                </ListItemButton>
                <Collapse in={expanded} timeout="auto" unmountOnExit>
                  <List disablePadding sx={{ pl: 2 }}>
                    {item.children.map((c) => renderLeaf(c, { nested: true }))}
                  </List>
                </Collapse>
              </Box>
            );
          })}
        </List>
      </Box>
      <Menu
        anchorEl={flyout.anchor}
        open={Boolean(flyout.anchor)}
        onClose={() => setFlyout({ anchor: null, group: null })}
        anchorOrigin={{ vertical: 'top', horizontal: theme.direction === 'rtl' ? 'left' : 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: theme.direction === 'rtl' ? 'right' : 'left' }}
      >
        {flyout.group && (
          <Typography variant="overline" sx={{ px: 2, color: 'text.secondary' }}>
            {t(flyout.group.labelKey)}
          </Typography>
        )}
        {flyout.group?.children.map((c) => (
          <MenuItem
            key={c.key}
            component={NavLink}
            to={c.path}
            end
            selected={isActivePath(pathname, c.path)}
            onClick={() => {
              setFlyout({ anchor: null, group: null });
              onNavigate?.();
            }}
          >
            {t(c.labelKey)}
          </MenuItem>
        ))}
      </Menu>
    </Box>
  );
}

/**
 * Persistent sidebar on desktop (full or icon-only), temporary drawer on mobile.
 * MUI swaps the "left" anchor to the right edge automatically when the theme is RTL.
 */
export default function Sidebar({ collapsed, mobileOpen, onMobileClose, isMobile }) {
  const theme = useTheme();
  const { sidebarWidth, sidebarCollapsedWidth } = theme.layout;
  const width = collapsed ? sidebarCollapsedWidth : sidebarWidth;

  if (isMobile) {
    return (
      <Drawer
        variant="temporary"
        anchor="left"
        open={mobileOpen}
        onClose={onMobileClose}
        ModalProps={{ keepMounted: true }}
        slotProps={{ paper: { sx: { width: sidebarWidth, border: 'none' } } }}
      >
        <SidebarContent collapsed={false} onNavigate={onMobileClose} />
      </Drawer>
    );
  }
  return (
    <Drawer
      variant="permanent"
      anchor="left"
      sx={{
        width,
        flexShrink: 0,
        transition: theme.transitions.create('width', { duration: theme.transitions.duration.shorter }),
        '& .MuiDrawer-paper': {
          width,
          border: 'none',
          overflowX: 'hidden',
          transition: theme.transitions.create('width', { duration: theme.transitions.duration.shorter }),
        },
      }}
    >
      <SidebarContent collapsed={collapsed} />
    </Drawer>
  );
}
