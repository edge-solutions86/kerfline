import { BadInput } from '../errors.js';
import { isotope } from './catalog.js';
import { isFiniteNumber, round } from '../util/num.js';

/**
 * Source decay. A camera loaded in March is not the camera it was in June,
 * and every exposure time in this package is computed from the activity on
 * the day of the shot rather than from the number stamped on the certificate.
 */

const LN2 = Math.log(2);

export function decayConstant(halfLifeDays) {
  requirePositive(halfLifeDays, 'half life');
  return LN2 / halfLifeDays;
}

/** The fraction of the original activity left after elapsedDays. */
export function decayFactor(halfLifeDays, elapsedDays) {
  requirePositive(halfLifeDays, 'half life');
  requireFinite(elapsedDays, 'elapsed days');
  return Math.exp(-decayConstant(halfLifeDays) * elapsedDays);
}

export function halfLivesElapsed(halfLifeDays, elapsedDays) {
  requirePositive(halfLifeDays, 'half life');
  requireFinite(elapsedDays, 'elapsed days');
  return elapsedDays / halfLifeDays;
}

/**
 * Activity after a span. A negative span back decays to a date before the
 * assay, which is what a technician does when a certificate arrives late.
 */
export function activityAfter(startCi, halfLifeDays, elapsedDays) {
  requirePositive(startCi, 'assay activity');
  return startCi * decayFactor(halfLifeDays, elapsedDays);
}

/** The same, taking the isotope by name so the half life comes from one place. */
export function activityOf(idOrLabel, startCi, elapsedDays) {
  const source = isotope(idOrLabel);
  return activityAfter(startCi, source.halfLifeDays, elapsedDays);
}

/**
 * Days from the assay until the activity has fallen to targetCi. Negative
 * when the target is above the assay, which means the date has passed.
 */
export function daysToActivity(startCi, halfLifeDays, targetCi) {
  requirePositive(startCi, 'assay activity');
  requirePositive(targetCi, 'target activity');
  requirePositive(halfLifeDays, 'half life');
  return (halfLifeDays * Math.log(startCi / targetCi)) / LN2;
}

/**
 * The last day the source is worth loading, given the smallest activity the
 * shop will shoot with. Rounded down, because a partial day of usefulness is
 * not one a scheduler can book.
 */
export function usableDays(idOrLabel, startCi, minimumCi) {
  const source = isotope(idOrLabel);
  const days = daysToActivity(startCi, source.halfLifeDays, minimumCi);
  return Math.max(0, Math.floor(days));
}

/**
 * A decay strip for the camera log: activity on each of the given days.
 * Field crews keep one of these taped inside the transport case.
 */
export function decayTable(idOrLabel, startCi, dayOffsets, options = {}) {
  const source = isotope(idOrLabel);
  if (!Array.isArray(dayOffsets) || dayOffsets.length === 0) {
    throw new BadInput('a decay table needs at least one day offset', { got: dayOffsets });
  }
  const places = options.decimals ?? 2;
  return dayOffsets.map((days) => {
    requireFinite(days, 'day offset');
    const curies = activityAfter(startCi, source.halfLifeDays, days);
    return {
      days,
      curies: round(curies, places),
      fraction: round(decayFactor(source.halfLifeDays, days), 4),
    };
  });
}

/**
 * The multiplier to put on an exposure time worked out at the assay activity,
 * so a chart read once stays usable as the source ages.
 */
export function exposureMultiplier(halfLifeDays, elapsedDays) {
  return 1 / decayFactor(halfLifeDays, elapsedDays);
}

function requireFinite(value, what) {
  if (!isFiniteNumber(value)) {
    throw new BadInput(`${what} must be a finite number`, { got: value });
  }
  return value;
}

function requirePositive(value, what) {
  requireFinite(value, what);
  if (value <= 0) throw new BadInput(`${what} must be greater than zero`, { got: value });
  return value;
}
