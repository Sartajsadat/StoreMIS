import { useEffect, useMemo, useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Box, useMediaQuery } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import Sidebar from './Sidebar';
import Topbar from './Topbar';
import { findNavTrail } from './navConfig';
import { EXTRA_PAGES } from '../routes/extraPages';
import { usePreferences } from '../contexts/PreferencesContext';
import { useSettings } from '../contexts/SettingsContext';
import ErrorBoundary from '../components/feedback/ErrorBoundary';

export default function AppLayout() {
  const theme = useTheme();
  const { t } = useTranslation();
  const { pathname } = useLocation();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const { sidebarCollapsed, setSidebarCollapsed } = usePreferences();
  const { settings } = useSettings();
  const [mobileOpen, setMobileOpen] = useState(false);

  const { title, crumbs } = useMemo(() => {
    const trail = findNavTrail(pathname, EXTRA_PAGES);
    if (!trail) return { title: '', crumbs: [] };
    const list = [{ label: t('nav.home'), to: '/' }];
    if (trail.group) list.push({ label: t(trail.group.labelKey) });
    if (trail.item.path !== '/') list.push({ label: t(trail.item.labelKey) });
    return { title: t(trail.item.labelKey), crumbs: list };
  }, [pathname, t]);

  useEffect(() => {
    const appName = settings.businessName || t('app.name');
    document.title = title ? `${title} · ${appName}` : appName;
  }, [title, settings.businessName, t]);

  const toggle = () => (isMobile ? setMobileOpen((o) => !o) : setSidebarCollapsed((c) => !c));

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'background.default' }}>
      <Sidebar collapsed={!isMobile && sidebarCollapsed} isMobile={isMobile} mobileOpen={mobileOpen} onMobileClose={() => setMobileOpen(false)} />
      <Box sx={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
        <Topbar title={title} crumbs={crumbs} onToggleSidebar={toggle} sidebarCollapsed={sidebarCollapsed} isMobile={isMobile} />
        <Box component="main" sx={{ flex: 1, p: { xs: 2, sm: 3 }, maxWidth: 1600, width: '100%', mx: 'auto' }}>
          <ErrorBoundary key={pathname}>
            <Outlet />
          </ErrorBoundary>
        </Box>
      </Box>
    </Box>
  );
}
