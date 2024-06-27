import { BadInput } from '../errors.js';

/**
 * Plain calendar days. Everything dated in this package is a day and not an
 * instant: a source is assayed on a day, a shot is fired on a day, a
 * certification lapses on a day. Carrying a time of day through would only
 * invite a timezone to change an expiry date by one, so days are held as
 * YYYY-MM-DD strings and all arithmetic goes through UTC.
 */

const DAY_MS = 86400000;
const PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;

/** Throw unless text is a real calendar day, then hand it back unchanged. */
export function requireDay(text, what = 'date') {
  const match = PATTERN.exec(String(text));
  if (!match) {
    throw new BadInput(`${what} must be written as YYYY-MM-DD`, { got: text });
  }
  const [, y, m, d] = match.map(Number);
  const stamp = Date.UTC(y, m - 1, d);
  const back = new Date(stamp);
  if (back.getUTCFullYear() !== y || back.getUTCMonth() !== m - 1 || back.getUTCDate() !== d) {
    throw new BadInput(`${what} is not a real calendar day`, { got: text });
  }
  return text;
}

export function isDay(text) {
  try {
    requireDay(text);
    return true;
  } catch {
    return false;
  }
}

/** Milliseconds at midnight UTC, used only as an arithmetic handle. */
function stampOf(day, what) {
  requireDay(day, what);
  const [y, m, d] = day.split('-').map(Number);
  return Date.UTC(y, m - 1, d);
}

export function formatDay(stamp) {
  const date = new Date(stamp);
  const y = String(date.getUTCFullYear()).padStart(4, '0');
  const m = String(date.getUTCMonth() + 1).padStart(2, '0');
  const d = String(date.getUTCDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/** Whole days from the first day to the second. Negative when it runs back. */
export function daysBetween(from, to) {
  return Math.round((stampOf(to, 'end date') - stampOf(from, 'start date')) / DAY_MS);
}

export function addDays(day, count) {
  if (!Number.isInteger(count)) {
    throw new BadInput('day count must be a whole number', { count });
  }
  return formatDay(stampOf(day) + count * DAY_MS);
}

/**
 * Add whole months, clamping onto the end of a short month. A six month leak
 * test due on the 31st of March falls due on the 30th of September and not on
 * the 1st of October, which is what the regulator reads it as.
 */
export function addMonths(day, count) {
  if (!Number.isInteger(count)) {
    throw new BadInput('month count must be a whole number', { count });
  }
  const [y, m, d] = requireDay(day).split('-').map(Number);
  const target = y * 12 + (m - 1) + count;
  const year = Math.floor(target / 12);
  const month = target % 12;
  const lastDay = new Date(Date.UTC(year, month + 1, 0)).getUTCDate();
  return formatDay(Date.UTC(year, month, Math.min(d, lastDay)));
}

export function addYears(day, count) {
  return addMonths(day, count * 12);
}

/** Day of the week, 0 for Sunday, matching the shop calendar. */
export function weekday(day) {
  return new Date(stampOf(day)).getUTCDay();
}

export function isWeekend(day) {
  const wd = weekday(day);
  return wd === 0 || wd === 6;
}

/** Skip forward by working days, which is how a repair window is counted. */
export function addWorkDays(day, count) {
  if (!Number.isInteger(count) || count < 0) {
    throw new BadInput('working day count must be a whole number of days', { count });
  }
  let cursor = requireDay(day);
  let left = count;
  while (left > 0) {
    cursor = addDays(cursor, 1);
    if (!isWeekend(cursor)) left -= 1;
  }
  return cursor;
}

export function earliest(days) {
  return pick(days, (a, b) => (a < b ? a : b), 'earliest');
}

export function latest(days) {
  return pick(days, (a, b) => (a > b ? a : b), 'latest');
}

function pick(days, choose, what) {
  if (!Array.isArray(days) || days.length === 0) {
    throw new BadInput(`the ${what} of no days is undefined`, { got: days });
  }
  days.forEach((d) => requireDay(d));
  return days.reduce(choose);
}

/** True when day falls inside the closed span, either bound optional. */
export function withinSpan(day, from, to) {
  requireDay(day);
  if (from !== null && from !== undefined && day < requireDay(from, 'span start')) return false;
  if (to !== null && to !== undefined && day > requireDay(to, 'span end')) return false;
  return true;
}

/** Whole months between two days, counting only months that completed. */
export function monthsBetween(from, to) {
  requireDay(from, 'start date');
  requireDay(to, 'end date');
  const [fy, fm, fd] = from.split('-').map(Number);
  const [ty, tm, td] = to.split('-').map(Number);
  const months = (ty - fy) * 12 + (tm - fm);
  return td < fd ? months - 1 : months;
}
