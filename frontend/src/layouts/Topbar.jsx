import { useTranslation } from 'react-i18next';
import { Link as RouterLink } from 'react-router-dom';
import { AppBar, Box, Breadcrumbs, IconButton, Link, Toolbar, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import MenuOutlined from '@mui/icons-material/MenuOutlined';
import MenuOpenOutlined from '@mui/icons-material/MenuOpenOutlined';
import NavigateNext from '@mui/icons-material/NavigateNext';
import LanguageMenu from './topbar/LanguageMenu';
import ThemeToggle from './topbar/ThemeToggle';
import NotificationsMenu from './topbar/NotificationsMenu';
import UserMenu from './topbar/UserMenu';
import DirectionalIcon from '../components/display/DirectionalIcon';

export default function Topbar({ title, crumbs = [], onToggleSidebar, sidebarCollapsed, isMobile }) {
  const { t } = useTranslation();
  const theme = useTheme();
  const toggleLabel = isMobile ? t('topbar.toggleSidebar') : sidebarCollapsed ? t('topbar.expandSidebar') : t('topbar.collapseSidebar');

  return (
    <AppBar
      position="sticky"
      color="inherit"
      elevation={0}
      sx={{ bgcolor: 'background.paper', borderBottom: 1, borderColor: 'divider', '@media print': { display: 'none' } }}
    >
      <Toolbar sx={{ minHeight: `${theme.layout.topbarHeight}px !important`, gap: 1, px: { xs: 1.5, sm: 2.5 } }}>
        <Tooltip title={toggleLabel}>
          <IconButton edge="start" onClick={onToggleSidebar} aria-label={toggleLabel}>
            {isMobile || sidebarCollapsed ? <MenuOutlined /> : <DirectionalIcon icon={MenuOpenOutlined} />}
          </IconButton>
        </Tooltip>

        <Box sx={{ minWidth: 0, flex: 1 }}>
          <Typography component="h1" variant="h6" noWrap sx={{ lineHeight: 1.25 }}>
            {title}
          </Typography>
          {!isMobile && crumbs.length > 0 && (
            <Breadcrumbs separator={<DirectionalIcon icon={NavigateNext} sx={{ fontSize: 16 }} />} sx={{ fontSize: '0.78rem', '& .MuiBreadcrumbs-separator': { mx: 0.5 } }}>
              {crumbs.map((c, i) =>
                c.to && i < crumbs.length - 1 ? (
                  <Link key={c.label} component={RouterLink} to={c.to} underline="hover" color="text.secondary">
                    {c.label}
                  </Link>
                ) : (
                  <Typography key={c.label} sx={{ fontSize: 'inherit' }} color={i === crumbs.length - 1 ? 'text.primary' : 'text.secondary'}>
                    {c.label}
                  </Typography>
                ),
              )}
            </Breadcrumbs>
          )}
        </Box>

        <Box sx={{ display: 'flex', alignItems: 'center', gap: { xs: 0, sm: 0.5 }, color: 'text.secondary' }}>
          <LanguageMenu />
          <ThemeToggle />
          <NotificationsMenu />
          <Box sx={{ ml: 0.5 }}>
            <UserMenu compact={isMobile} />
          </Box>
        </Box>
      </Toolbar>
    </AppBar>
  );
}
