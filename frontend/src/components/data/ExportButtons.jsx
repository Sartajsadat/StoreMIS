import { Button, ButtonGroup } from '@mui/material';
import PictureAsPdfOutlined from '@mui/icons-material/PictureAsPdfOutlined';
import TableViewOutlined from '@mui/icons-material/TableViewOutlined';
import PrintOutlined from '@mui/icons-material/PrintOutlined';
import { useTranslation } from 'react-i18next';

/** PDF / Excel / Print buttons for reports. Each handler is optional; missing ones are hidden. */
export default function ExportButtons({ onPdf, onExcel, onPrint = () => window.print(), disabled }) {
  const { t } = useTranslation();
  return (
    <ButtonGroup variant="outlined" size="small" disabled={disabled} sx={{ '@media print': { display: 'none' } }}>
      {onPdf && (
        <Button startIcon={<PictureAsPdfOutlined />} onClick={onPdf}>
          {t('common.exportPdf')}
        </Button>
      )}
      {onExcel && (
        <Button startIcon={<TableViewOutlined />} onClick={onExcel}>
          {t('common.exportExcel')}
        </Button>
      )}
      {onPrint && (
        <Button startIcon={<PrintOutlined />} onClick={onPrint}>
          {t('common.print')}
        </Button>
      )}
    </ButtonGroup>
  );
}
