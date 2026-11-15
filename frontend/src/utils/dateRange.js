import dayjs from 'dayjs';

export const RANGE_PRESETS = ['TODAY', 'WEEK', 'MONTH', 'YEAR', 'CUSTOM'];

/** Resolves a preset into inclusive ISO dates (YYYY-MM-DD). Weeks start on Saturday, as in Afghanistan. */
export function resolveRange(preset, custom = {}, today = dayjs()) {
  const iso = (d) => d.format('YYYY-MM-DD');
  switch (preset) {
    case 'TODAY':
      return { from: iso(today), to: iso(today) };
    case 'WEEK': {
      const offset = (today.day() + 1) % 7; // Saturday = 0
      return { from: iso(today.subtract(offset, 'day')), to: iso(today) };
    }
    case 'MONTH':
      return { from: iso(today.startOf('month')), to: iso(today) };
    case 'YEAR':
      return { from: iso(today.startOf('year')), to: iso(today) };
    default:
      return { from: custom.from ?? iso(today), to: custom.to ?? iso(today) };
  }
}
