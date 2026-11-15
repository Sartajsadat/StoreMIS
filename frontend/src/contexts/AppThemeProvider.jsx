import { useMemo } from 'react';
import { CacheProvider } from '@emotion/react';
import { CssBaseline, ThemeProvider } from '@mui/material';
import { enUS as coreEn, faIR as coreFa } from '@mui/material/locale';
import { enUS as gridEn, faIR as gridFa } from '@mui/x-data-grid/locales';
import { enUS as pickersEn, faIR as pickersFa } from '@mui/x-date-pickers/locales';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import { useTranslation } from 'react-i18next';
import { createAppTheme } from '../theme/createAppTheme';
import { ltrCache, rtlCache } from '../theme/rtlCache';
import { DAYJS_LOCALE } from '../utils/dayjsLocales';
import { usePreferences } from './PreferencesContext';

// MUI ships Persian strings; Pashto starts from them and every visible table string
// is then overridden from our own translation files (see `ownLocale`).
const VENDOR_LOCALES = {
  en: [coreEn, gridEn, pickersEn],
  fa: [coreFa, gridFa, pickersFa],
  ps: [coreFa, gridFa, pickersFa],
};

export function AppThemeProvider({ children }) {
  const { language, direction, themeMode } = usePreferences();
  const { t } = useTranslation();

  const theme = useMemo(() => {
    const ownLocale = {
      components: {
        MuiTablePagination: {
          defaultProps: {
            labelRowsPerPage: t('table.rowsPerPage'),
            labelDisplayedRows: ({ from, to, count }) => t('table.displayedRows', { from, to, count }),
          },
        },
        MuiDataGrid: {
          defaultProps: {
            localeText: {
              noRowsLabel: t('common.noData'),
              noResultsOverlayLabel: t('table.noResults'),
              footerRowSelected: (count) => t('table.rowsSelected', { count }),
              toolbarColumns: t('table.columns'),
              toolbarDensity: t('table.density'),
              toolbarExport: t('common.export'),
              columnMenuSortAsc: t('table.sortAsc'),
              columnMenuSortDesc: t('table.sortDesc'),
              columnMenuUnsort: t('table.unsort'),
              columnMenuHideColumn: t('table.hideColumn'),
              columnMenuManageColumns: t('table.manageColumns'),
              columnMenuLabel: t('table.columnMenu'),
              paginationRowsPerPage: t('table.rowsPerPage'),
              paginationDisplayedRows: ({ from, to, count }) => t('table.displayedRows', { from, to, count }),
            },
          },
        },
      },
    };
    return createAppTheme({ mode: themeMode, direction, locales: [...VENDOR_LOCALES[language], ownLocale] });
  }, [themeMode, direction, language, t]);

  return (
    <CacheProvider value={direction === 'rtl' ? rtlCache : ltrCache}>
      <ThemeProvider theme={theme}>
        <LocalizationProvider dateAdapter={AdapterDayjs} adapterLocale={DAYJS_LOCALE[language]}>
          <CssBaseline enableColorScheme />
          {children}
        </LocalizationProvider>
      </ThemeProvider>
    </CacheProvider>
  );
}
