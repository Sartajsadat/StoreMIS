// Zod messages are i18n keys. Parameters are encoded after a "|" so a single string can carry them:
//   msg('validation.min', { min: 1 })  ->  'validation.min|{"min":1}'
export const msg = (key, params) => (params ? `${key}|${JSON.stringify(params)}` : key);

export function translateValidation(t, message) {
  if (!message) return '';
  const [key, raw] = message.split('|');
  let params;
  try {
    params = raw ? JSON.parse(raw) : undefined;
  } catch {
    params = undefined;
  }
  return t(key, params);
}
