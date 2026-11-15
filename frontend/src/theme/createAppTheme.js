import { createTheme } from '@mui/material/styles';
import { brand, surfaces, chartSeries, fonts } from './palette';
import { buildComponents } from './components';

/**
 * Builds the MUI theme for a colour mode ('light' | 'dark') and a text direction ('ltr' | 'rtl').
 * Locale objects (MUI core / DataGrid / pickers) are merged in by the caller.
 */
export function createAppTheme({ mode = 'light', direction = 'ltr', locales = [] } = {}) {
  const s = surfaces[mode];
  const base = createTheme(
    {
      direction,
      palette: {
        mode,
        primary: { main: mode === 'light' ? brand.blue : '#3B82F6', contrastText: '#fff' },
        secondary: { main: brand.navy },
        success: { main: brand.success },
        warning: { main: brand.warning },
        error: { main: brand.danger },
        info: { main: '#0284C7' },
        background: { default: s.background, paper: s.paper },
        divider: s.border,
        text:
          mode === 'light'
            ? { primary: '#101828', secondary: '#475467', disabled: '#98A2B3' }
            : { primary: '#F3F4F6', secondary: '#9CA3AF', disabled: '#6B7280' },
        sidebar: {
          background: s.sidebar,
          text: '#C9D3E3',
          textActive: '#FFFFFF',
          activeBg: 'rgba(37, 99, 235, 0.9)',
          hoverBg: 'rgba(255, 255, 255, 0.08)',
          groupText: '#8A99B4',
        },
        chart: { series: chartSeries[mode], grid: mode === 'light' ? '#E5E7EB' : '#374151' },
      },
      shape: { borderRadius: 8 },
      typography: {
        fontFamily: direction === 'rtl' ? fonts.rtl : fonts.ltr,
        h4: { fontWeight: 700, fontSize: '1.6rem' },
        h5: { fontWeight: 700, fontSize: '1.3rem' },
        h6: { fontWeight: 600, fontSize: '1.05rem' },
        subtitle1: { fontWeight: 600 },
        subtitle2: { fontWeight: 600 },
        button: { fontWeight: 600 },
      },
      layout: { sidebarWidth: 264, sidebarCollapsedWidth: 76, topbarHeight: 64 },
    },
    ...locales,
  );
  return createTheme(base, { components: buildComponents(base) });
}
