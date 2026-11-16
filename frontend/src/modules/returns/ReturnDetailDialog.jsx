import { useTranslation } from 'react-i18next';
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Divider, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material';
import MoneyText from '../../components/display/MoneyText';
import QtyText from '../../components/display/QtyText';
import UserText from '../../components/display/UserText';
import Ltr from '../../components/display/Ltr';
import { useSettings } from '../../contexts/SettingsContext';
import { formatDateTime } from '../../utils/format';

/** Read-only view of one posted return. */
export default function ReturnDetailDialog({ value, onClose }) {
  const { t } = useTranslation();
  const { settings } = useSettings();
  const r = value;
  const ns = r?.kind === 'SALES' ? 'returns.sales' : 'returns.purchase';
  return (
    <Dialog open={Boolean(r)} onClose={onClose} maxWidth="md" fullWidth>
      {r && (
        <>
          <DialogTitle>
            <Ltr>{r.returnNo}</Ltr>
          </DialogTitle>
          <DialogContent dividers>
            <Stack spacing={1} sx={{ mb: 2 }}>
              <Row label={t(`${ns}.documentNo`)} value={<Ltr>{r.documentNo}</Ltr>} />
              <Row label={t(`${ns}.party`)} value={<UserText>{r.partyName}</UserText>} />
              <Row label={t('returns.date')} value={<Ltr>{formatDateTime(r.returnDate)}</Ltr>} />
              <Row label={t('returns.reason')} value={<UserText>{r.reason}</UserText>} />
              <Row label={t('returns.by')} value={<UserText>{r.createdByName}</UserText>} />
            </Stack>
            <Box sx={{ overflowX: 'auto' }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>{t('sales.product')}</TableCell>
                    <TableCell align="right">{t('returns.qty')}</TableCell>
                    <TableCell align="right">{t('returns.netPrice')}</TableCell>
                    <TableCell align="right">{t('sales.lineTotal')}</TableCell>
                    {r.kind === 'SALES' && <TableCell>{t('returns.condition')}</TableCell>}
                    <TableCell>{t('returns.lineReason')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {r.items.map((i) => (
                    <TableRow key={i.id}>
                      <TableCell>
                        <UserText>{i.productName}</UserText>
                      </TableCell>
                      <TableCell align="right">
                        <QtyText value={i.quantity} unit={i.unitName} />
                      </TableCell>
                      <TableCell align="right">
                        <MoneyText amount={Number(i.unitNetPrice).toFixed(2)} currency={r.currency} />
                      </TableCell>
                      <TableCell align="right">
                        <MoneyText amount={i.lineTotal} currency={r.currency} fontWeight={600} />
                      </TableCell>
                      {r.kind === 'SALES' && <TableCell>{t(`returns.conditions.${i.condition}`)}</TableCell>}
                      <TableCell>
                        <UserText>{i.reason}</UserText>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>
            <Divider sx={{ my: 2 }} />
            <Stack spacing={1} sx={{ maxWidth: 420, ml: 'auto' }}>
              <Row label={<b>{t('returns.total')}</b>} value={<MoneyText amount={r.totalAmount} currency={r.currency} variant="h6" />} />
              <Row label={t(`${ns}.reduction`)} value={<MoneyText amount={r.reduction} currency={r.currency} />} />
              {Number(r.cashRefundAmount) > 0 && (
                <Row label={`${t(`${ns}.refunded`)} (${r.refundCashAccountName})`} value={<MoneyText amount={r.cashRefundAmount} currency={r.currency} />} />
              )}
              {Number(r.creditAmount) > 0 && <Row label={t(`${ns}.credited`)} value={<MoneyText amount={r.creditAmount} currency={r.currency} />} />}
              {r.totalCostBase != null && <Row label={t('returns.cost')} value={<MoneyText amount={r.totalCostBase} currency={settings.baseCurrency} />} />}
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button onClick={onClose}>{t('common.close')}</Button>
          </DialogActions>
        </>
      )}
    </Dialog>
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
