import { Avatar, Box } from '@mui/material';
import StorefrontOutlined from '@mui/icons-material/StorefrontOutlined';

/** Business logo, or a default storefront mark when no logo has been uploaded. */
export default function BrandMark({ logoUrl, size = 40, alt }) {
  if (logoUrl) {
    return <Box component="img" src={logoUrl} alt={alt} sx={{ width: size, height: size, borderRadius: 2, objectFit: 'cover', bgcolor: '#fff' }} />;
  }
  return (
    <Avatar variant="rounded" sx={{ width: size, height: size, bgcolor: 'primary.main', borderRadius: 2 }}>
      <StorefrontOutlined sx={{ fontSize: size * 0.6 }} />
    </Avatar>
  );
}
