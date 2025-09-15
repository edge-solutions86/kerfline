import { round, sum } from '../util/num.js';
import { requireArray, requireAtLeast, requireLabel, requireOneOf } from '../util/guard.js';
import { requireDay, addYears, daysBetween, withinSpan } from '../util/dates.js';
import { requirementsFor, levels } from './cert.js';

/**
 * Training records, and the hours they add up to.
 *
 * Two things make this less trivial than a running total. Training is per
 * method, so hours on ultrasonics do not count towards a radiography level
 * however many of them there are. And some courses expire: radiation safety
 * has to be refreshed, and hours from a lapsed safety course still count
 * towards experience but do not keep somebody current to work.
 *
 * So there are two questions with different answers, and the module answers
 * both separately rather than producing one number that means neither.
 */

const METHODS = ['RT', 'UT', 'MT', 'PT', 'VT', 'SAFETY'];
const REFRESH_YEARS = { SAFETY: 1 };

export function methods() {
  return [...METHODS];
}

export function refreshYearsFor(method) {
  requireOneOf(method, METHODS, 'method');
  return REFRESH_YEARS[method] ?? null;
}

export function makeTraining({ person, method, course, hours, day, provider = null }) {
  requireLabel(person, 'a training record names who attended');
  requireLabel(course, 'a training record names the course');
  requireOneOf(method, METHODS, 'method');
  requireDay(day, 'training date');
  requireAtLeast(hours, 0, 'hours');
  const refresh = refreshYearsFor(method);
  return {
    person: person.trim(),
    method,
    course: course.trim(),
    hours: round(hours, 1),
    day,
    provider,
    expiresOn: refresh === null ? null : addYears(day, refresh),
  };
}

/** Hours in one method, which is the only total that counts for a level. */
export function hoursToward(records, method) {
  requireArray(records, 'training records');
  requireOneOf(method, METHODS, 'method');
  return round(
    sum(records.filter((record) => record.method === method).map((record) => record.hours)),
    1
  );
}

/** Hours split by method, so a gap in one shows next to a surplus in another. */
export function hoursByMethod(records) {
  requireArray(records, 'training records');
  return Object.fromEntries(METHODS.map((method) => [method, hoursToward(records, method)]));
}

/**
 * Whether the training hours reach a level. Experience hours are counted
 * separately, on the job, and are passed in rather than derived here.
 */
export function readyFor(records, { method = 'RT', level, experienceHours = 0 }) {
  requireOneOf(level, levels(), 'certification level');
  const wanted = requirementsFor(level);
  const training = hoursToward(records, method);
  requireAtLeast(experienceHours, 0, 'experience hours');
  return {
    level,
    method,
    trainingHours: training,
    experienceHours,
    trainingShort: round(Math.max(0, wanted.trainingHours - training), 1),
    experienceShort: round(Math.max(0, wanted.experienceHours - experienceHours), 1),
    ready: training >= wanted.trainingHours && experienceHours >= wanted.experienceHours,
  };
}

/** Records of a kind that has to be refreshed and has not been, on a day. */
export function lapsed(records, day) {
  requireArray(records, 'training records');
  requireDay(day, 'date');
  return records.filter(
    (record) => record.expiresOn !== null && daysBetween(record.expiresOn, day) > 0
  );
}

/**
 * Whether somebody is current to work on a day. Safety training is the one
 * that gates that, and having taken it once a long time ago is not the same
 * as being current.
 */
export function currentToWork(records, day) {
  requireDay(day, 'date');
  const safety = records
    .filter((record) => record.method === 'SAFETY')
    .filter((record) => daysBetween(record.day, day) >= 0)
    .sort((a, b) => (a.day < b.day ? 1 : -1));
  if (safety.length === 0) {
    return { current: false, reason: 'no radiation safety training on file', latest: null };
  }
  const latest = safety[0];
  if (daysBetween(latest.expiresOn, day) > 0) {
    return { current: false, reason: 'the safety training has lapsed', latest };
  }
  return { current: true, reason: null, latest, daysLeft: daysBetween(day, latest.expiresOn) };
}

/** Training taken inside a window, for an annual return. */
export function takenBetween(records, { from, to }) {
  requireArray(records, 'training records');
  requireDay(from, 'period start');
  requireDay(to, 'period end');
  return records.filter((record) => withinSpan(record.day, from, to));
}
