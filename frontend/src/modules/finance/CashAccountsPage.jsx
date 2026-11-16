import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Card, CardHeader, Chip, Grid, Stack, TextField, Typography } from '@mui/material';
import AccountBalanceWalletOutlined from '@mui/icons-material/AccountBalanceWalletOutlined';
import PageHeader from '../../components/display/PageHeader';
import MoneyText from '../../components/display/MoneyText';
import FormDialog from '../../components/form/FormDialog';
import { normalizeDecimalInput } from '../../components/form/FormMoneyField';
import CashTransactionsTable from './CashTransactionsTable';
import { cashService } from '../../services/cashService';
import { errorMessage } from '../../services/apiClient';
import { useCashAccounts } from '../../hooks/useCurrency';
import { useNotify } from '../../contexts/NotificationContext';

export default function CashAccountsPage() {
  const { t } = useTranslation();
  const notify = useNotify();
  const queryClient = useQueryClient();
  const accounts = useCashAccounts();
  const [target, setTarget] = useState(null);
  const [amount, setAmount] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await cashService.openingBalance(target.id, amount);
      notify.success(t('cash.openingSaved'));
      setTarget(null);
      ['cash-accounts', 'cash-transactions'].forEach((k) => queryClient.invalidateQueries({ queryKey: [k] }));
    } catch (err) {
      setError(errorMessage(t, err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader title={t('cash.accountsTitle')} subtitle={t('cash.accountsSubtitle')} />
      <Grid container spacing={3} sx={{ mb: 3 }}>
        {(accounts.data ?? []).map((a) => (
          <Grid key={a.id} size={{ xs: 12, sm: 6, lg: 4 }}>
            <Card sx={{ p: 2.5 }}>
              <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                    <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 500 }}>
                      {a.name}
                    </Typography>
                    {a.isDefault && <Chip size="small" label={t('cash.default')} />}
                  </Stack>
                  <Typography variant="h5" sx={{ mt: 1 }}>
                    <MoneyText amount={a.balance} currency={a.currency} alwaysDecimals />
                  </Typography>
                </div>
                <AccountBalanceWalletOutlined color="primary" />
              </Stack>
              {!a.hasTransactions && (
                <Button
                  size="small"
                  sx={{ mt: 1.5 }}
                  onClick={() => {
                    setTarget(a);
                    setAmount('');
                    setError(null);
                  }}
                >
                  {t('cash.setOpening')}
                </Button>
              )}
            </Card>
          </Grid>
        ))}
      </Grid>
      <Card>
        <CardHeader title={t('cash.recent')} />
        <CashTransactionsTable />
      </Card>
      <FormDialog open={Boolean(target)} title={t('cash.openingTitle', { account: target?.name ?? '' })} onClose={() => setTarget(null)} onSubmit={submit} submitting={busy}>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Typography variant="body2" color="text.secondary">
            {t('cash.openingHint')}
          </Typography>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField
            autoFocus
            label={t('payments.amount')}
            value={amount}
            onChange={(e) => setAmount(normalizeDecimalInput(e.target.value, 2))}
            slotProps={{ htmlInput: { inputMode: 'decimal', dir: 'ltr', style: { textAlign: 'end' } }, input: { endAdornment: target?.currency } }}
          />
        </Stack>
      </FormDialog>
    </>
  );
}
