import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { IconButton, ListItemIcon, ListItemText, Menu, MenuItem, Tooltip } from '@mui/material';
import LanguageOutlined from '@mui/icons-material/LanguageOutlined';
import Check from '@mui/icons-material/Check';
import { LANGUAGES } from '../../i18n';
import { usePreferences } from '../../contexts/PreferencesContext';

/** Globe icon dropdown: English / دری / پښتو. Each label is shown in its own script. */
export default function LanguageMenu({ color = 'inherit' }) {
  const { t } = useTranslation();
  const { language, setLanguage } = usePreferences();
  const [anchor, setAnchor] = useState(null);

  return (
    <>
      <Tooltip title={t('topbar.language')}>
        <IconButton color={color} onClick={(e) => setAnchor(e.currentTarget)} aria-label={t('topbar.language')} aria-haspopup="menu">
          <LanguageOutlined />
        </IconButton>
      </Tooltip>
      <Menu anchorEl={anchor} open={Boolean(anchor)} onClose={() => setAnchor(null)}>
        {LANGUAGES.map((lang) => {
          const selected = lang.code === language;
          return (
            <MenuItem
              key={lang.code}
              selected={selected}
              onClick={() => {
                setLanguage(lang.code);
                setAnchor(null);
              }}
              lang={lang.code}
              dir={lang.dir}
              sx={{ minWidth: 200 }}
            >
              <ListItemIcon>{selected ? <Check fontSize="small" color="primary" /> : null}</ListItemIcon>
              <ListItemText primary={t(lang.labelKey)} slotProps={{ primary: { fontWeight: selected ? 700 : 400 } }} />
            </MenuItem>
          );
        })}
      </Menu>
    </>
  );
}
