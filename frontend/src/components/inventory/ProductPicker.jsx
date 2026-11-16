import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Autocomplete, Avatar, Box, InputAdornment, TextField, Typography } from '@mui/material';
import SearchOutlined from '@mui/icons-material/SearchOutlined';
import Inventory2Outlined from '@mui/icons-material/Inventory2Outlined';
import { productService } from '../../services/productService';
import { useDebounce } from '../../hooks/useDebounce';
import { useUnitLabel } from '../../hooks/useUnitLabel';
import StatusChip from '../display/StatusChip';
import QtyText from '../display/QtyText';
import UserText from '../display/UserText';
import Ltr from '../display/Ltr';

/**
 * Search-as-you-type product selector (name, code or brand). Pressing Enter on an exact code — e.g. from a
 * barcode scanner — selects that product immediately. Reused by adjustments now and by POS / purchases later.
 */
export default function ProductPicker({ value, onChange, label, autoFocus, error, helperText, disabled, size = 'small' }) {
  const { t } = useTranslation();
  const unitLabel = useUnitLabel();
  const [input, setInput] = useState('');
  const q = useDebounce(input.trim(), 200);
  const results = useQuery({ queryKey: ['product-search', q], queryFn: () => productService.search(q, 20), enabled: q.length > 0, staleTime: 10000 });

  useEffect(() => {
    if (!value) setInput('');
  }, [value]);

  const options = results.data ?? [];

  return (
    <Autocomplete
      value={value ?? null}
      onChange={(_e, v) => onChange(v)}
      inputValue={input}
      onInputChange={(_e, v, reason) => {
        if (reason !== 'reset') setInput(v);
      }}
      options={options}
      filterOptions={(x) => x}
      getOptionLabel={(o) => o?.name ?? ''}
      isOptionEqualToValue={(a, b) => a.id === b.id}
      loading={results.isFetching}
      disabled={disabled}
      noOptionsText={q ? t('products.noMatches') : t('products.pickerPlaceholder')}
      loadingText={t('common.loading')}
      autoHighlight
      onKeyDown={(e) => {
        if (e.key === 'Enter' && input.trim()) {
          const exact = options.find((o) => o.code && o.code.toLowerCase() === input.trim().toLowerCase());
          if (exact) {
            // Stop MUI from also selecting the highlighted option, which would add the product twice.
            e.preventDefault();
            e.defaultMuiPrevented = true;
            onChange(exact);
          }
        }
      }}
      renderOption={(props, o) => {
        const { key, ...rest } = props;
        return (
          <Box component="li" key={key} {...rest} sx={{ gap: 1.5 }}>
            <Avatar variant="rounded" src={o.imageUrl ?? undefined} sx={{ width: 36, height: 36, bgcolor: 'action.hover', color: 'text.secondary' }}>
              <Inventory2Outlined fontSize="small" />
            </Avatar>
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <Typography variant="body2" noWrap sx={{ fontWeight: 600 }}>
                <UserText>{o.name}</UserText>
              </Typography>
              <Typography variant="caption" color="text.secondary" component="div" noWrap>
                {o.code && <Ltr>{o.code}</Ltr>}
                {o.code && ' · '}
                {o.category?.name}
              </Typography>
            </Box>
            <Box sx={{ textAlign: 'end' }}>
              <Typography variant="caption" component="div">
                <QtyText value={o.stock?.quantity} unit={unitLabel(o.unit)} />
              </Typography>
              {o.stock?.status !== 'IN_STOCK' && <StatusChip status={o.stock?.status} />}
            </Box>
          </Box>
        );
      }}
      renderInput={(params) => (
        <TextField
          {...params}
          label={label ?? t('inventory.product')}
          placeholder={t('products.pickerPlaceholder')}
          autoFocus={autoFocus}
          error={error}
          helperText={helperText}
          size={size}
          slotProps={{
            input: {
              ...params.InputProps,
              startAdornment: (
                <InputAdornment position="start">
                  <SearchOutlined fontSize="small" />
                </InputAdornment>
              ),
            },
          }}
        />
      )}
    />
  );
}
