import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { Alert, Grid } from '@mui/material';
import FormDialog from '../../components/form/FormDialog';
import FormTextField from '../../components/form/FormTextField';
import FormSelect from '../../components/form/FormSelect';
import { customerService } from '../../services/customerService';
import { errorMessage } from '../../services/apiClient';
import { msg } from '../../utils/validation';
import { applyServerFieldErrors } from '../../utils/serverErrors';

const schema = z.object({
  name: z.string().trim().min(1, msg('validation.required')).max(150, msg('validation.maxLength', { max: 150 })),
  phone: z.string().max(30, msg('validation.maxLength', { max: 30 })).optional(),
  address: z.string().max(300).optional(),
  customerType: z.enum(['RETAIL', 'WHOLESALE']),
  notes: z.string().max(2000).optional(),
});

export default function CustomerFormDialog({ open, customer, initialName, onClose, onSaved }) {
  const { t } = useTranslation();
  const [error, setError] = useState(null);
  const { control, handleSubmit, reset, setError: setFieldError, formState } = useForm({ resolver: zodResolver(schema) });
  useEffect(() => {
    if (open) {
      setError(null);
      reset({ name: customer?.name ?? initialName ?? '', phone: customer?.phone ?? '', address: customer?.address ?? '', customerType: customer?.customerType ?? 'RETAIL', notes: customer?.notes ?? '' });
    }
  }, [open, customer, initialName, reset]);
  const submit = handleSubmit(async (v) => {
    try {
      const saved = customer ? await customerService.update(customer.id, { ...v, version: customer.version }) : await customerService.create(v);
      onSaved(saved);
    } catch (e) {
      if (!applyServerFieldErrors(e, setFieldError)) setError(errorMessage(t, e));
    }
  });
  return (
    <FormDialog open={open} title={customer ? t('customers.editTitle') : t('customers.createTitle')} onClose={onClose} onSubmit={submit} submitting={formState.isSubmitting}>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}
      <Grid container spacing={2} sx={{ pt: 1 }}>
        <Grid size={12}>
          <FormTextField name="name" control={control} label={t('customers.name')} autoFocus disabled={customer?.walkIn} />
        </Grid>
        <Grid size={{ xs: 12, sm: 6 }}>
          <FormTextField name="phone" control={control} label={t('customers.phone')} ltr inputMode="tel" />
        </Grid>
        <Grid size={{ xs: 12, sm: 6 }}>
          <FormSelect
            name="customerType"
            control={control}
            label={t('customers.type')}
            options={[
              { value: 'RETAIL', label: t('saleTypes.RETAIL') },
              { value: 'WHOLESALE', label: t('saleTypes.WHOLESALE') },
            ]}
          />
        </Grid>
        <Grid size={12}>
          <FormTextField name="address" control={control} label={t('customers.address')} />
        </Grid>
        <Grid size={12}>
          <FormTextField name="notes" control={control} label={t('customers.notes')} multiline minRows={2} helperText={t('common.optional')} />
        </Grid>
      </Grid>
    </FormDialog>
  );
}
