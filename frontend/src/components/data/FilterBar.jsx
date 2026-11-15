import { Box, Button, InputAdornment, TextField } from '@mui/material';
import SearchOutlined from '@mui/icons-material/SearchOutlined';
import RestartAltOutlined from '@mui/icons-material/RestartAltOutlined';
import { useTranslation } from 'react-i18next';

/** One row of filters above a table: search box, extra filter controls (children), reset, and end-side actions. */
export default function FilterBar({ search, onSearchChange, searchPlaceholder, onReset, children, actions }) {
  const { t } = useTranslation();
  return (
    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1.5, alignItems: 'center', p: 2 }}>
      {onSearchChange && (
        <TextField
          value={search ?? ''}
          onChange={(e) => onSearchChange(e.target.value)}
          placeholder={searchPlaceholder ?? t('common.searchPlaceholder')}
          sx={{ width: { xs: '100%', sm: 280 } }}
          fullWidth={false}
          slotProps={{
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <SearchOutlined fontSize="small" />
                </InputAdornment>
              ),
            },
            htmlInput: { 'aria-label': t('common.search') },
          }}
        />
      )}
      {children}
      {onReset && (
        <Button color="inherit" startIcon={<RestartAltOutlined />} onClick={onReset}>
          {t('common.reset')}
        </Button>
      )}
      {actions && <Box sx={{ ml: 'auto', display: 'flex', gap: 1, flexWrap: 'wrap' }}>{actions}</Box>}
    </Box>
  );
}
