import { useEffect, useMemo, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { Alert, Box, Button, Card, IconButton, Stack, Tooltip } from '@mui/material';
import AddOutlined from '@mui/icons-material/AddOutlined';
import EditOutlined from '@mui/icons-material/EditOutlined';
import ToggleOnOutlined from '@mui/icons-material/ToggleOnOutlined';
import ToggleOffOutlined from '@mui/icons-material/ToggleOffOutlined';
import PageHeader from '../../components/display/PageHeader';
import DataTable from '../../components/data/DataTable';
import FilterBar from '../../components/data/FilterBar';
import StatusChip from '../../components/display/StatusChip';
import UserText from '../../components/display/UserText';
import FormDialog from '../../components/form/FormDialog';
import FormTextField from '../../components/form/FormTextField';
import { catalogService } from '../../services/catalogService';
import { errorMessage } from '../../services/apiClient';
import { useNotify } from '../../contexts/NotificationContext';
import { msg } from '../../utils/validation';
import { applyServerFieldErrors } from '../../utils/serverErrors';

const schema = z.object({
  name: z.string().trim().min(1, msg('validation.required')).max(100, msg('validation.maxLength', { max: 100 })),
  description: z.string().max(300, msg('validation.maxLength', { max: 300 })).optional(),
});

function CategoryDialog({ open, category, onClose, onSaved }) {
  const { t } = useTranslation();
  const [error, setError] = useState(null);
  const { control, handleSubmit, reset, setError: setFieldError, formState } = useForm({ resolver: zodResolver(schema) });
  useEffect(() => {
    if (open) {
      reset({ name: category?.name ?? '', description: category?.description ?? '' });
      setError(null);
    }
  }, [open, category, reset]);
  const submit = handleSubmit(async (values) => {
    try {
      if (category) await catalogService.updateCategory(category.id, { ...values, version: category.version });
      else await catalogService.createCategory(values);
      onSaved();
    } catch (e) {
      if (!applyServerFieldErrors(e, setFieldError)) setError(errorMessage(t, e));
    }
  });
  return (
    <FormDialog open={open} title={category ? t('categories.editTitle') : t('categories.createTitle')} onClose={onClose} onSubmit={submit} submitting={formState.isSubmitting}>
      <Stack spacing={2} sx={{ pt: 1 }}>
        {error && <Alert severity="error">{error}</Alert>}
        <FormTextField name="name" control={control} label={t('categories.name')} autoFocus />
        <FormTextField name="description" control={control} label={t('categories.description')} multiline minRows={2} helperText={t('common.optional')} />
      </Stack>
    </FormDialog>
  );
}

export default function CategoriesPage() {
  const { t } = useTranslation();
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [dialog, setDialog] = useState({ open: false, category: null });
  const list = useQuery({ queryKey: ['categories', 'all'], queryFn: () => catalogService.categories() });

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['categories'] });
  const toggle = async (c) => {
    try {
      await catalogService.setCategoryActive(c.id, !c.active);
      notify.success(t('categories.statusChanged'));
      refresh();
    } catch (e) {
      notify.error(errorMessage(t, e));
    }
  };

  const columns = useMemo(
    () => [
      { field: 'name', headerName: t('categories.name'), flex: 1, minWidth: 180, renderCell: (p) => <UserText>{p.value}</UserText> },
      { field: 'description', headerName: t('categories.description'), flex: 1.5, minWidth: 200, renderCell: (p) => <UserText>{p.value}</UserText> },
      { field: 'productCount', headerName: t('categories.products'), width: 120, type: 'number' },
      { field: 'active', headerName: t('common.status'), width: 130, renderCell: (p) => <StatusChip status={p.value ? 'ACTIVE' : 'INACTIVE'} /> },
      {
        field: 'actions',
        headerName: '',
        width: 110,
        sortable: false,
        renderCell: (p) => (
          <Stack direction="row" sx={{ alignItems: 'center', height: '100%' }}>
            <Tooltip title={t('common.edit')}>
              <IconButton size="small" onClick={() => setDialog({ open: true, category: p.row })} aria-label={t('common.edit')}>
                <EditOutlined fontSize="small" />
              </IconButton>
            </Tooltip>
            <Tooltip title={p.row.active ? `${t('categories.deactivate')} — ${t('categories.deactivateHint')}` : t('categories.activate')}>
              <IconButton size="small" onClick={() => toggle(p.row)} color={p.row.active ? 'default' : 'success'} aria-label={p.row.active ? t('categories.deactivate') : t('categories.activate')}>
                {p.row.active ? <ToggleOnOutlined fontSize="small" /> : <ToggleOffOutlined fontSize="small" />}
              </IconButton>
            </Tooltip>
          </Stack>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  const rows = (list.data ?? []).filter((c) => !search || `${c.name} ${c.description ?? ''}`.toLowerCase().includes(search.toLowerCase()));

  return (
    <>
      <PageHeader
        title={t('categories.title')}
        subtitle={t('categories.subtitle')}
        actions={
          <Button variant="contained" startIcon={<AddOutlined />} onClick={() => setDialog({ open: true, category: null })}>
            {t('categories.add')}
          </Button>
        }
      />
      <Card>
        <FilterBar search={search} onSearchChange={setSearch} />
        <Box sx={{ px: 1, pb: 1 }}>
          <DataTable rows={rows} columns={columns} loading={list.isFetching} />
        </Box>
      </Card>
      <CategoryDialog
        open={dialog.open}
        category={dialog.category}
        onClose={() => setDialog({ open: false, category: null })}
        onSaved={() => {
          notify.success(t('categories.saved'));
          setDialog({ open: false, category: null });
          refresh();
        }}
      />
    </>
  );
}
