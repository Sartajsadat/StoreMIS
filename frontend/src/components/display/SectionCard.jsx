import { Card, CardContent, CardHeader, Divider } from '@mui/material';

/** Card with an optional header row (title, subheader, action) used for every page section. */
export default function SectionCard({ title, subheader, action, children, noPadding, divider = false, sx }) {
  return (
    <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column', ...sx }}>
      {(title || action) && <CardHeader title={title} subheader={subheader} action={action} slotProps={{ subheader: { variant: 'caption' } }} />}
      {divider && <Divider />}
      {noPadding ? children : <CardContent sx={{ flex: 1 }}>{children}</CardContent>}
    </Card>
  );
}
