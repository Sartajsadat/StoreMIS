import { useEffect, useMemo, useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { Alert, Box, Checkbox, FormControlLabel, FormGroup, Grid, TextField, Typography } from '@mui/material';
import FormDialog from '../../components/form/FormDialog';
import FormTextField from '../../components/form/FormTextField';
import FormSelect from '../../components/form/FormSelect';
import { msg } from '../../utils/validation';
import { applyServerFieldErrors } from '../../utils/serverErrors';
import { errorMessage } from '../../services/apiClient';

const base = {
  fullName: z.string().trim().min(2, msg('validation.minLength', { min: 2 })).max(150, msg('validation.maxLength', { max: 150 })),
  phone: z.string().max(30, msg('validation.maxLength', { max: 30 })).optional(),
  role: z.enum(['ADMIN', 'ENTRY_USER']),
};
const createSchema = z.object({
  ...base,
  username: z
    .string()
    .trim()
    .min(3, msg('validation.minLength', { min: 3 }))
    .max(50, msg('validation.maxLength', { max: 50 }))
    .regex(/^[A-Za-z0-9._-]+$/, msg('validation.invalid')),
  password: z
    .string()
    .min(8, msg('validation.passwordWeak'))
    .regex(/[A-Za-z]/, msg('validation.passwordWeak'))
    .regex(/\d/, msg('validation.passwordWeak')),
});
const editSchema = z.object(base);

/**
 * Create or edit a user. `user` null = create. `permissionCatalog` = [{ code, defaultForEntryUser }].
 */
export default function UserFormDialog({ open, user, permissionCatalog = [], onClose, onSubmit }) {
  const { t } = useTranslation();
  const isEdit = Boolean(user);
  const [selected, setSelected] = useState([]);
  const [formError, setFormError] = useState(null);
  const defaults = useMemo(
    () => (isEdit ? { fullName: user.fullName, phone: user.phone ?? '', role: user.role } : { username: '', fullName: '', phone: '', role: 'ENTRY_USER', password: '' }),
    [isEdit, user],
  );
  const { control, handleSubmit, reset, setError, formState } = useForm({ resolver: zodResolver(isEdit ? editSchema : createSchema), defaultValues: defaults });
  const role = useWatch({ control, name: 'role' });

  useEffect(() => {
    if (!open) return;
    reset(defaults);
    setFormError(null);
    setSelected(isEdit ? user.permissions : permissionCatalog.filter((p) => p.defaultForEntryUser).map((p) => p.code));
  }, [open, defaults, isEdit, user, permissionCatalog, reset]);

  const toggle = (code) => setSelected((s) => (s.includes(code) ? s.filter((c) => c !== code) : [...s, code]));

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit({ ...values, phone: values.phone || null, permissions: values.role === 'ADMIN' ? [] : selected, ...(isEdit && { version: user.version }) });
    } catch (e) {
      if (!applyServerFieldErrors(e, setError)) setFormError(errorMessage(t, e));
    }
  });

  return (
    <FormDialog open={open} title={isEdit ? t('users.editTitle') : t('users.createTitle')} onClose={onClose} onSubmit={submit} submitting={formState.isSubmitting} maxWidth="md">
      {formError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {formError}
        </Alert>
      )}
      <Grid container spacing={2} sx={{ pt: 1 }}>
        <Grid size={{ xs: 12, sm: 6 }}>
          <FormTextField name="fullName" control={control} label={t('users.fullName')} autoFocus />
        </Grid>
        <Grid size={{ xs: 12, sm: 6 }}>
          {isEdit ? (
            <TextField label={t('users.username')} value={user.username} disabled slotProps={{ htmlInput: { dir: 'ltr' } }} />
          ) : (
            <FormTextField name="username" control={control} label={t('users.username')} helperText={t('users.usernameHint')} autoComplete="off" ltr />
          )}
        </Grid>
        <Grid size={{ xs: 12, sm: 6 }}>
          <FormTextField name="phone" control={control} label={t('users.phone')} ltr inputMode="tel" />
        </Grid>
        <Grid size={{ xs: 12, sm: 6 }}>
          <FormSelect
            name="role"
            control={control}
            label={t('users.role')}
            options={[
              { value: 'ENTRY_USER', label: t('roles.ENTRY_USER') },
              { value: 'ADMIN', label: t('roles.ADMIN') },
            ]}
          />
        </Grid>
        {!isEdit && (
          <Grid size={{ xs: 12, sm: 6 }}>
            <FormTextField name="password" control={control} label={t('users.password')} helperText={t('users.passwordHint')} autoComplete="new-password" ltr />
          </Grid>
        )}
        <Grid size={12}>
          <Typography variant="subtitle2" sx={{ mt: 1 }}>
            {t('users.permissions')}
          </Typography>
          {role === 'ADMIN' ? (
            <Alert severity="info" sx={{ mt: 1 }}>
              {t('users.adminHasAll')}
            </Alert>
          ) : (
            <Box>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                {t('users.permissionsHint')}
              </Typography>
              <FormGroup sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, columnGap: 2 }}>
                {permissionCatalog.map((p) => (
                  <FormControlLabel key={p.code} control={<Checkbox checked={selected.includes(p.code)} onChange={() => toggle(p.code)} />} label={t(`permissions.${p.code}`)} />
                ))}
              </FormGroup>
            </Box>
          )}
        </Grid>
      </Grid>
    </FormDialog>
  );
}
