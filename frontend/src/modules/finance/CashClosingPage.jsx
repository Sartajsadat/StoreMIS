import { useEffect, useMemo, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import dayjs from 'dayjs';
import {
  Alert, Box, Button, Card, CardContent, CardHeader, Chip, Divider, Grid, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import LockOutlined from '@mui/icons-material/LockOutlined';
import LockOpenOutlined from '@mui/icons-material/LockOpenOutlined';
import PageHeader from '../../components/display/PageHeader';
import PageLoader from '../../components/feedback/PageLoader';
import MoneyText from '../../components/display/MoneyText';
import StatusChip from '../../components/display/StatusChip';
import UserText from '../../components/display/UserText';
import Ltr from '../../components/display/Ltr';
import DataTable from '../../components/data/DataTable';
import ConfirmDialog from '../../components/feedback/ConfirmDialog';
import ReasonDialog from '../../components/feedback/ReasonDialog';
import { normalizeDecimalInput } from '../../components/form/FormMoneyField';
import { cashService } from '../../services/cashService';
import { errorMessage } from '../../services/apiClient';
import { useNotify } from '../../contexts/NotificationContext';
import { dec } from '../../utils/money';
import { formatDate, formatDateTime } from '../../utils/format';

/** Difference between the counted cash and the expected balance, or null while nothing is entered. */
export function countDifference(counted, expected) {
  if (counted === '' || counted == null) return null;
  return dec(counted).minus(dec(expected));
}

export default function CashClosingPage() {
  const { t } = useTranslation();
  const notify = useNotify();
  const queryClient = useQueryClient();
  const today = dayjs().format('YYYY-MM-DD');
  const [date, setDate] = useState(today);
  const [counted, setCounted] = useState({});
  const [notes, setNotes] = useState('');
  const [error, setError] = useState(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [reopenOpen, setReopenOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const preview = useQuery({ queryKey: ['cash-closing', 'preview', date], queryFn: () => cashService.closingPreview(date), enabled: Boolean(date) });
  const history = useQuery({ queryKey: ['cash-closing', 'history'], queryFn: () => cashService.closings({}) });
  const p = preview.data;

  useEffect(() => {
    setCounted({});
    setNotes('');
    setError(null);
  }, [date]);

  const refresh = () => ['cash-closing', 'cash-accounts', 'cash-transactions'].forEach((k) => queryClient.invalidateQueries({ queryKey: [k] }));
  const accounts = p?.accounts ?? [];
  const differences = accounts.map((a) => countDifference(counted[a.accountId], a.expectedBalance));
  const allCounted = accounts.length > 0 && accounts.every((a) => counted[a.accountId] !== undefined && counted[a.accountId] !== '');
  const anyDifference = differences.some((d) => d && !d.isZero());

  const close = async () => {
    setBusy(true);
    setError(null);
    try {
      await cashService.closeDay({ date, notes: notes.trim() || null, accounts: accounts.map((a) => ({ accountId: a.accountId, countedBalance: counted[a.accountId] })) });
      notify.success(t('closing.closed', { date: formatDate(date) }));
      setConfirmOpen(false);
      refresh();
    } catch (err) {
      setError(errorMessage(t, err));
      setConfirmOpen(false);
    } finally {
      setBusy(false);
    }
  };

  const historyColumns = useMemo(
    () => [
      { field: 'date', headerName: t('closing.date'), width: 120, renderCell: (r) => <Ltr>{formatDate(r.value)}</Ltr> },
      { field: 'accountName', headerName: t('cash.account'), width: 130 },
      { field: 'expectedBalance', headerName: t('closing.expected'), width: 150, align: 'right', headerAlign: 'right', renderCell: (r) => <MoneyText amount={r.value} currency={r.row.currency} /> },
      { field: 'countedBalance', headerName: t('closing.counted'), width: 150, align: 'right', headerAlign: 'right', renderCell: (r) => <MoneyText amount={r.value} currency={r.row.currency} /> },
      {
        field: 'difference',
        headerName: t('closing.difference'),
        width: 140,
        align: 'right',
        headerAlign: 'right',
        renderCell: (r) => <MoneyText amount={r.value} currency={r.row.currency} color={Number(r.value) < 0 ? 'error.main' : Number(r.value) > 0 ? 'success.main' : undefined} />,
      },
      { field: 'status', headerName: t('common.status'), width: 120, renderCell: (r) => <StatusChip status={r.value} /> },
      { field: 'closedByName', headerName: t('closing.closedBy'), width: 140, renderCell: (r) => <UserText>{r.value}</UserText> },
      { field: 'notes', headerName: t('common.notes'), flex: 1, minWidth: 160, renderCell: (r) => <UserText>{r.row.reopenReason ? `${r.value ?? ''} · ${r.row.reopenReason}` : r.value}</UserText> },
    ],
    [t],
  );

  return (
    <>
      <PageHeader
        title={t('closing.title')}
        subtitle={t('closing.subtitle')}
        actions={
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            <TextField
              type="date"
              size="small"
              label={t('closing.date')}
              value={date}
              onChange={(e) => setDate(e.target.value)}
              slotProps={{ inputLabel: { shrink: true }, htmlInput: { max: today, dir: 'ltr' } }}
              sx={{ width: 180 }}
            />
            <Button onClick={() => setDate(today)} disabled={date === today}>
              {t('dateRange.TODAY')}
            </Button>
          </Stack>
        }
      />
      {preview.isLoading || !p ? (
        <PageLoader />
      ) : (
        <>
          {p.lastClosedDate && (
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              {t('closing.lastClosed')}: <Ltr>{formatDate(p.lastClosedDate)}</Ltr>
            </Typography>
          )}
          {p.closed ? (
            <Alert
              severity="success"
              icon={<LockOutlined />}
              sx={{ mb: 3 }}
              action={
                p.canReopen && (
                  <Button color="inherit" size="small" startIcon={<LockOpenOutlined />} onClick={() => setReopenOpen(true)}>
                    {t('closing.reopen')}
                  </Button>
                )
              }
            >
              {t('closing.isClosed', { date: formatDate(p.date) })}
            </Alert>
          ) : !p.canClose ? (
            <Alert severity="info" sx={{ mb: 3 }}>
              {t('closing.cannotClose')}
            </Alert>
          ) : (
            <Alert severity="info" sx={{ mb: 3 }}>
              {t('closing.howTo')}
            </Alert>
          )}
          {error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {error}
            </Alert>
          )}
          <Grid container spacing={3}>
            {accounts.map((a, idx) => {
              const diff = p.closed ? dec(a.closing?.difference ?? 0) : differences[idx];
              return (
                <Grid key={a.accountId} size={{ xs: 12, lg: 6 }}>
                  <Card>
                    <CardHeader title={a.accountName} action={a.closing && <StatusChip status={a.closing.status} />} />
                    <CardContent sx={{ pt: 0 }}>
                      <Stack spacing={1}>
                        <Row label={t('closing.opening')} value={<MoneyText amount={a.openingBalance} currency={a.currency} />} />
                        <Row label={t('closing.totalIn')} value={<MoneyText amount={a.totalIn} currency={a.currency} color="success.main" />} />
                        <Row label={t('closing.totalOut')} value={<MoneyText amount={a.totalOut} currency={a.currency} color="error.main" />} />
                        <Divider />
                        <Row label={<b>{t('closing.expected')}</b>} value={<MoneyText amount={a.expectedBalance} currency={a.currency} variant="h6" />} />
                        {p.closed && a.closing ? (
                          <Row label={t('closing.counted')} value={<MoneyText amount={a.closing.countedBalance} currency={a.currency} fontWeight={700} />} />
                        ) : (
                          p.canClose && (
                            <TextField
                              label={t('closing.counted')}
                              value={counted[a.accountId] ?? ''}
                              onChange={(e) => setCounted((c) => ({ ...c, [a.accountId]: normalizeDecimalInput(e.target.value, 2) }))}
                              slotProps={{ htmlInput: { inputMode: 'decimal', dir: 'ltr', style: { textAlign: 'end' }, 'aria-label': `${t('closing.counted')} ${a.accountName}` }, input: { endAdornment: a.currency } }}
                            />
                          )
                        )}
                        {diff && (
                          <Row
                            label={t('closing.difference')}
                            value={
                              <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                                {!diff.isZero() && <Chip size="small" color={diff.lt(0) ? 'error' : 'success'} label={diff.lt(0) ? t('closing.short') : t('closing.over')} />}
                                <MoneyText amount={diff.toFixed(2)} currency={a.currency} fontWeight={700} color={diff.lt(0) ? 'error.main' : diff.gt(0) ? 'success.main' : undefined} />
                              </Stack>
                            }
                          />
                        )}
                      </Stack>
                      {a.byType.length > 0 && (
                        <Box sx={{ mt: 2, overflowX: 'auto' }}>
                          <Typography variant="subtitle2" sx={{ mb: 0.5 }}>
                            {t('closing.byType')}
                          </Typography>
                          <Table size="small">
                            <TableBody>
                              {a.byType.map((x) => (
                                <TableRow key={`${x.type}-${x.direction}`}>
                                  <TableCell>{t(`cash.types.${x.type}`)}</TableCell>
                                  <TableCell align="right">
                                    <Ltr>{`× ${x.count}`}</Ltr>
                                  </TableCell>
                                  <TableCell align="right">
                                    <MoneyText amount={x.direction === 'OUT' ? `-${x.amount}` : x.amount} currency={a.currency} color={x.direction === 'OUT' ? 'error.main' : 'success.main'} />
                                  </TableCell>
                                </TableRow>
                              ))}
                            </TableBody>
                          </Table>
                        </Box>
                      )}
                      {a.byUser.length > 0 && (
                        <Box sx={{ mt: 2, overflowX: 'auto' }}>
                          <Typography variant="subtitle2" sx={{ mb: 0.5 }}>
                            {t('closing.byUser')}
                          </Typography>
                          <Table size="small">
                            <TableHead>
                              <TableRow>
                                <TableCell>{t('closing.user')}</TableCell>
                                <TableCell align="right">{t('cash.in')}</TableCell>
                                <TableCell align="right">{t('cash.out')}</TableCell>
                              </TableRow>
                            </TableHead>
                            <TableBody>
                              {a.byUser.map((u) => (
                                <TableRow key={u.userId ?? 'system'}>
                                  <TableCell>
                                    <UserText>{u.userName ?? t('closing.system')}</UserText>
                                  </TableCell>
                                  <TableCell align="right">
                                    <MoneyText amount={u.totalIn} currency={a.currency} />
                                  </TableCell>
                                  <TableCell align="right">
                                    <MoneyText amount={u.totalOut} currency={a.currency} />
                                  </TableCell>
                                </TableRow>
                              ))}
                            </TableBody>
                          </Table>
                        </Box>
                      )}
                      {p.closed && a.closing && (
                        <Typography variant="caption" color="text.secondary" component="div" sx={{ mt: 2 }}>
                          {t('closing.closedByAt')}: <UserText>{a.closing.closedByName}</UserText> · <Ltr>{formatDateTime(a.closing.closedAt)}</Ltr>
                          {a.closing.notes && (
                            <>
                              {' · '}
                              <UserText>{a.closing.notes}</UserText>
                            </>
                          )}
                        </Typography>
                      )}
                    </CardContent>
                  </Card>
                </Grid>
              );
            })}
          </Grid>
          {p.canClose && (
            <Card sx={{ mt: 3 }}>
              <CardContent>
                <Stack spacing={2}>
                  <TextField
                    label={anyDifference ? t('closing.notesRequired') : t('common.notes')}
                    value={notes}
                    onChange={(e) => setNotes(e.target.value)}
                    multiline
                    minRows={2}
                    error={anyDifference && !notes.trim()}
                    helperText={anyDifference ? t('closing.differenceHint') : ' '}
                  />
                  <Box>
                    <Button variant="contained" size="large" startIcon={<LockOutlined />} disabled={!allCounted || (anyDifference && !notes.trim())} onClick={() => setConfirmOpen(true)}>
                      {t('closing.closeDay')}
                    </Button>
                  </Box>
                </Stack>
              </CardContent>
            </Card>
          )}
        </>
      )}
      <Card sx={{ mt: 3 }}>
        <CardHeader title={t('closing.history')} />
        <Box sx={{ px: 1, pb: 1 }}>
          <DataTable rows={history.data} columns={historyColumns} loading={history.isFetching} />
        </Box>
      </Card>
      <ConfirmDialog
        open={confirmOpen}
        title={t('closing.confirmTitle', { date: formatDate(date) })}
        message={t('closing.confirmText')}
        confirmLabel={t('closing.closeDay')}
        loading={busy}
        onConfirm={close}
        onClose={() => setConfirmOpen(false)}
      />
      <ReasonDialog
        open={reopenOpen}
        title={t('closing.reopenTitle', { date: formatDate(date) })}
        message={t('closing.reopenHint')}
        confirmLabel={t('closing.reopen')}
        color="warning"
        loading={busy}
        onClose={() => setReopenOpen(false)}
        onConfirm={async (reason) => {
          setBusy(true);
          try {
            await cashService.reopenDay(date, reason);
            notify.success(t('closing.reopened'));
            setReopenOpen(false);
            refresh();
          } catch (e) {
            notify.error(errorMessage(t, e));
          } finally {
            setBusy(false);
          }
        }}
      />
    </>
  );
}

function Row({ label, value }) {
  return (
    <Stack direction="row" spacing={2} sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
      <Typography variant="body2" color="text.secondary" component="div">
        {label}
      </Typography>
      <Box sx={{ textAlign: 'end' }}>{value}</Box>
    </Stack>
  );
}
