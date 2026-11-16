import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Card, CardContent, CardHeader, Table, TableBody, TableCell, TableHead, TableRow } from '@mui/material';
import MoneyText from '../../components/display/MoneyText';
import UserText from '../../components/display/UserText';
import Ltr from '../../components/display/Ltr';
import ReturnDetailDialog from './ReturnDetailDialog';
import { returnsService } from '../../services/returnsService';
import { formatDate } from '../../utils/format';

/** Returns made against one sale or purchase (shown on the document page; hidden when there are none). */
export default function DocumentReturns({ kind, documentId }) {
  const { t } = useTranslation();
  const [detail, setDetail] = useState(null);
  const q = useQuery({ queryKey: ['returns', kind, 'document', documentId], queryFn: () => returnsService.forDocument(kind, documentId) });
  const rows = q.data ?? [];
  if (rows.length === 0) return null;
  const ns = kind === 'sales' ? 'returns.sales' : 'returns.purchase';
  return (
    <Card sx={{ mt: 3 }}>
      <CardHeader title={t('returns.ofDocument')} />
      <CardContent sx={{ pt: 0, overflowX: 'auto' }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>{t('returns.number')}</TableCell>
              <TableCell>{t('returns.date')}</TableCell>
              <TableCell align="right">{t('returns.total')}</TableCell>
              <TableCell align="right">{t(`${ns}.refunded`)}</TableCell>
              <TableCell align="right">{t(`${ns}.credited`)}</TableCell>
              <TableCell>{t('returns.reason')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((r) => (
              <TableRow key={r.id} hover sx={{ cursor: 'pointer' }} onClick={() => setDetail(r)}>
                <TableCell>
                  <Ltr>{r.returnNo}</Ltr>
                </TableCell>
                <TableCell>
                  <Ltr>{formatDate(r.businessDate)}</Ltr>
                </TableCell>
                <TableCell align="right">
                  <MoneyText amount={r.totalAmount} currency={r.currency} fontWeight={600} />
                </TableCell>
                <TableCell align="right">{Number(r.cashRefundAmount) ? <MoneyText amount={r.cashRefundAmount} currency={r.currency} /> : ''}</TableCell>
                <TableCell align="right">{Number(r.creditAmount) ? <MoneyText amount={r.creditAmount} currency={r.currency} /> : ''}</TableCell>
                <TableCell>
                  <UserText>{r.reason}</UserText>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
      <ReturnDetailDialog value={detail} onClose={() => setDetail(null)} />
    </Card>
  );
}
