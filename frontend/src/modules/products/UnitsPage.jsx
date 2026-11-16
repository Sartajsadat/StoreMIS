import { useEffect, useMemo, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { Alert, Box, Button, Card, Chip, IconButton, Stack, TextField, Tooltip } from '@mui/material';
import AddOutlined from '@mui/icons-material/AddOutlined';
import EditOutlined from '@mui/icons-material/EditOutlined';
import ToggleOnOutlined from '@mui/icons-material/ToggleOnOutlined';
import ToggleOffOutlined from '@mui/icons-material/ToggleOffOutlined';
import PageHeader from '../../components/display/PageHeader';
import DataTable from '../../components/data/DataTable';
import StatusChip from '../../components/display/StatusChip';
import Ltr from '../../components/display/Ltr';
import FormDialog from '../../components/form/FormDialog';
import FormTextField from '../../components/form/FormTextField';
import FormSwitch from '../../components/form/FormSwitch';
import { catalogService } from '../../services/catalogService';
import { errorMessage } from '../../services/apiClient';
import { useNotify } from '../../contexts/NotificationContext';
import { useUnitLabel } from '../../hooks/useUnitLabel';
import { msg } from '../../utils/validation';
import { applyServerFieldErrors } from '../../utils/serverErrors';

const schema = z.object({
  name: z.string().trim().min(1, msg('validation.required')).max(50, msg('validation.maxLength', { max: 50 })),
  symbol: z.string().max(10, msg('validation.maxLength', { max: 10 })).optional(),
  allowDecimal: z.boolean(),
});

function UnitDialog({ open, unit, onClose, onSaved }) {
  const { t } = useTranslation();
  const unitLabel = useUnitLabel();
  const [error, setError] = useState(null);
  const { control, handleSubmit, reset, setError: setFieldError, formState } = useForm({ resolver: zodResolver(schema) });
  useEffect(() => {
    if (open) {
      reset({ name: unit?.name ?? '', symbol: unit?.symbol ?? '', allowDecimal: unit?.allowDecimal ?? false });
      setError(null);
    }
  }, [open, unit, reset]);
  const submit = handleSubmit(async (values) => {
    try {
      if (unit) await catalogService.updateUnit(unit.id, { ...values, version: unit.version });
      else await catalogService.createUnit(values);
      onSaved();
    } catch (e) {
      if (!applyServerFieldErrors(e, setFieldError)) setError(errorMessage(t, e));
    }
  });
  const isSystem = Boolean(unit?.code);
  return (
    <FormDialog open={open} title={unit ? t('units.editTitle') : t('units.createTitle')} onClose={onClose} onSubmit={submit} submitting={formState.isSubmitting}>
      <Stack spacing={2} sx={{ pt: 1 }}>
        {error && <Alert severity="error">{error}</Alert>}
        {isSystem ? (
          <TextField label={t('units.name')} value={unitLabel(unit)} disabled helperText={t('units.systemHint')} />
        ) : (
          <FormTextField name="name" control={control} label={t('units.name')} autoFocus />
        )}
        <FormTextField name="symbol" control={control} label={t('units.symbol')} ltr helperText={t('common.optional')} />
        <Box>
          <FormSwitch name="allowDecimal" control={control} label={t('units.allowDecimal')} />
          <Box sx={{ color: 'text.secondary', fontSize: '0.8rem' }}>{t('units.allowDecimalHint')}</Box>
        </Box>
      </Stack>
    </FormDialog>
  );
}

export default function UnitsPage() {
  const { t } = useTranslation();
  const notify = useNotify();
  const unitLabel = useUnitLabel();
  const queryClient = useQueryClient();
  const [dialog, setDialog] = useState({ open: false, unit: null });
  const list = useQuery({ queryKey: ['units', 'all'], queryFn: () => catalogService.units() });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['units'] });

  const toggle = async (u) => {
    try {
      await catalogService.setUnitActive(u.id, !u.active);
      notify.success(t('units.statusChanged'));
      refresh();
    } catch (e) {
      notify.error(errorMessage(t, e));
    }
  };

  const columns = useMemo(
    () => [
      {
        field: 'name',
        headerName: t('units.name'),
        flex: 1,
        minWidth: 180,
        renderCell: (p) => (
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', height: '100%' }}>
            <span>{unitLabel(p.row)}</span>
            {p.row.code && <Chip size="small" variant="outlined" label={t('units.system')} />}
          </Stack>
        ),
      },
      { field: 'symbol', headerName: t('units.symbol'), width: 120, renderCell: (p) => <Ltr>{p.value}</Ltr> },
      { field: 'allowDecimal', headerName: t('units.allowDecimal'), width: 200, renderCell: (p) => (p.value ? t('units.decimal') : t('units.whole')) },
      { field: 'active', headerName: t('common.status'), width: 130, renderCell: (p) => <StatusChip status={p.value ? 'ACTIVE' : 'INACTIVE'} /> },
      {
        field: 'actions',
        headerName: '',
        width: 110,
        sortable: false,
        renderCell: (p) => (
          <Stack direction="row" sx={{ alignItems: 'center', height: '100%' }}>
            <Tooltip title={t('common.edit')}>
              <IconButton size="small" onClick={() => setDialog({ open: true, unit: p.row })} aria-label={t('common.edit')}>
                <EditOutlined fontSize="small" />
              </IconButton>
            </Tooltip>
            <Tooltip title={p.row.active ? t('categories.deactivate') : t('categories.activate')}>
              <IconButton size="small" onClick={() => toggle(p.row)} color={p.row.active ? 'default' : 'success'} aria-label={p.row.active ? t('categories.deactivate') : t('categories.activate')}>
                {p.row.active ? <ToggleOnOutlined fontSize="small" /> : <ToggleOffOutlined fontSize="small" />}
              </IconButton>
            </Tooltip>
          </Stack>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, unitLabel],
  );

  return (
    <>
      <PageHeader
        title={t('units.title')}
        subtitle={t('units.subtitle')}
        actions={
          <Button variant="contained" startIcon={<AddOutlined />} onClick={() => setDialog({ open: true, unit: null })}>
            {t('units.add')}
          </Button>
        }
      />
      <Card>
        <Box sx={{ p: 1 }}>
          <DataTable rows={list.data} columns={columns} loading={list.isFetching} />
        </Box>
      </Card>
      <UnitDialog
        open={dialog.open}
        unit={dialog.unit}
        onClose={() => setDialog({ open: false, unit: null })}
        onSaved={() => {
          notify.success(t('units.saved'));
          setDialog({ open: false, unit: null });
          refresh();
        }}
      />
    </>
  );
}
