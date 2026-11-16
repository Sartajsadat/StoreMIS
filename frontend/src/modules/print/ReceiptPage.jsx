import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useParams, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Box, Button, GlobalStyles, Stack } from '@mui/material';
import PrintOutlined from '@mui/icons-material/PrintOutlined';
import { salesService } from '../../services/salesService';
import PageLoader from '../../components/feedback/PageLoader';
import UserText from '../../components/display/UserText';
import Ltr from '../../components/display/Ltr';
import { formatDateTime, formatMoney, formatNumber } from '../../utils/format';

const WIDTH = { THERMAL_80: '80mm', THERMAL_58: '58mm', A4: '210mm' };

/** Printable receipt / invoice in the current language. Opened in its own window; prints automatically with ?autoprint=1. */
export default function ReceiptPage() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const { t } = useTranslation();
  const q = useQuery({ queryKey: ['sales', 'receipt', id], queryFn: () => salesService.receipt(id) });

  useEffect(() => {
    if (q.data && params.get('autoprint') === '1') {
      const timer = setTimeout(() => window.print(), 400);
      return () => clearTimeout(timer);
    }
    return undefined;
  }, [q.data, params]);

  if (q.isLoading || !q.data) return <PageLoader fullScreen />;
  const { sale: s, business: b } = q.data;
  const format = b.receiptFormat ?? 'THERMAL_80';
  const thermal = format !== 'A4';
  const width = WIDTH[format];
  const money = (v) => formatMoney(v, s.currency, { alwaysDecimals: !thermal });

  return (
    <Box sx={{ bgcolor: '#e5e7eb', minHeight: '100vh', py: 3, '@media print': { bgcolor: '#fff', py: 0 } }}>
      <GlobalStyles styles={{ '@page': { size: thermal ? `${width} auto` : 'A4', margin: thermal ? '2mm' : '12mm' }, body: { background: '#fff' } }} />
      <Stack direction="row" spacing={1} sx={{ justifyContent: 'center', mb: 2, '@media print': { display: 'none' } }}>
        <Button variant="contained" startIcon={<PrintOutlined />} onClick={() => window.print()}>
          {t('receipt.printAgain')}
        </Button>
        <Button variant="outlined" onClick={() => window.close()} sx={{ bgcolor: '#fff' }}>
          {t('receipt.close')}
        </Button>
      </Stack>
      <Box
        sx={{
          width,
          maxWidth: '100%',
          mx: 'auto',
          bgcolor: '#fff',
          color: '#000',
          p: thermal ? '3mm' : '10mm',
          boxShadow: '0 2px 12px rgba(0,0,0,.15)',
          fontSize: thermal ? (format === 'THERMAL_58' ? 11 : 12) : 13,
          lineHeight: 1.45,
          '@media print': { boxShadow: 'none', p: 0, width: '100%' },
          '& table': { width: '100%', borderCollapse: 'collapse' },
          '& th, & td': { py: thermal ? '2px' : '6px', px: thermal ? 0 : '6px', textAlign: 'start', verticalAlign: 'top' },
          '& .num': { textAlign: 'end', whiteSpace: 'nowrap' },
          '& .rule': { borderTop: '1px dashed #000', my: 1 },
          '& thead th': { borderBottom: thermal ? '1px dashed #000' : '1px solid #000', fontWeight: 700 },
        }}
      >
        <Box sx={{ textAlign: thermal ? 'center' : 'start', display: thermal ? 'block' : 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 2 }}>
          <Box>
            {b.showLogo && b.logoUrl && <Box component="img" src={b.logoUrl} alt="" sx={{ maxHeight: thermal ? 48 : 64, maxWidth: '60%', mb: 0.5 }} />}
            <Box sx={{ fontWeight: 800, fontSize: thermal ? '1.2em' : '1.6em' }}>
              <UserText>{b.name}</UserText>
            </Box>
            {b.headerNote && (
              <Box>
                <UserText>{b.headerNote}</UserText>
              </Box>
            )}
            {b.address && (
              <Box>
                <UserText>{b.address}</UserText>
              </Box>
            )}
            {b.phone && (
              <Box>
                {t('receipt.phone')}: <Ltr>{b.phone}</Ltr>
              </Box>
            )}
          </Box>
          {!thermal && (
            <Box sx={{ textAlign: 'end' }}>
              <Box sx={{ fontWeight: 800, fontSize: '1.4em' }}>{t('receipt.invoice')}</Box>
              <Ltr>{s.invoiceNo}</Ltr>
            </Box>
          )}
        </Box>
        <Box className="rule" />
        {s.status === 'CANCELLED' && <Box sx={{ textAlign: 'center', fontWeight: 800, fontSize: '1.3em', border: '2px solid #000', my: 1 }}>{t('receipt.cancelled')}</Box>}
        <table>
          <tbody>
            {thermal && (
              <tr>
                <td>{t('receipt.invoice')}</td>
                <td className="num">
                  <Ltr>{s.invoiceNo}</Ltr>
                </td>
              </tr>
            )}
            <tr>
              <td>{t('receipt.date')}</td>
              <td className="num">
                <Ltr>{formatDateTime(s.saleDate)}</Ltr>
              </td>
            </tr>
            <tr>
              <td>{t('receipt.customer')}</td>
              <td className="num">{s.walkIn ? t('customers.walkIn') : <UserText>{s.customerName}</UserText>}</td>
            </tr>
            <tr>
              <td>{t('receipt.cashier')}</td>
              <td className="num">
                <UserText>{s.createdByName}</UserText>
              </td>
            </tr>
          </tbody>
        </table>
        <Box className="rule" />
        <table>
          <thead>
            <tr>
              <th>{t('receipt.item')}</th>
              <th className="num">{t('receipt.qty')}</th>
              {!thermal && <th className="num">{t('receipt.price')}</th>}
              <th className="num">{t('receipt.amount')}</th>
            </tr>
          </thead>
          <tbody>
            {s.items.map((i) => (
              <tr key={i.id}>
                <td>
                  <UserText>{i.productName}</UserText>
                  {thermal && (
                    <Box sx={{ fontSize: '0.9em' }}>
                      <Ltr>{`${formatNumber(i.quantity, { maxDecimals: 3 })} × ${formatNumber(i.unitPrice, { maxDecimals: 2 })}`}</Ltr>
                      {Number(i.discount) > 0 && <Ltr>{` − ${formatNumber(i.discount, { maxDecimals: 2 })}`}</Ltr>}
                    </Box>
                  )}
                </td>
                <td className="num">
                  <Ltr>{formatNumber(i.quantity, { maxDecimals: 3 })}</Ltr>
                </td>
                {!thermal && (
                  <td className="num">
                    <Ltr>{money(i.unitPrice)}</Ltr>
                  </td>
                )}
                <td className="num">
                  <Ltr>{thermal ? formatNumber(i.lineTotal, { maxDecimals: 2 }) : money(i.lineTotal)}</Ltr>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <Box className="rule" />
        <table>
          <tbody>
            <tr>
              <td>{t('receipt.subtotal')}</td>
              <td className="num">
                <Ltr>{money(s.subtotal)}</Ltr>
              </td>
            </tr>
            {Number(s.itemDiscountTotal) + Number(s.overallDiscount) > 0 && (
              <tr>
                <td>{t('receipt.discount')}</td>
                <td className="num">
                  <Ltr>{money((Number(s.itemDiscountTotal) + Number(s.overallDiscount)).toFixed(2))}</Ltr>
                </td>
              </tr>
            )}
            <tr style={{ fontWeight: 800, fontSize: '1.15em' }}>
              <td>{t('receipt.total')}</td>
              <td className="num">
                <Ltr>{money(s.grandTotal)}</Ltr>
              </td>
            </tr>
            {s.payments
              .filter((p) => p.status === 'POSTED')
              .map((p) => (
                <tr key={p.paymentId}>
                  <td>
                    {t('receipt.paid')} <Ltr>{p.currency !== s.currency ? `(${t('receipt.rate')} ${formatNumber(p.exchangeRate, { maxDecimals: 4 })})` : ''}</Ltr>
                  </td>
                  <td className="num">
                    <Ltr>{formatMoney(p.paidAmount, p.currency, { alwaysDecimals: !thermal })}</Ltr>
                  </td>
                </tr>
              ))}
            {Number(s.balanceDue) > 0 && (
              <tr style={{ fontWeight: 700 }}>
                <td>{t('receipt.due')}</td>
                <td className="num">
                  <Ltr>{money(s.balanceDue)}</Ltr>
                </td>
              </tr>
            )}
          </tbody>
        </table>
        <Box className="rule" />
        <Box sx={{ textAlign: 'center', mt: 1 }}>
          {b.footerNote ? <UserText>{b.footerNote}</UserText> : t('receipt.thanks')}
        </Box>
      </Box>
    </Box>
  );
}
