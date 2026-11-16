import { Box, MenuItem, TextField } from '@mui/material';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import dayjs from 'dayjs';
import { useTranslation } from 'react-i18next';
import { RANGE_PRESETS, resolveRange } from '../../utils/dateRange';

/**
 * Period filter used by the dashboard and every report: Today / This Week / This Month / This Year / Custom.
 * value: { preset, from, to } with ISO dates; onChange receives the resolved value.
 */
export default function DateRangeFilter({ value, onChange }) {
  const { t } = useTranslation();
  const preset = value?.preset ?? 'TODAY';

  const setPreset = (p) => onChange({ preset: p, ...resolveRange(p, value) });
  const setCustom = (key, d) => {
    if (!d || !d.isValid()) return;
    onChange({ ...value, preset: 'CUSTOM', [key]: d.format('YYYY-MM-DD') });
  };

  return (
    <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap', alignItems: 'center' }}>
      <TextField select label={t('dateRange.label')} value={preset} onChange={(e) => setPreset(e.target.value)} sx={{ width: 170 }} fullWidth={false}>
        {RANGE_PRESETS.map((p) => (
          <MenuItem key={p} value={p}>
            {t(`dateRange.${p}`)}
          </MenuItem>
        ))}
      </TextField>
      {preset === 'CUSTOM' && (
        <>
          <DatePicker
            label={t('dateRange.from')}
            format="YYYY-MM-DD"
            value={value?.from ? dayjs(value.from) : null}
            onChange={(d) => setCustom('from', d)}
            maxDate={value?.to ? dayjs(value.to) : undefined}
            slotProps={{ textField: { size: 'small', sx: { width: 170 } } }}
          />
          <DatePicker
            label={t('dateRange.to')}
            format="YYYY-MM-DD"
            value={value?.to ? dayjs(value.to) : null}
            onChange={(d) => setCustom('to', d)}
            minDate={value?.from ? dayjs(value.from) : undefined}
            slotProps={{ textField: { size: 'small', sx: { width: 170 } } }}
          />
        </>
      )}
    </Box>
  );
}
