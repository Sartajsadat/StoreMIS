import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, List, ListItem, ListItemText, Stack, Switch, TextField, Tooltip } from '@mui/material';
import EditOutlined from '@mui/icons-material/EditOutlined';
import CheckOutlined from '@mui/icons-material/CheckOutlined';
import { expenseService } from '../../services/expenseService';
import { errorMessage } from '../../services/apiClient';
import UserText from '../../components/display/UserText';
import { categoryLabel } from './expenseUtils';

/** Admin: add, rename and switch off expense categories. */
export default function ExpenseCategoriesDialog({ open, onClose }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const list = useQuery({ queryKey: ['expense-categories', 'all'], queryFn: () => expenseService.categories({}), enabled: open });
  const [name, setName] = useState('');
  const [editing, setEditing] = useState(null);
  const [error, setError] = useState(null);
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['expense-categories'] });
  const run = async (fn) => {
    setError(null);
    try {
      await fn();
      refresh();
      return true;
    } catch (e) {
      setError(errorMessage(t, e));
      return false;
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{t('expenses.categoriesTitle')}</DialogTitle>
      <DialogContent dividers>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        <Stack
          direction="row"
          spacing={1}
          component="form"
          onSubmit={async (e) => {
            e.preventDefault();
            if (name.trim() && (await run(() => expenseService.createCategory({ name: name.trim() })))) setName('');
          }}
        >
          <TextField size="small" label={t('expenses.newCategory')} value={name} onChange={(e) => setName(e.target.value)} />
          <Button type="submit" variant="contained" disabled={!name.trim()}>
            {t('common.add')}
          </Button>
        </Stack>
        <List dense sx={{ mt: 1 }}>
          {(list.data ?? []).map((c) => (
            <ListItem
              key={c.id}
              secondaryAction={
                <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
                  {!c.code && editing?.id !== c.id && (
                    <Tooltip title={t('common.edit')}>
                      <IconButton size="small" onClick={() => setEditing({ id: c.id, name: c.name, version: c.version })} aria-label={t('common.edit')}>
                        <EditOutlined fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  )}
                  <Switch size="small" checked={c.active} onChange={(e) => run(() => expenseService.setCategoryActive(c.id, e.target.checked))} slotProps={{ input: { 'aria-label': t('common.active') } }} />
                </Stack>
              }
            >
              {editing?.id === c.id ? (
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center', pr: 10 }}>
                  <TextField size="small" value={editing.name} onChange={(e) => setEditing({ ...editing, name: e.target.value })} autoFocus />
                  <IconButton
                    size="small"
                    aria-label={t('common.save')}
                    onClick={async () => {
                      if (await run(() => expenseService.updateCategory(c.id, { name: editing.name.trim(), version: editing.version }))) setEditing(null);
                    }}
                  >
                    <CheckOutlined fontSize="small" />
                  </IconButton>
                </Stack>
              ) : (
                <ListItemText primary={c.code ? categoryLabel(t, c) : <UserText>{c.name}</UserText>} secondary={c.active ? null : t('common.inactive')} />
              )}
            </ListItem>
          ))}
        </List>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('common.close')}</Button>
      </DialogActions>
    </Dialog>
  );
}
