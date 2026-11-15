import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import en from './locales/en.json';
import fa from './locales/fa.json';
import ps from './locales/ps.json';
import { storage } from '../utils/storage';

export const LANGUAGES = [
  { code: 'en', dir: 'ltr', labelKey: 'languages.en', htmlLang: 'en' },
  { code: 'fa', dir: 'rtl', labelKey: 'languages.fa', htmlLang: 'fa-AF' },
  { code: 'ps', dir: 'rtl', labelKey: 'languages.ps', htmlLang: 'ps-AF' },
];
export const SUPPORTED_LANGUAGES = LANGUAGES.map((l) => l.code);
export const DEFAULT_LANGUAGE = 'en';

export const isSupportedLanguage = (code) => SUPPORTED_LANGUAGES.includes(code);
export const directionOf = (code) => LANGUAGES.find((l) => l.code === code)?.dir ?? 'ltr';

const stored = storage.get('storemis.language');

i18n.use(initReactI18next).init({
  resources: { en: { translation: en }, fa: { translation: fa }, ps: { translation: ps } },
  lng: isSupportedLanguage(stored) ? stored : DEFAULT_LANGUAGE,
  fallbackLng: 'en',
  supportedLngs: SUPPORTED_LANGUAGES,
  interpolation: { escapeValue: false },
  returnNull: false,
});

export default i18n;
