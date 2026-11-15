import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient, keepPreviousData } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Box, Button, Card, Chip, IconButton, ListItemIcon, Menu, MenuItem, Stack, TextField, Tooltip, Typography } from '@mui/material';
import PersonAddAlt1Outlined from '@mui/icons-material/PersonAddAlt1Outlined';
import MoreVert from '@mui/icons-material/MoreVert';
import EditOutlined from '@mui/icons-material/EditOutlined';
import KeyOutlined from '@mui/icons-material/KeyOutlined';
import BlockOutlined from '@mui/icons-material/BlockOutlined';
import CheckCircleOutline from '@mui/icons-material/CheckCircleOutline';
import LockClockOutlined from '@mui/icons-material/LockClockOutlined';
import PageHeader from '../../components/display/PageHeader';
import DataTable from '../../components/data/DataTable';
import FilterBar from '../../components/data/FilterBar';
import StatusChip from '../../components/display/StatusChip';
import Ltr from '../../components/display/Ltr';
import ConfirmDialog from '../../components/feedback/ConfirmDialog';
import UserFormDialog from './UserFormDialog';
import ResetPasswordDialog from './ResetPasswordDialog';
import { userService } from '../../services/userService';
import { errorMessage } from '../../services/apiClient';
import { useNotify } from '../../contexts/NotificationContext';
import { useAuth } from '../../contexts/AuthContext';
import { useDebounce } from '../../hooks/useDebounce';
import { formatDateTime } from '../../utils/format';

export default function UsersPage() {
  const { t } = useTranslation();
  const notify = useNotify();
  const { user: me } = useAuth();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [role, setRole] = useState('');
  const [active, setActive] = useState('');
  const [pagination, setPagination] = useState({ page: 0, pageSize: 20 });
  const [dialog, setDialog] = useState({ type: null, user: null });
  const [menu, setMenu] = useState({ anchor: null, user: null });
  const q = useDebounce(search, 300);

  const params = { q: q || undefined, role: role || undefined, active: active === '' ? undefined : active, page: pagination.page, size: pagination.pageSize };
  const usersQuery = useQuery({ queryKey: ['users', params], queryFn: () => userService.list(params), placeholderData: keepPreviousData });
  const permissionsQuery = useQuery({ queryKey: ['permissions'], queryFn: userService.permissions, staleTime: Infinity });

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['users'] });
  const close = () => setDialog({ type: null, user: null });

  const statusMutation = useMutation({
    mutationFn: ({ id, value }) => userService.setActive(id, value),
    onSuccess: () => {
      notify.success(t('users.statusChanged'));
      close();
      refresh();
    },
    onError: (e) => notify.error(errorMessage(t, e)),
  });

  const columns = useMemo(
    () => [
      {
        field: 'fullName',
        headerName: t('users.fullName'),
        flex: 1.3,
        minWidth: 180,
        renderCell: (p) => (
          <Stack sx={{ justifyContent: 'center', height: '100%', lineHeight: 1.3 }}>
            <Typography variant="body2" sx={{ fontWeight: 600 }}>
              {p.value}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              <Ltr>{p.row.username}</Ltr>
            </Typography>
          </Stack>
        ),
      },
      { field: 'phone', headerName: t('users.phone'), width: 150, renderCell: (p) => <Ltr>{p.value}</Ltr> },
      {
        field: 'role',
        headerName: t('users.role'),
        width: 170,
        renderCell: (p) => <Chip size="small" variant="outlined" color={p.value === 'ADMIN' ? 'secondary' : 'default'} label={t(`roles.${p.value}`)} />,
      },
      {
        field: 'permissions',
        headerName: t('users.permissions'),
        flex: 1,
        minWidth: 150,
        sortable: false,
        renderCell: (p) =>
          p.row.role === 'ADMIN' ? (
            <Typography variant="caption" color="text.secondary">
              {t('common.all')}
            </Typography>
          ) : (
            <Tooltip title={p.value.map((c) => t(`permissions.${c}`)).join(' · ')}>
              <span>{t('users.permissionCount', { count: p.value.length })}</span>
            </Tooltip>
          ),
      },
      {
        field: 'active',
        headerName: t('common.status'),
        width: 170,
        renderCell: (p) => (
          <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', height: '100%' }}>
            <StatusChip status={p.value ? 'ACTIVE' : 'INACTIVE'} />
            {p.row.locked && (
              <Tooltip title={t('users.locked')}>
                <LockClockOutlined fontSize="small" color="warning" />
              </Tooltip>
            )}
          </Stack>
        ),
      },
      { field: 'lastLoginAt', headerName: t('users.lastLogin'), width: 170, renderCell: (p) => (p.value ? <Ltr>{formatDateTime(p.value)}</Ltr> : t('users.never')) },
      {
        field: 'actions',
        headerName: '',
        width: 64,
        sortable: false,
        align: 'center',
        renderCell: (p) => (
          <IconButton size="small" aria-label={t('common.actions')} onClick={(e) => setMenu({ anchor: e.currentTarget, user: p.row })}>
            <MoreVert fontSize="small" />
          </IconButton>
        ),
      },
    ],
    [t],
  );

  const target = dialog.user;
  const data = usersQuery.data;

  return (
    <>
      <PageHeader
        title={t('users.title')}
        subtitle={t('users.subtitle')}
        actions={
          <Button variant="contained" startIcon={<PersonAddAlt1Outlined />} onClick={() => setDialog({ type: 'form', user: null })}>
            {t('users.add')}
          </Button>
        }
      />
      <Card>
        <FilterBar
          search={search}
          onSearchChange={(v) => {
            setSearch(v);
            setPagination((p) => ({ ...p, page: 0 }));
          }}
          onReset={() => {
            setSearch('');
            setRole('');
            setActive('');
          }}
        >
          <TextField select value={role} onChange={(e) => setRole(e.target.value)} label={t('users.role')} sx={{ width: 180 }} fullWidth={false}>
            <MenuItem value="">{t('users.allRoles')}</MenuItem>
            <MenuItem value="ADMIN">{t('roles.ADMIN')}</MenuItem>
            <MenuItem value="ENTRY_USER">{t('roles.ENTRY_USER')}</MenuItem>
          </TextField>
          <TextField select value={active} onChange={(e) => setActive(e.target.value)} label={t('common.status')} sx={{ width: 160 }} fullWidth={false}>
            <MenuItem value="">{t('users.allStatuses')}</MenuItem>
            <MenuItem value="true">{t('common.active')}</MenuItem>
            <MenuItem value="false">{t('common.inactive')}</MenuItem>
          </TextField>
        </FilterBar>
        <Box sx={{ px: 1, pb: 1 }}>
          <DataTable
            server
            rows={data?.content}
            rowCount={data?.totalElements}
            columns={columns}
            loading={usersQuery.isFetching}
            paginationModel={pagination}
            onPaginationModelChange={setPagination}
            rowHeight={60}
          />
        </Box>
      </Card>

      <Menu anchorEl={menu.anchor} open={Boolean(menu.anchor)} onClose={() => setMenu({ anchor: null, user: null })}>
        <MenuItem
          onClick={() => {
            setDialog({ type: 'form', user: menu.user });
            setMenu({ anchor: null, user: null });
          }}
        >
          <ListItemIcon>
            <EditOutlined fontSize="small" />
          </ListItemIcon>
          {t('common.edit')}
        </MenuItem>
        <MenuItem
          onClick={() => {
            setDialog({ type: 'reset', user: menu.user });
            setMenu({ anchor: null, user: null });
          }}
        >
          <ListItemIcon>
            <KeyOutlined fontSize="small" />
          </ListItemIcon>
          {t('users.resetPassword')}
        </MenuItem>
        {menu.user && menu.user.id !== me?.id && (
          <MenuItem
            onClick={() => {
              setDialog({ type: 'status', user: menu.user });
              setMenu({ anchor: null, user: null });
            }}
            sx={{ color: menu.user.active ? 'error.main' : 'success.main' }}
          >
            <ListItemIcon sx={{ color: 'inherit' }}>{menu.user.active ? <BlockOutlined fontSize="small" /> : <CheckCircleOutline fontSize="small" />}</ListItemIcon>
            {menu.user.active ? t('users.deactivate') : t('users.activate')}
          </MenuItem>
        )}
      </Menu>

      <UserFormDialog
        open={dialog.type === 'form'}
        user={target}
        permissionCatalog={permissionsQuery.data ?? []}
        onClose={close}
        onSubmit={async (values) => {
          if (target) await userService.update(target.id, values);
          else await userService.create(values);
          notify.success(target ? t('users.updated') : t('users.created'));
          close();
          refresh();
        }}
      />
      <ResetPasswordDialog
        open={dialog.type === 'reset'}
        user={target}
        onClose={close}
        onSubmit={async (pwd) => {
          await userService.resetPassword(target.id, pwd);
          notify.success(t('users.passwordReset'));
          close();
          refresh();
        }}
      />
      <ConfirmDialog
        open={dialog.type === 'status'}
        title={target?.active ? t('users.deactivate') : t('users.activate')}
        message={target ? t(target.active ? 'users.deactivateConfirm' : 'users.activateConfirm', { name: target.fullName }) : ''}
        confirmLabel={target?.active ? t('users.deactivate') : t('users.activate')}
        color={target?.active ? 'error' : 'success'}
        loading={statusMutation.isPending}
        onClose={close}
        onConfirm={() => statusMutation.mutate({ id: target.id, value: !target.active })}
      />
    </>
  );
}
