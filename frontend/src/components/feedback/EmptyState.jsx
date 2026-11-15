import { Box, Typography } from '@mui/material';
import InboxOutlined from '@mui/icons-material/InboxOutlined';

export default function EmptyState({ icon: Icon = InboxOutlined, title, description, action, dense }) {
  return (
    <Box sx={{ textAlign: 'center', py: dense ? 4 : 8, px: 3, color: 'text.secondary' }}>
      <Box sx={{ width: 56, height: 56, mx: 'auto', mb: 2, borderRadius: '50%', display: 'grid', placeItems: 'center', bgcolor: 'action.hover' }}>
        <Icon />
      </Box>
      {title && (
        <Typography variant="subtitle1" color="text.primary">
          {title}
        </Typography>
      )}
      {description && (
        <Typography variant="body2" sx={{ mt: 0.5, maxWidth: 420, mx: 'auto' }}>
          {description}
        </Typography>
      )}
      {action && <Box sx={{ mt: 2.5 }}>{action}</Box>}
    </Box>
  );
}
