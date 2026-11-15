import { Backdrop, CircularProgress } from '@mui/material';

/** Blocks the screen while a posting (sale, payment…) is being saved, to prevent double submission. */
export default function LoadingOverlay({ open }) {
  return (
    <Backdrop open={Boolean(open)} sx={{ zIndex: (theme) => theme.zIndex.modal + 1, color: '#fff' }}>
      <CircularProgress color="inherit" />
    </Backdrop>
  );
}
