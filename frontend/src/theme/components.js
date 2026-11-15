import { alpha } from '@mui/material/styles';

// Component-level overrides shared by both themes.
export function buildComponents(theme) {
  const { palette } = theme;
  const border = palette.divider;
  return {
    MuiCssBaseline: {
      styleOverrides: {
        body: { backgroundColor: palette.background.default },
        '*::-webkit-scrollbar': { width: 8, height: 8 },
        '*::-webkit-scrollbar-thumb': { backgroundColor: alpha(palette.text.primary, 0.2), borderRadius: 8 },
        '@media print': { body: { backgroundColor: '#fff' } },
      },
    },
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: { root: { borderRadius: 8, textTransform: 'none', fontWeight: 600 } },
    },
    MuiIconButton: { styleOverrides: { root: { borderRadius: 8 } } },
    MuiPaper: {
      defaultProps: { elevation: 0 },
      styleOverrides: { root: { backgroundImage: 'none' } },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          borderRadius: 12,
          border: `1px solid ${border}`,
          boxShadow: palette.mode === 'light' ? '0 1px 3px rgba(16,24,40,0.06), 0 1px 2px rgba(16,24,40,0.04)' : 'none',
        },
      },
    },
    MuiCardHeader: {
      styleOverrides: {
        root: { padding: '16px 20px 8px' },
        title: { fontSize: '1rem', fontWeight: 600 },
      },
    },
    MuiCardContent: { styleOverrides: { root: { padding: '12px 20px 20px', '&:last-child': { paddingBottom: 20 } } } },
    MuiTextField: { defaultProps: { size: 'small', fullWidth: true } },
    MuiFormControl: { defaultProps: { size: 'small' } },
    MuiOutlinedInput: { styleOverrides: { root: { borderRadius: 8 } } },
    MuiChip: { styleOverrides: { root: { fontWeight: 600, borderRadius: 6 } } },
    MuiDialog: { styleOverrides: { paper: { borderRadius: 14 } } },
    MuiDialogTitle: { styleOverrides: { root: { fontWeight: 700, fontSize: '1.1rem' } } },
    MuiTooltip: { defaultProps: { arrow: true } },
    MuiMenu: {
      styleOverrides: {
        paper: { borderRadius: 10, border: `1px solid ${border}`, boxShadow: '0 8px 24px rgba(16,24,40,0.14)' },
      },
    },
    MuiListItemButton: { styleOverrides: { root: { borderRadius: 8 } } },
    MuiTableCell: {
      styleOverrides: {
        head: { fontWeight: 600, color: palette.text.secondary, backgroundColor: palette.background.default },
      },
    },
    MuiDataGrid: {
      styleOverrides: {
        root: {
          border: 'none',
          '--DataGrid-containerBackground': palette.background.default,
          '& .MuiDataGrid-columnHeaderTitle': { fontWeight: 600, color: palette.text.secondary },
          '& .MuiDataGrid-cell:focus, & .MuiDataGrid-columnHeader:focus': { outline: 'none' },
        },
      },
    },
  };
}
