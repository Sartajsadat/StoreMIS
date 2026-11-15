import { useTranslation } from 'react-i18next';
import { IconButton, Tooltip } from '@mui/material';
import DarkModeOutlined from '@mui/icons-material/DarkModeOutlined';
import LightModeOutlined from '@mui/icons-material/LightModeOutlined';
import { usePreferences } from '../../contexts/PreferencesContext';

export default function ThemeToggle({ color = 'inherit' }) {
  const { t } = useTranslation();
  const { themeMode, toggleTheme } = usePreferences();
  const label = themeMode === 'light' ? t('topbar.switchToDark') : t('topbar.switchToLight');
  return (
    <Tooltip title={label}>
      <IconButton color={color} onClick={toggleTheme} aria-label={label}>
        {themeMode === 'light' ? <DarkModeOutlined /> : <LightModeOutlined />}
      </IconButton>
    </Tooltip>
  );
}
