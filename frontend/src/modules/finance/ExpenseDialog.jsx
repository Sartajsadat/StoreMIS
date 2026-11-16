import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import dayjs from 'dayjs';
import { Alert, Button, Grid, MenuItem, Stack, TextField, Typography } from '@mui/material';
import AttachFileOutlined from '@mui/icons-material/AttachFileOutlined';
import FormDialog from '../../components/form/FormDialog';
import { normalizeDecimalInput } from '../../components/form/FormMoneyField';
import { expenseService } from '../../services/expenseService';
import { errorMessage } from '../../services/apiClient';
import { useCashAccounts } from '../../hooks/useCurrency';
import { uuid } from '../../utils/uuid';
import { categoryLabel } from './expenseUtils';

/** Record an expense; the money leaves the chosen cash account immediately. A receipt photo or PDF can be attached. */
export default function ExpenseDialog({ open, onClose, onSaved }) {
  const { t } = useTranslation();
  const accounts = useCashAccounts();
  const categories = useQuery({ queryKey: ['expense-categories', 'active'], queryFn: () => expenseService.categories({ active: true }), enabled: open });
  const active = useMemo(() => (accounts.data ?? []).filter((a) => a.active), [accounts.data]);
  const today = dayjs().format('YYYY-MM-DD');
  const [categoryId, setCategoryId] = useState('');
  const [date, setDate] = useState(today);
  const [description, setDescription] = useState('');
  const [amount, setAmount] = useState('');
  const [accountId, setAccountId] = useState('');
  const [file, setFile] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [requestId, setRequestId] = useState(uuid);
  const account = active.find((a) => String(a.id) === String(accountId));

  useEffect(() => {
    if (!open) return;
    setCategoryId('');
    setDate(dayjs().format('YYYY-MM-DD'));
    setDescription('');
    setAmount('');
    setFile(null);
    setError(null);
    setRequestId(uuid());
    setAccountId((active.find((a) => a.isDefault) ?? active[0])?.id ?? '');
  }, [open, active]);

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    if (!categoryId || !description.trim() || !(Number(amount) > 0) || !accountId) return setError(t('errors.VALIDATION_FAILED'));
    setBusy(true);
    try {
      let saved = await expenseService.create({ clientRequestId: requestId, categoryId, expenseDate: date, description: description.trim(), amount, cashAccountId: accountId });
      if (file) {
        try {
          saved = await expenseService.uploadAttachment(saved.id, file);
        } catch (err) {
          onSaved(saved, errorMessage(t, err));
          return;
        }
      }
      onSaved(saved);
    } catch (err) {
      setError(errorMessage(t, err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <FormDialog open={open} title={t('expenses.new')} onClose={onClose} onSubmit={submit} submitting={busy} submitLabel={t('expenses.save')}>
      <Stack spacing={2} sx={{ pt: 1 }}>
        {error && <Alert severity="error">{error}</Alert>}
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField select autoFocus label={t('expenses.category')} value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
              {(categories.data ?? []).map((c) => (
                <MenuItem key={c.id} value={c.id}>
                  {categoryLabel(t, c)}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField type="date" label={t('expenses.date')} value={date} onChange={(e) => setDate(e.target.value)} slotProps={{ inputLabel: { shrink: true }, htmlInput: { max: today, dir: 'ltr' } }} />
          </Grid>
          <Grid size={12}>
            <TextField label={t('expenses.description')} value={description} onChange={(e) => setDescription(e.target.value)} inputProps={{ maxLength: 300 }} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField select label={t('expenses.paidFrom')} value={accountId} onChange={(e) => setAccountId(e.target.value)}>
              {active.map((a) => (
                <MenuItem key={a.id} value={a.id}>
                  {a.name}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField
              label={t('expenses.amount')}
              value={amount}
              onChange={(e) => setAmount(normalizeDecimalInput(e.target.value, 2))}
              slotProps={{ htmlInput: { inputMode: 'decimal', dir: 'ltr', style: { textAlign: 'end' } }, input: { endAdornment: account?.currency } }}
            />
          </Grid>
        </Grid>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <Button component="label" variant="outlined" startIcon={<AttachFileOutlined />}>
            {t('expenses.attach')}
            <input hidden type="file" accept="image/png,image/jpeg,image/webp,application/pdf" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
          </Button>
          <Typography variant="body2" color="text.secondary" noWrap>
            {file ? file.name : t('expenses.attachHint')}
          </Typography>
        </Stack>
      </Stack>
    </FormDialog>
  );
}
