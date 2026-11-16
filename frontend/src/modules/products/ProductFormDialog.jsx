import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { Alert, Divider, Grid, Typography } from '@mui/material';
import FormDialog from '../../components/form/FormDialog';
import FormTextField from '../../components/form/FormTextField';
import FormSelect from '../../components/form/FormSelect';
import FormMoneyField from '../../components/form/FormMoneyField';
import { catalogService } from '../../services/catalogService';
import { errorMessage } from '../../services/apiClient';
import { useSettings } from '../../contexts/SettingsContext';
import { useUnitLabel } from '../../hooks/useUnitLabel';
import { msg } from '../../utils/validation';
import { applyServerFieldErrors } from '../../utils/serverErrors';

const optionalDecimal = z.string().optional().nullable();
const schema = z
  .object({
    name: z.string().trim().min(1, msg('validation.required')).max(200, msg('validation.maxLength', { max: 200 })),
    code: z.string().max(60, msg('validation.maxLength', { max: 60 })).optional().nullable(),
    categoryId: z.union([z.number(), z.string()]).refine((v) => v !== '' && v !== null, msg('validation.required')),
    unitId: z.union([z.number(), z.string()]).refine((v) => v !== '' && v !== null, msg('validation.required')),
    brand: z.string().max(100, msg('validation.maxLength', { max: 100 })).optional().nullable(),
    description: z.string().max(2000).optional().nullable(),
    minStockLevel: optionalDecimal,
    openingQuantity: optionalDecimal,
    openingUnitCost: optionalDecimal,
  })
  .refine((v) => !v.openingQuantity || Number(v.openingQuantity) === 0 || (v.openingUnitCost !== '' && v.openingUnitCost != null), {
    path: ['openingUnitCost'],
    message: msg('validation.required'),
  });

const empty = { name: '', code: '', categoryId: '', unitId: '', brand: '', description: '', minStockLevel: '', openingQuantity: '', openingUnitCost: '' };

/** Create or edit a product. Opening stock can be entered only when creating. */
export default function ProductFormDialog({ open, product, onClose, onSaved, save }) {
  const { t } = useTranslation();
  const unitLabel = useUnitLabel();
  const { settings } = useSettings();
  const [error, setError] = useState(null);
  const categories = useQuery({ queryKey: ['categories', 'all'], queryFn: () => catalogService.categories(), enabled: open });
  const units = useQuery({ queryKey: ['units', 'all'], queryFn: () => catalogService.units(), enabled: open });
  const { control, handleSubmit, reset, setError: setFieldError, formState } = useForm({ resolver: zodResolver(schema), defaultValues: empty });
  const unitId = useWatch({ control, name: 'unitId' });
  const unit = (units.data ?? []).find((u) => u.id === Number(unitId));
  const qtyDecimals = unit?.allowDecimal ? 3 : 0;

  useEffect(() => {
    if (!open) return;
    setError(null);
    reset(
      product
        ? {
            ...empty,
            name: product.name,
            code: product.code ?? '',
            categoryId: product.category.id,
            unitId: product.unit.id,
            brand: product.brand ?? '',
            description: product.description ?? '',
            minStockLevel: product.minStockLevel ?? '',
          }
        : empty,
    );
  }, [open, product, reset]);

  // Inactive categories/units are offered only if the product already uses them.
  const categoryOptions = (categories.data ?? [])
    .filter((c) => c.active || c.id === product?.category?.id)
    .map((c) => ({ value: c.id, label: c.name }));
  const unitOptions = (units.data ?? []).filter((u) => u.active || u.id === product?.unit?.id).map((u) => ({ value: u.id, label: unitLabel(u) }));

  const submit = handleSubmit(async (v) => {
    setError(null);
    const body = {
      name: v.name,
      code: v.code || null,
      categoryId: Number(v.categoryId),
      unitId: Number(v.unitId),
      brand: v.brand || null,
      description: v.description || null,
      minStockLevel: v.minStockLevel || null,
      ...(product ? { version: product.version } : { openingQuantity: v.openingQuantity || null, openingUnitCost: v.openingUnitCost || null }),
    };
    try {
      await save(body);
      onSaved();
    } catch (e) {
      if (!applyServerFieldErrors(e, setFieldError)) setError(errorMessage(t, e));
    }
  });

  return (
    <FormDialog open={open} title={product ? t('products.editTitle') : t('products.createTitle')} onClose={onClose} onSubmit={submit} submitting={formState.isSubmitting} maxWidth="md">
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}
      <Grid container spacing={2} sx={{ pt: 1 }}>
        <Grid size={{ xs: 12, sm: 8 }}>
          <FormTextField name="name" control={control} label={t('products.name')} autoFocus />
        </Grid>
        <Grid size={{ xs: 12, sm: 4 }}>
          <FormTextField name="code" control={control} label={t('products.code')} helperText={t('products.codeHint')} ltr />
        </Grid>
        <Grid size={{ xs: 12, sm: 4 }}>
          <FormSelect name="categoryId" control={control} label={t('products.category')} options={categoryOptions} emptyOption />
        </Grid>
        <Grid size={{ xs: 12, sm: 4 }}>
          <FormSelect name="unitId" control={control} label={t('products.unit')} options={unitOptions} emptyOption />
        </Grid>
        <Grid size={{ xs: 12, sm: 4 }}>
          <FormTextField name="brand" control={control} label={t('products.brand')} helperText={t('common.optional')} />
        </Grid>
        <Grid size={{ xs: 12, sm: 4 }}>
          <FormMoneyField name="minStockLevel" control={control} label={t('products.minStock')} decimals={qtyDecimals} helperText={t('products.minStockHint')} />
        </Grid>
        <Grid size={{ xs: 12, sm: 8 }}>
          <FormTextField name="description" control={control} label={t('products.description')} multiline minRows={2} helperText={t('common.optional')} />
        </Grid>
        <Grid size={12}>
          <Alert severity="info" variant="outlined">
            {t('products.noPriceNote')}
          </Alert>
        </Grid>
        {!product && (
          <>
            <Grid size={12}>
              <Divider />
              <Typography variant="subtitle2" sx={{ mt: 2 }}>
                {t('products.openingStock')}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {t('products.openingHint', { currency: settings.baseCurrency })}
              </Typography>
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormMoneyField name="openingQuantity" control={control} label={t('products.openingQuantity')} decimals={qtyDecimals} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <FormMoneyField name="openingUnitCost" control={control} label={t('products.openingUnitCost')} currency={settings.baseCurrency} decimals={4} />
            </Grid>
          </>
        )}
      </Grid>
    </FormDialog>
  );
}
