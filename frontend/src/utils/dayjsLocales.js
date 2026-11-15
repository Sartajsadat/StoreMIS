import dayjs from 'dayjs';

// Afghan Gregorian month names (the stock dayjs "fa" locale uses Iranian names).
// The week starts on Saturday in Afghanistan.
const common = {
  weekStart: 6,
  formats: { LT: 'HH:mm', LTS: 'HH:mm:ss', L: 'YYYY/MM/DD', LL: 'D MMMM YYYY', LLL: 'D MMMM YYYY HH:mm', LLLL: 'dddd, D MMMM YYYY HH:mm' },
  ordinal: (n) => `${n}`,
};

dayjs.locale(
  {
    ...common,
    name: 'fa-af',
    months: 'جنوری_فبروری_مارچ_اپریل_می_جون_جولای_اگست_سپتمبر_اکتوبر_نومبر_دسمبر'.split('_'),
    monthsShort: 'جنوری_فبروری_مارچ_اپریل_می_جون_جولای_اگست_سپتمبر_اکتوبر_نومبر_دسمبر'.split('_'),
    weekdays: 'یکشنبه_دوشنبه_سه‌شنبه_چهارشنبه_پنجشنبه_جمعه_شنبه'.split('_'),
    weekdaysShort: 'یک_دو_سه_چهار_پنج_جمعه_شنبه'.split('_'),
    weekdaysMin: 'ی_د_س_چ_پ_ج_ش'.split('_'),
  },
  null,
  true,
);

dayjs.locale(
  {
    ...common,
    name: 'ps-af',
    months: 'جنوري_فبروري_مارچ_اپرېل_مۍ_جون_جولای_اګست_سپتمبر_اکتوبر_نومبر_ډسمبر'.split('_'),
    monthsShort: 'جنوري_فبروري_مارچ_اپرېل_مۍ_جون_جولای_اګست_سپتمبر_اکتوبر_نومبر_ډسمبر'.split('_'),
    weekdays: 'یکشنبه_دوشنبه_سه‌شنبه_چهارشنبه_پنجشنبه_جمعه_شنبه'.split('_'),
    weekdaysShort: 'یک_دو_سه_چهار_پنج_جمعه_شنبه'.split('_'),
    weekdaysMin: 'ی_د_س_چ_پ_ج_ش'.split('_'),
  },
  null,
  true,
);

export const DAYJS_LOCALE = { en: 'en', fa: 'fa-af', ps: 'ps-af' };
