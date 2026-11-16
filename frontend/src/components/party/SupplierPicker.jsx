import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Autocomplete, Box, TextField, Typography } from '@mui/material';
import { supplierService } from '../../services/supplierService';
import { useDebounce } from '../../hooks/useDebounce';
import UserText from '../display/UserText';
import Ltr from '../display/Ltr';
import BalanceChips from './BalanceChips';

/** Active-supplier search by name, phone or contact person. */
export default function SupplierPicker({ value, onChange, label, error, helperText, autoFocus, disabled }) {
  const { t } = useTranslation();
  const [input, setInput] = useState('');
  const q = useDebounce(input.trim(), 250);
  const results = useQuery({ queryKey: ['supplier-search', q], queryFn: () => supplierService.list({ q: q || undefined, active: true, size: 20 }), staleTime: 10000 });
  return (
    <Autocomplete
      value={value ?? null}
      onChange={(_e, v) => onChange(v)}
      inputValue={input}
      onInputChange={(_e, v) => setInput(v)}
      options={results.data?.content ?? []}
      filterOptions={(x) => x}
      getOptionLabel={(o) => o?.name ?? ''}
      isOptionEqualToValue={(a, b) => a.id === b.id}
      loading={results.isFetching}
      disabled={disabled}
      noOptionsText={t('suppliers.noMatches')}
      renderOption={(props, o) => {
        const { key, ...rest } = props;
        return (
          <Box component="li" key={key} {...rest} sx={{ gap: 1.5, justifyContent: 'space-between' }}>
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="body2" sx={{ fontWeight: 600 }} noWrap>
                <UserText>{o.name}</UserText>
              </Typography>
              {o.phone && (
                <Typography variant="caption" color="text.secondary">
                  <Ltr>{o.phone}</Ltr>
                </Typography>
              )}
            </Box>
            <BalanceChips balances={o.balances} emptyText=" " />
          </Box>
        );
      }}
      renderInput={(params) => (
        <TextField {...params} label={label ?? t('purchases.supplier')} placeholder={t('suppliers.pickerPlaceholder')} error={error} helperText={helperText} autoFocus={autoFocus} />
      )}
    />
  );
}
