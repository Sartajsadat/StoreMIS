import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Autocomplete, Box, Chip, TextField, Typography } from '@mui/material';
import { customerService } from '../../services/customerService';
import { useDebounce } from '../../hooks/useDebounce';
import UserText from '../display/UserText';
import Ltr from '../display/Ltr';
import BalanceChips from './BalanceChips';

/** Customer search; the Walk-in Customer is shown with a translated name. */
export default function CustomerPicker({ value, onChange, label, size = 'small', disableClearable, error, helperText }) {
  const { t } = useTranslation();
  const [input, setInput] = useState('');
  const q = useDebounce(input.trim(), 250);
  const results = useQuery({ queryKey: ['customer-search', q], queryFn: () => customerService.list({ q: q || undefined, active: true, size: 20 }), staleTime: 10000 });
  const nameOf = (c) => (c?.walkIn ? t('customers.walkIn') : c?.name ?? '');
  return (
    <Autocomplete
      value={value ?? null}
      onChange={(_e, v) => onChange(v)}
      // The field shows the chosen customer's name; only typed text is used as the search query.
      onInputChange={(_e, v, reason) => setInput(reason === 'input' ? v : '')}
      options={results.data?.content ?? []}
      filterOptions={(x) => x}
      getOptionLabel={nameOf}
      isOptionEqualToValue={(a, b) => a.id === b.id}
      loading={results.isFetching}
      disableClearable={disableClearable}
      noOptionsText={t('customers.noMatches')}
      size={size}
      renderOption={(props, o) => {
        const { key, ...rest } = props;
        return (
          <Box component="li" key={key} {...rest} sx={{ gap: 1.5, justifyContent: 'space-between' }}>
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="body2" sx={{ fontWeight: 600 }} noWrap>
                {o.walkIn ? t('customers.walkIn') : <UserText>{o.name}</UserText>}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {o.phone && <Ltr>{o.phone}</Ltr>} {!o.walkIn && t(`saleTypes.${o.customerType}`)}
              </Typography>
            </Box>
            {o.walkIn ? <Chip size="small" label={t('customers.walkInHint')} /> : <BalanceChips balances={o.balances} emptyText=" " owedLabel={t('customers.owes')} creditLabel={t('customers.credit')} />}
          </Box>
        );
      }}
      renderInput={(params) => <TextField {...params} label={label ?? t('pos.customer')} placeholder={t('customers.pickerPlaceholder')} error={error} helperText={helperText} />}
    />
  );
}
