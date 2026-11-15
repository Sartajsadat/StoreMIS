import { useMemo, useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Box, Card, Chip, IconButton, MenuItem, TextField, Tooltip } from '@mui/material';
import { alpha } from '@mui/material/styles';
import ManageSearchOutlined from '@mui/icons-material/ManageSearchOutlined';
import PageHeader from '../../components/display/PageHeader';
import DataTable from '../../components/data/DataTable';
import FilterBar from '../../components/data/FilterBar';
import DateRangeFilter from '../../components/data/DateRangeFilter';
import Ltr from '../../components/display/Ltr';
import AuditDetailsDialog from './AuditDetailsDialog';
import { auditService, AUDIT_ACTIONS, AUDIT_MODULES } from '../../services/auditService';
import { userService } from '../../services/userService';
import { useDebounce } from '../../hooks/useDebounce';
import { resolveRange } from '../../utils/dateRange';
import { formatDateTime } from '../../utils/format';

const DANGER = new Set(['LOGIN_FAILED', 'CANCEL', 'REVERSE', 'VOID', 'DEACTIVATE']);
const WARN = new Set(['PASSWORD_RESET', 'ADJUST', 'RETURN', 'REOPEN', 'SETTINGS_CHANGE']);

export default function ActivityLogsPage() {
  const { t } = useTranslation();
  const [range, setRange] = useState({ preset: 'WEEK', ...resolveRange('WEEK') });
  const [module, setModule] = useState('');
  const [action, setAction] = useState('');
  const [userId, setUserId] = useState('');
  const [search, setSearch] = useState('');
  const [pagination, setPagination] = useState({ page: 0, pageSize: 20 });
  const [selected, setSelected] = useState(null);
  const q = useDebounce(search, 300);

  const params = {
    from: range.from,
    to: range.to,
    module: module || undefined,
    action: action || undefined,
    userId: userId || undefined,
    q: q || undefined,
    page: pagination.page,
    size: pagination.pageSize,
  };
  const logs = useQuery({ queryKey: ['audit', params], queryFn: () => auditService.search(params), placeholderData: keepPreviousData });
  const users = useQuery({ queryKey: ['users', 'all-for-filter'], queryFn: () => userService.list({ size: 200 }), staleTime: 5 * 60 * 1000 });

  const columns = useMemo(
    () => [
      { field: 'occurredAt', headerName: t('audit.time'), width: 170, renderCell: (p) => <Ltr>{formatDateTime(p.value)}</Ltr> },
      { field: 'username', headerName: t('audit.user'), width: 150, renderCell: (p) => (p.value ? <Ltr>{p.value}</Ltr> : t('audit.system')) },
      { field: 'module', headerName: t('audit.module'), width: 150, renderCell: (p) => t(`audit.modules.${p.value}`) },
      {
        field: 'action',
        headerName: t('audit.action'),
        width: 190,
        renderCell: (p) => {
          const color = DANGER.has(p.value) ? 'error' : WARN.has(p.value) ? 'warning' : 'primary';
          return (
            <Chip
              size="small"
              label={t(`audit.actions.${p.value}`)}
              sx={(th) => ({ color: th.palette[color].main, bgcolor: alpha(th.palette[color].main, 0.12), border: `1px solid ${alpha(th.palette[color].main, 0.3)}` })}
            />
          );
        },
      },
      { field: 'reference', headerName: t('audit.reference'), flex: 1, minWidth: 160, renderCell: (p) => <Ltr>{p.value}</Ltr> },
      { field: 'ipAddress', headerName: t('audit.ip'), width: 140, renderCell: (p) => <Ltr>{p.value}</Ltr> },
      {
        field: 'details',
        headerName: '',
        width: 60,
        sortable: false,
        align: 'center',
        renderCell: (p) => (
          <Tooltip title={t('audit.details')}>
            <IconButton size="small" onClick={() => setSelected(p.row)} aria-label={t('audit.details')}>
              <ManageSearchOutlined fontSize="small" />
            </IconButton>
          </Tooltip>
        ),
      },
    ],
    [t],
  );

  const resetPage = () => setPagination((p) => ({ ...p, page: 0 }));

  return (
    <>
      <PageHeader title={t('audit.title')} subtitle={t('audit.subtitle')} />
      <Card>
        <FilterBar
          search={search}
          onSearchChange={(v) => {
            setSearch(v);
            resetPage();
          }}
          onReset={() => {
            setSearch('');
            setModule('');
            setAction('');
            setUserId('');
            setRange({ preset: 'WEEK', ...resolveRange('WEEK') });
          }}
        >
          <DateRangeFilter
            value={range}
            onChange={(r) => {
              setRange(r);
              resetPage();
            }}
          />
          <TextField select label={t('audit.module')} value={module} onChange={(e) => (setModule(e.target.value), resetPage())} sx={{ width: 170 }} fullWidth={false}>
            <MenuItem value="">{t('audit.allModules')}</MenuItem>
            {AUDIT_MODULES.map((m) => (
              <MenuItem key={m} value={m}>
                {t(`audit.modules.${m}`)}
              </MenuItem>
            ))}
          </TextField>
          <TextField select label={t('audit.action')} value={action} onChange={(e) => (setAction(e.target.value), resetPage())} sx={{ width: 170 }} fullWidth={false}>
            <MenuItem value="">{t('audit.allActions')}</MenuItem>
            {AUDIT_ACTIONS.map((a) => (
              <MenuItem key={a} value={a}>
                {t(`audit.actions.${a}`)}
              </MenuItem>
            ))}
          </TextField>
          <TextField select label={t('audit.user')} value={userId} onChange={(e) => (setUserId(e.target.value), resetPage())} sx={{ width: 180 }} fullWidth={false}>
            <MenuItem value="">{t('audit.allUsers')}</MenuItem>
            {(users.data?.content ?? []).map((u) => (
              <MenuItem key={u.id} value={u.id}>
                {u.fullName}
              </MenuItem>
            ))}
          </TextField>
        </FilterBar>
        <Box sx={{ px: 1, pb: 1 }}>
          <DataTable
            server
            rows={logs.data?.content}
            rowCount={logs.data?.totalElements}
            columns={columns}
            loading={logs.isFetching}
            paginationModel={pagination}
            onPaginationModelChange={setPagination}
          />
        </Box>
      </Card>
      <AuditDetailsDialog log={selected} onClose={() => setSelected(null)} />
    </>
  );
}
