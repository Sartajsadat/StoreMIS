import { useState } from 'react';
import { Box, IconButton, Table, TableBody, TableCell, TableHead, TableRow, Tooltip } from '@mui/material';
import TableChartOutlined from '@mui/icons-material/TableChartOutlined';
import InsertChartOutlined from '@mui/icons-material/InsertChartOutlined';
import { useTranslation } from 'react-i18next';
import SectionCard from '../display/SectionCard';
import EmptyState from '../feedback/EmptyState';
import Ltr from '../display/Ltr';

/**
 * Card around a chart with a built-in table view (accessibility + exact numbers).
 * table: { columns: [{ key, label, format? }], rows: [...] }
 */
export default function ChartCard({ title, subheader, height = 280, table, empty, children, action }) {
  const { t } = useTranslation();
  const [asTable, setAsTable] = useState(false);
  const toggle = table && (
    <Tooltip title={asTable ? t('common.showChart') : t('common.showTable')}>
      <IconButton size="small" onClick={() => setAsTable((v) => !v)} aria-label={asTable ? t('common.showChart') : t('common.showTable')}>
        {asTable ? <InsertChartOutlined fontSize="small" /> : <TableChartOutlined fontSize="small" />}
      </IconButton>
    </Tooltip>
  );
  return (
    <SectionCard
      title={title}
      subheader={subheader}
      action={
        <Box sx={{ display: 'flex', gap: 0.5 }}>
          {action}
          {toggle}
        </Box>
      }
    >
      {empty ? (
        <EmptyState title={t('common.noData')} dense />
      ) : asTable && table ? (
        <Box sx={{ maxHeight: height, overflow: 'auto' }}>
          <Table size="small" stickyHeader>
            <TableHead>
              <TableRow>
                {table.columns.map((c) => (
                  <TableCell key={c.key}>{c.label}</TableCell>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {table.rows.map((r, i) => (
                <TableRow key={i}>
                  {table.columns.map((c) => (
                    <TableCell key={c.key}>{c.format ? <Ltr>{c.format(r[c.key], r)}</Ltr> : r[c.key]}</TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Box>
      ) : (
        <Box sx={{ height, width: '100%', direction: 'ltr' }}>{children}</Box>
      )}
    </SectionCard>
  );
}
