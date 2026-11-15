import createCache from '@emotion/cache';
import { prefixer } from 'stylis';
import rtlPlugin from 'stylis-plugin-rtl';

// Two Emotion caches: the RTL one mirrors every CSS rule (margins, paddings, positions)
// so MUI components and our own sx styles flip automatically for Dari and Pashto.
export const ltrCache = createCache({ key: 'mui', prepend: true });
export const rtlCache = createCache({ key: 'muirtl', stylisPlugins: [prefixer, rtlPlugin], prepend: true });
