import { Box, Typography } from '@mui/material';

/** Page heading row: title + subtitle on the start side, actions on the end side. */
export default function PageHeader({ title, subtitle, actions }) {
  return (
    <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 2, mb: 3 }}>
      <Box sx={{ minWidth: 0 }}>
        {title && (
          <Typography variant="h5" component="h2" sx={{ display: "flex", alignItems: "center", gap: 1 }}>
            {title}
          </Typography>
        )}
        {subtitle && (
          <Typography variant="body2" component="div" color="text.secondary" sx={{ mt: 0.5, maxWidth: 720 }}>
            {subtitle}
          </Typography>
        )}
      </Box>
      {actions && <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>{actions}</Box>}
    </Box>
  );
}
