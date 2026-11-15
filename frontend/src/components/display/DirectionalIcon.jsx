import { useTheme } from '@mui/material/styles';

/** Mirrors a directional icon (arrows, chevrons) when the layout is right-to-left. */
export default function DirectionalIcon({ icon: Icon, sx, ...props }) {
  const theme = useTheme();
  return <Icon {...props} sx={{ transform: theme.direction === 'rtl' ? 'scaleX(-1)' : undefined, ...sx }} />;
}
