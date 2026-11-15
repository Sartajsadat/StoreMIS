import { DataGrid } from '@mui/x-data-grid';
import { useTranslation } from 'react-i18next';
import EmptyState from '../feedback/EmptyState';

function NoRows() {
  const { t } = useTranslation();
  return <EmptyState title={t('common.noData')} description={t('common.noDataHint')} dense />;
}

/**
 * Shared table. Client-side by default; pass `rowCount`, `paginationModel`, `onPaginationModelChange`
 * (and sort handlers) for server-side pagination — `server` switches the modes.
 * Column headers must be translated by the caller (headerName: t('...')).
 */
export default function DataTable({ rows, columns, loading, server = false, rowCount, paginationModel, onPaginationModelChange, sortModel, onSortModelChange, height, pageSizeOptions = [10, 20, 50, 100], getRowId, onRowClick, ...props }) {
  return (
    <DataGrid
      rows={rows ?? []}
      columns={columns}
      loading={loading}
      getRowId={getRowId}
      onRowClick={onRowClick}
      disableRowSelectionOnClick
      disableColumnMenu={false}
      pageSizeOptions={pageSizeOptions}
      paginationMode={server ? 'server' : 'client'}
      sortingMode={server ? 'server' : 'client'}
      rowCount={server ? rowCount ?? 0 : undefined}
      paginationModel={paginationModel}
      onPaginationModelChange={onPaginationModelChange}
      sortModel={sortModel}
      onSortModelChange={onSortModelChange}
      initialState={paginationModel ? undefined : { pagination: { paginationModel: { pageSize: 20 } } }}
      slots={{ noRowsOverlay: NoRows }}
      autoHeight={!height}
      density="standard"
      sx={{
        ...(height && { height }),
        '& .MuiDataGrid-row': { cursor: onRowClick ? 'pointer' : 'default' },
        '& .MuiDataGrid-overlayWrapper': { minHeight: 200 },
      }}
      {...props}
    />
  );
}
