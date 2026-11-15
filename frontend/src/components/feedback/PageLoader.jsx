import { Box, CircularProgress } from '@mui/material';

export default function PageLoader({ fullScreen }) {
  return (
    <Box sx={{ display: 'grid', placeItems: 'center', minHeight: fullScreen ? '100vh' : 320, bgcolor: fullScreen ? 'background.default' : undefined }}>
      <CircularProgress />
    </Box>
  );
}
