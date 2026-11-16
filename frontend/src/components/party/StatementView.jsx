import { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Box, MenuItem, Stack, Table, TableBody, TableCell, TableFooter, TableHead, TableRow, TextField, Typography } from '@mui/material';
import DateRangeFilter from '../data/DateRangeFilter';
import ExportButtons from '../data/ExportButtons';
import MoneyText from '../display/MoneyText';
import Ltr from '../display/Ltr';
import { useCurrencies } from '../../hooks/useCurrency';
import { resolveRange } from '../../utils/dateRange';
import { formatDate } from '../../utils/format';

/**
 * Party statement (supplier or customer) for one currency and period.
 * increaseField: the ledger column that increases the balance — "credit" for suppliers (we owe more), "debit" for customers.
 */
export default function StatementView({ queryKey, load, increaseField = 'credit' }) {
  const { t } = useTranslation();
  const currencies = useCurrencies();
  const [currency, setCurrency] = useState(currencies[0]);
  const [range, setRange] = useState({ preset: 'YEAR', ...resolveRange('YEAR') });
  const decreaseField = increaseField === 'credit' ? 'debit' : 'credit';
  const st = useQuery({ queryKey: [...queryKey, currency, range.from, range.to], queryFn: () => load({ currency, from: range.from, to: range.to }), placeholderData: keepPreviousData });
  const s = st.data;
  const totalOf = (field) => (field === 'credit' ? s.totalCredit : s.totalDebit);
  return (
    <Box>
      <Stack direction="row" sx={{ mb: 2, flexWrap: 'wrap', gap: 1.5, alignItems: 'center' }}>
        <TextField select label={t('statement.currency')} value={currency} onChange={(e) => setCurrency(e.target.value)} sx={{ width: 130 }} fullWidth={false}>
          {currencies.map((c) => (
            <MenuItem key={c} value={c}>
              {c}
            </MenuItem>
          ))}
        </TextField>
        <DateRangeFilter value={range} onChange={setRange} />
        <Box sx={{ ml: 'auto' }}>
          <ExportButtons />
        </Box>
      </Stack>
      {s && (
        <Box sx={{ overflowX: 'auto' }}>
          <Table size="small" sx={{ minWidth: 640 }}>
            <TableHead>
              <TableRow>
                <TableCell>{t('statement.date')}</TableCell>
                <TableCell>{t('statement.type')}</TableCell>
                <TableCell>{t('statement.reference')}</TableCell>
                <TableCell align="right">{t('statement.credit')}</TableCell>
                <TableCell align="right">{t('statement.debit')}</TableCell>
                <TableCell align="right">{t('statement.balance')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              <TableRow>
                <TableCell colSpan={5}>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    {t('statement.opening')}
                  </Typography>
                </TableCell>
                <TableCell align="right">
                  <MoneyText amount={s.openingBalance} currency={s.currency} fontWeight={600} />
                </TableCell>
              </TableRow>
              {s.lines.map((l) => (
                <TableRow key={l.id} hover>
                  <TableCell>
                    <Ltr>{formatDate(l.date)}</Ltr>
                  </TableCell>
                  <TableCell>{t(`statement.types.${l.type}`)}</TableCell>
                  <TableCell>
                    <Ltr>{l.referenceNo}</Ltr>
                  </TableCell>
                  <TableCell align="right">{Number(l[increaseField]) ? <MoneyText amount={l[increaseField]} currency={s.currency} /> : ''}</TableCell>
                  <TableCell align="right">{Number(l[decreaseField]) ? <MoneyText amount={l[decreaseField]} currency={s.currency} /> : ''}</TableCell>
                  <TableCell align="right">
                    <MoneyText amount={l.balance} currency={s.currency} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
            <TableFooter>
              <TableRow>
                <TableCell colSpan={3}>{t('statement.closing')}</TableCell>
                <TableCell align="right">
                  <MoneyText amount={totalOf(increaseField)} currency={s.currency} />
                </TableCell>
                <TableCell align="right">
                  <MoneyText amount={totalOf(decreaseField)} currency={s.currency} />
                </TableCell>
                <TableCell align="right">
                  <MoneyText amount={s.closingBalance} currency={s.currency} fontWeight={700} />
                </TableCell>
              </TableRow>
            </TableFooter>
          </Table>
        </Box>
      )}
    </Box>
  );
}
