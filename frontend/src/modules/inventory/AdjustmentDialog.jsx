import { useEffect, useMemo, useState } from 'react';
import { useForm, useWatch, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { Alert, Grid, Stack, Typography } from '@mui/material';
import FormDialog from '../../components/form/FormDialog';
import FormSelect from '../../components/form/FormSelect';
import FormMoneyField from '../../components/form/FormMoneyField';
import FormTextField from '../../components/form/FormTextField';
import ProductPicker from '../../components/inventory/ProductPicker';
import QtyText from '../../components/display/QtyText';
import { inventoryService, ADJUSTMENT_TYPES } from '../../services/inventoryService';
import { errorMessage } from '../../services/apiClient';
import { useAuth } from '../../contexts/AuthContext';
import { useSettings } from '../../contexts/SettingsContext';
import { useUnitLabel } from '../../hooks/useUnitLabel';
import { formatMoney } from '../../utils/format';
import { msg, translateValidation } from '../../utils/validation';
import { uuid } from '../../utils/uuid';

const schema = z
  .object({
    product: z.any().refine((p) => Boolean(p?.id), msg('validation.required')),
    type: z.enum(['OPENING', 'INCREASE', 'DECREASE', 'DAMAGE']),
    quantity: z.string().min(1, msg('validation.required')).refine((v) => Number(v) > 0, msg('validation.positive')),
    unitCost: z.string().optional().nullable(),
    reason: z.string().trim().min(3, msg('validation.minLength', { min: 3 })).max(300, msg('validation.maxLength', { max: 300 })),
  })
  .refine((v) => v.type !== 'OPENING' || (v.unitCost !== '' && v.unitCost != null), { path: ['unitCost'], message: msg('validation.required') });

export default function AdjustmentDialog({ open, onClose, onSaved, initialProduct }) {
  const { t } = useTranslation();
  const { isAdmin, hasPermission } = useAuth();
  const { settings } = useSettings();
  const unitLabel = useUnitLabel();
  const [error, setError] = useState(null);
  const [requestId, setRequestId] = useState(uuid);
  const { control, handleSubmit, reset, formState } = useForm({
    resolver: zodResolver(schema),
    defaultValues: { product: null, type: 'DECREASE', quantity: '', unitCost: '', reason: '' },
  });
  const product = useWatch({ control, name: 'product' });
  const type = useWatch({ control, name: 'type' });
  const showCost = hasPermission('VIEW_COST_PRICES');

  useEffect(() => {
    if (open) {
      reset({ product: initialProduct ?? null, type: 'DECREASE', quantity: '', unitCost: '', reason: '' });
      setError(null);
      setRequestId(uuid()); // one id per dialog session: a double click or retry never posts twice
    }
  }, [open, initialProduct, reset]);

  const typeOptions = useMemo(
    () => ADJUSTMENT_TYPES.filter((x) => x !== 'OPENING' || isAdmin).map((x) => ({ value: x, label: t(`inventory.adjustmentTypes.${x}`) })),
    [t, isAdmin],
  );
  const inbound = type === 'OPENING' || type === 'INCREASE';
  const decimals = product?.unit?.allowDecimal ? 3 : 0;

  const submit = handleSubmit(async (v) => {
    setError(null);
    try {
      const saved = await inventoryService.createAdjustment({
        productId: v.product.id,
        type: v.type,
        quantity: v.quantity,
        unitCost: inbound && v.unitCost ? v.unitCost : null,
        reason: v.reason,
        clientRequestId: requestId,
      });
      onSaved(saved);
    } catch (e) {
      setError(errorMessage(t, e));
    }
  });

  return (
    <FormDialog open={open} title={t('inventory.newAdjustment')} onClose={onClose} onSubmit={submit} submitting={formState.isSubmitting} submitLabel={t('common.save')}>
      <Stack spacing={2} sx={{ pt: 1 }}>
        {error && <Alert severity="error">{error}</Alert>}
        <Controller
          name="product"
          control={control}
          render={({ field, fieldState }) => (
            <ProductPicker value={field.value} onChange={field.onChange} autoFocus={!initialProduct} error={Boolean(fieldState.error)} helperText={fieldState.error ? translateValidation(t, fieldState.error.message) : undefined} />
          )}
        />
        {product && (
          <Stack direction="row" spacing={2} sx={{ color: 'text.secondary', fontSize: '0.85rem' }}>
            <span>
              {t('inventory.current', { qty: '' })}
              <QtyText value={product.stock?.quantity} unit={unitLabel(product.unit)} />
            </span>
            {showCost && product.stock?.avgCost != null && <span>{t('inventory.currentCost', { cost: formatMoney(product.stock.avgCost, settings.baseCurrency) })}</span>}
          </Stack>
        )}
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, sm: 6 }}>
            <FormSelect name="type" control={control} label={t('inventory.adjustmentType')} options={typeOptions} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <FormMoneyField name="quantity" control={control} label={t('inventory.quantity')} decimals={decimals} />
          </Grid>
        </Grid>
        {inbound ? (
          <FormMoneyField
            name="unitCost"
            control={control}
            label={t('inventory.unitCost')}
            currency={settings.baseCurrency}
            decimals={4}
            helperText={type === 'OPENING' ? t('inventory.unitCostHintOpening', { currency: settings.baseCurrency }) : t('inventory.unitCostHintIncrease')}
          />
        ) : (
          <Typography variant="caption" color="text.secondary">
            {t('inventory.outboundCostNote')}
          </Typography>
        )}
        <FormTextField name="reason" control={control} label={t('inventory.reason')} helperText={t('inventory.reasonHint')} multiline minRows={2} />
      </Stack>
    </FormDialog>
  );
}
