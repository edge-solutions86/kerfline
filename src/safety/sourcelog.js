import { Conflict } from '../errors.js';
import { round, sum } from '../util/num.js';
import { requireAbove, requireArray, requireAtLeast, requireLabel } from '../util/guard.js';
import { requireDay, addMonths, daysBetween, withinSpan } from '../util/dates.js';
import { isotope } from '../isotope/catalog.js';
import { activityAfter } from '../isotope/decay.js';

/**
 * The source inventory, the utilisation log and the leak test.
 *
 * Every sealed source in the shop is on this list from the day it arrives to
 * the day it is shipped back, and the log has to be able to say where it was
 * on any given day in between. That is not paperwork for its own sake: it is
 * the only way to answer the question that gets asked after an incident,
 * which is who had it and what were they doing.
 *
 * The leak test is six monthly and the threshold is five nanocuries of
 * removable contamination. A source over that comes out of service the same
 * day, and there is no margin and no supervisor override on that number.
 */

const LEAK_LIMIT_UCI = 0.005;
const LEAK_INTERVAL_MONTHS = 6;

export function leakLimitMicroCuries() {
  return LEAK_LIMIT_UCI;
}

export function leakIntervalMonths() {
  return LEAK_INTERVAL_MONTHS;
}

export function makeSource({ serial, isotopeId, assayCi, assayDay, camera = null }) {
  requireLabel(serial, 'a source is identified by its serial number');
  const source = isotope(isotopeId);
  requireAbove(assayCi, 0, 'assay activity');
  requireDay(assayDay, 'assay date');
  return {
    serial: serial.trim(),
    isotope: source.id,
    assayCi: round(assayCi, 2),
    assayDay,
    camera,
  };
}

/** What the source reads on a given day, from its certificate. */
export function activityOn(source, day) {
  requireDay(day, 'date');
  const record = isotope(source.isotope);
  const days = daysBetween(source.assayDay, day);
  return round(activityAfter(source.assayCi, record.halfLifeDays, days), 2);
}

/** One entry in the utilisation log. */
export function makeUse({ serial, day, user, location, minutes, exposures }) {
  requireDay(day, 'date');
  requireLabel(user, 'every use of a source names who had it');
  requireAtLeast(minutes, 0, 'time out of shield');
  requireAtLeast(exposures, 0, 'exposure count');
  return {
    serial,
    day,
    user: user.trim(),
    location: location ?? null,
    minutes: round(minutes, 1),
    exposures,
  };
}

/** What a source did over a period. */
export function utilisation(uses, { serial, from, to }) {
  requireArray(uses, 'log entries');
  requireDay(from, 'period start');
  requireDay(to, 'period end');
  const mine = uses.filter((use) => use.serial === serial && withinSpan(use.day, from, to));
  return {
    serial,
    from,
    to,
    entries: mine.length,
    minutes: round(sum(mine.map((use) => use.minutes)), 1),
    exposures: sum(mine.map((use) => use.exposures)),
    users: [...new Set(mine.map((use) => use.user))].sort(),
    days: [...new Set(mine.map((use) => use.day))].sort(),
  };
}

/** Who had a source on a day, which is the question asked after an incident. */
export function whoHadIt(uses, { serial, day }) {
  requireArray(uses, 'log entries');
  requireDay(day, 'date');
  return uses
    .filter((use) => use.serial === serial && use.day === day)
    .map((use) => ({ user: use.user, location: use.location, minutes: use.minutes }));
}

export function makeLeakTest({ serial, day, microCuries, laboratory = null }) {
  requireDay(day, 'test date');
  requireAtLeast(microCuries, 0, 'removable activity');
  const pass = microCuries < LEAK_LIMIT_UCI;
  return {
    serial,
    day,
    microCuries: round(microCuries, 5),
    laboratory,
    pass,
    dueBy: addMonths(day, LEAK_INTERVAL_MONTHS),
    action: pass ? null : 'remove the source from service today',
  };
}

/**
 * Whether a source may be used on a day. A failed test never becomes current
 * again by waiting, and a test that has run out is the same answer as never
 * having had one.
 */
export function leakTestCurrent(tests, { serial, day }) {
  requireArray(tests, 'leak tests');
  requireDay(day, 'date');
  const mine = tests
    .filter((test) => test.serial === serial && daysBetween(test.day, day) >= 0)
    .sort((a, b) => (a.day < b.day ? 1 : -1));
  if (mine.length === 0) {
    return { current: false, reason: 'no leak test on file', latest: null };
  }
  const latest = mine[0];
  if (!latest.pass) {
    return { current: false, reason: 'the last leak test failed', latest };
  }
  if (day > latest.dueBy) {
    return { current: false, reason: 'the leak test has run out', latest };
  }
  return { current: true, reason: null, latest, daysLeft: daysBetween(day, latest.dueBy) };
}

/** Refuse to book out a source that is not fit to leave the store. */
export function checkOut({ source, tests, day }) {
  const leak = leakTestCurrent(tests, { serial: source.serial, day });
  if (!leak.current) {
    throw new Conflict(`source ${source.serial} cannot go out: ${leak.reason}`, {
      serial: source.serial,
      reason: leak.reason,
    });
  }
  return { serial: source.serial, day, activityCi: activityOn(source, day) };
}

/** Sources whose leak test is due inside a window, so it can be booked. */
export function leakTestsDueBy(sources, tests, { day, withinDays }) {
  requireDay(day, 'date');
  requireAtLeast(withinDays, 0, 'window');
  return sources
    .map((source) => ({ source, leak: leakTestCurrent(tests, { serial: source.serial, day }) }))
    .filter(({ leak }) => !leak.current || leak.daysLeft <= withinDays)
    .map(({ source, leak }) => ({
      serial: source.serial,
      dueBy: leak.latest ? leak.latest.dueBy : null,
      reason: leak.reason ?? 'due for renewal',
    }));
}
