import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { Alert, Grid } from '@mui/material';
import FormDialog from '../../components/form/FormDialog';
import FormTextField from '../../components/form/FormTextField';
import { supplierService } from '../../services/supplierService';
import { errorMessage } from '../../services/apiClient';
import { msg } from '../../utils/validation';
import { applyServerFieldErrors } from '../../utils/serverErrors';

const schema = z.object({
  name: z.string().trim().min(1, msg('validation.required')).max(150, msg('validation.maxLength', { max: 150 })),
  phone: z.string().max(30, msg('validation.maxLength', { max: 30 })).optional(),
  contactPerson: z.string().max(150).optional(),
  address: z.string().max(300).optional(),
  notes: z.string().max(2000).optional(),
});

export default function SupplierFormDialog({ open, supplier, onClose, onSaved }) {
  const { t } = useTranslation();
  const [error, setError] = useState(null);
  const { control, handleSubmit, reset, setError: setFieldError, formState } = useForm({ resolver: zodResolver(schema) });
  useEffect(() => {
    if (open) {
      setError(null);
      reset({ name: supplier?.name ?? '', phone: supplier?.phone ?? '', contactPerson: supplier?.contactPerson ?? '', address: supplier?.address ?? '', notes: supplier?.notes ?? '' });
    }
  }, [open, supplier, reset]);
  const submit = handleSubmit(async (v) => {
    try {
      const saved = supplier ? await supplierService.update(supplier.id, { ...v, version: supplier.version }) : await supplierService.create(v);
      onSaved(saved);
    } catch (e) {
      if (!applyServerFieldErrors(e, setFieldError)) setError(errorMessage(t, e));
    }
  });
  return (
    <FormDialog open={open} title={supplier ? t('suppliers.editTitle') : t('suppliers.createTitle')} onClose={onClose} onSubmit={submit} submitting={formState.isSubmitting}>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}
      <Grid container spacing={2} sx={{ pt: 1 }}>
        <Grid size={12}>
          <FormTextField name="name" control={control} label={t('suppliers.name')} autoFocus />
        </Grid>
        <Grid size={{ xs: 12, sm: 6 }}>
          <FormTextField name="phone" control={control} label={t('suppliers.phone')} ltr inputMode="tel" />
        </Grid>
        <Grid size={{ xs: 12, sm: 6 }}>
          <FormTextField name="contactPerson" control={control} label={t('suppliers.contactPerson')} />
        </Grid>
        <Grid size={12}>
          <FormTextField name="address" control={control} label={t('suppliers.address')} />
        </Grid>
        <Grid size={12}>
          <FormTextField name="notes" control={control} label={t('suppliers.notes')} multiline minRows={2} helperText={t('common.optional')} />
        </Grid>
      </Grid>
    </FormDialog>
  );
}
