import { Box } from '@mui/material';
import { Outlet } from 'react-router-dom';
import LanguageMenu from './topbar/LanguageMenu';
import ThemeToggle from './topbar/ThemeToggle';

/** Full-screen layout for sign-in screens, with language and theme controls in the corner. */
export default function AuthLayout() {
  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default', position: 'relative' }}>
      <Box sx={{ position: 'absolute', top: 12, right: 12, zIndex: 2, display: 'flex', gap: 0.5, color: 'text.secondary', bgcolor: 'background.paper', borderRadius: 2, border: 1, borderColor: 'divider' }}>
        <LanguageMenu />
        <ThemeToggle />
      </Box>
      <Outlet />
    </Box>
  );
}
