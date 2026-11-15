import { normalizeError } from '../services/apiClient';
import { msg } from './validation';

/**
 * Puts backend field errors ({ field, code, params }) onto react-hook-form fields.
 * Codes match the `validation.*` translation keys. Returns true if any field error was applied.
 */
export function applyServerFieldErrors(error, setError) {
  const e = normalizeError(error);
  let applied = false;
  for (const fe of e.fieldErrors ?? []) {
    setError(fe.field, { type: 'server', message: msg(`validation.${fe.code}`, fe.params && Object.keys(fe.params).length ? fe.params : undefined) });
    applied = true;
  }
  if (e.code === 'DUPLICATE' && e.params?.field) {
    setError(e.params.field, { type: 'server', message: 'validation.duplicate' });
    applied = true;
  }
  return applied;
}
