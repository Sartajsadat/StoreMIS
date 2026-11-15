import en from './locales/en.json';
import fa from './locales/fa.json';
import ps from './locales/ps.json';
import { NAV, flattenNav } from '../layouts/navConfig';

const flatten = (obj, prefix = '') =>
  Object.entries(obj).flatMap(([k, v]) => (v && typeof v === 'object' ? flatten(v, `${prefix}${k}.`) : [`${prefix}${k}`]));
const get = (obj, key) => key.split('.').reduce((o, k) => o?.[k], obj);

describe('translations', () => {
  it('have identical keys in en, fa and ps', () => {
    const keys = flatten(en).sort();
    expect(flatten(fa).sort()).toEqual(keys);
    expect(flatten(ps).sort()).toEqual(keys);
  });

  it('translate every menu entry and its phase name', () => {
    for (const lang of [en, fa, ps]) {
      for (const item of [...NAV, ...flattenNav()]) {
        expect(get(lang, item.labelKey), item.labelKey).toBeTruthy();
        if (item.phase) expect(get(lang, `comingSoon.phases.${item.phase}`)).toBeTruthy();
      }
    }
  });
});
