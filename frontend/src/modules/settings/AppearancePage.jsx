import { useTranslation } from 'react-i18next';
import { Box, Card, FormControlLabel, Grid, Radio, RadioGroup, Stack, Switch, Typography } from '@mui/material';
import { alpha } from '@mui/material/styles';
import LightModeOutlined from '@mui/icons-material/LightModeOutlined';
import DarkModeOutlined from '@mui/icons-material/DarkModeOutlined';
import PageHeader from '../../components/display/PageHeader';
import SectionCard from '../../components/display/SectionCard';
import { LANGUAGES } from '../../i18n';
import { usePreferences } from '../../contexts/PreferencesContext';
import { surfaces } from '../../theme/palette';

function ThemeOption({ mode, selected, onSelect, label, icon: Icon }) {
  const s = surfaces[mode];
  return (
    <Card
      component="button"
      type="button"
      onClick={onSelect}
      aria-pressed={selected}
      sx={(theme) => ({
        p: 1.5,
        width: '100%',
        cursor: 'pointer',
        textAlign: 'start',
        font: 'inherit',
        color: 'inherit',
        bgcolor: 'background.paper',
        borderWidth: 2,
        borderColor: selected ? 'primary.main' : 'divider',
        boxShadow: selected ? `0 0 0 3px ${alpha(theme.palette.primary.main, 0.15)}` : 'none',
      })}
    >
      <Box sx={{ display: 'flex', height: 84, borderRadius: 1.5, overflow: 'hidden', border: `1px solid ${s.border}` }}>
        <Box sx={{ width: '28%', bgcolor: s.sidebar }} />
        <Box sx={{ flex: 1, bgcolor: s.background, p: 1, display: 'flex', flexDirection: 'column', gap: 0.75 }}>
          <Box sx={{ height: 10, borderRadius: 1, bgcolor: s.paper }} />
          <Box sx={{ display: 'flex', gap: 0.75, flex: 1 }}>
            <Box sx={{ flex: 1, borderRadius: 1, bgcolor: s.paper }} />
            <Box sx={{ flex: 1, borderRadius: 1, bgcolor: s.paper }} />
          </Box>
        </Box>
      </Box>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mt: 1.25 }}>
        <Icon fontSize="small" color={selected ? 'primary' : 'action'} />
        <Typography variant="body2" sx={{ fontWeight: selected ? 700 : 500 }}>
          {label}
        </Typography>
      </Stack>
    </Card>
  );
}

export default function AppearancePage() {
  const { t } = useTranslation();
  const { language, setLanguage, themeMode, setThemeMode, sidebarCollapsed, setSidebarCollapsed, direction } = usePreferences();

  return (
    <>
      <PageHeader title={t('appearance.title')} subtitle={t('appearance.subtitle')} />
      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 6 }}>
          <SectionCard title={t('appearance.language')} subheader={t('appearance.languageHint')}>
            <RadioGroup value={language} onChange={(e) => setLanguage(e.target.value)}>
              {LANGUAGES.map((l) => (
                <FormControlLabel
                  key={l.code}
                  value={l.code}
                  control={<Radio />}
                  label={
                    <Stack direction="row" spacing={1} sx={{ alignItems: 'baseline' }}>
                      <span lang={l.code}>{t(l.labelKey)}</span>
                      <Typography variant="caption" color="text.secondary">
                        {t(`appearance.${l.dir}`)}
                      </Typography>
                    </Stack>
                  }
                />
              ))}
            </RadioGroup>
            <Typography variant="caption" color="text.secondary">
              {t('appearance.direction')}: {t(`appearance.${direction}`)}
            </Typography>
          </SectionCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <SectionCard title={t('appearance.theme')}>
            <Grid container spacing={2}>
              <Grid size={6}>
                <ThemeOption mode="light" label={t('appearance.light')} icon={LightModeOutlined} selected={themeMode === 'light'} onSelect={() => setThemeMode('light')} />
              </Grid>
              <Grid size={6}>
                <ThemeOption mode="dark" label={t('appearance.dark')} icon={DarkModeOutlined} selected={themeMode === 'dark'} onSelect={() => setThemeMode('dark')} />
              </Grid>
            </Grid>
            <Box sx={{ mt: 3 }}>
              <Typography variant="subtitle2" sx={{ mb: 1 }}>
                {t('appearance.sidebar')}
              </Typography>
              <FormControlLabel control={<Switch checked={sidebarCollapsed} onChange={(e) => setSidebarCollapsed(e.target.checked)} />} label={t('appearance.sidebarCompact')} />
            </Box>
          </SectionCard>
        </Grid>
      </Grid>
    </>
  );
}
