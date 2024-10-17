import { BadInput } from '../errors.js';
import { round, maxOf } from '../util/num.js';
import { requireArray, requirePositive } from '../util/guard.js';
import { daysBetween, addDays, requireDay } from '../util/dates.js';

/**
 * Densitometer calibration against a step wedge comparison film.
 *
 * The comparison film carries a strip of steps of certified density. The
 * densitometer reads each one and every reading has to land within five
 * hundredths of the certificate. That tolerance is on each step and not on
 * the average, which matters: an instrument reading high at the light end and
 * low at the dark end averages to nothing and is still unusable.
 *
 * The calibration lapses after ninety days. It also lapses the moment the
 * instrument is dropped or repaired, which is not something this module can
 * know about, so the record carries a reason and a caller can retire a
 * calibration early.
 */

const TOLERANCE = 0.05;
const VALID_DAYS = 90;

export function calibrationTolerance() {
  return TOLERANCE;
}

export function calibrationValidDays() {
  return VALID_DAYS;
}

/**
 * Compare a set of readings against the certified step densities. Both lists
 * are in step order and have to be the same length, because a reading with no
 * step to compare it against is not a calibration point.
 */
export function compareSteps(readings, certified, tolerance = TOLERANCE) {
  requireArray(readings, 'readings');
  requireArray(certified, 'certified densities');
  requirePositive(tolerance, 'tolerance');
  if (readings.length === 0) {
    throw new BadInput('a calibration needs at least one step', { readings });
  }
  if (readings.length !== certified.length) {
    throw new BadInput('there is a reading without a certified step to match it', {
      readings: readings.length,
      certified: certified.length,
    });
  }

  const steps = readings.map((reading, index) => {
    const expected = certified[index];
    if (typeof reading !== 'number' || typeof expected !== 'number') {
      throw new BadInput(`step ${index + 1} is not a pair of numbers`, { reading, expected });
    }
    const error = round(reading - expected, 4);
    return {
      step: index + 1,
      certified: expected,
      read: reading,
      error,
      withinTolerance: Math.abs(error) <= tolerance + 1e-9,
    };
  });

  const worst = steps.reduce((a, b) => (Math.abs(b.error) > Math.abs(a.error) ? b : a));
  return {
    steps,
    tolerance,
    pass: steps.every((step) => step.withinTolerance),
    worstStep: worst.step,
    worstError: worst.error,
    largestError: round(maxOf(steps.map((step) => Math.abs(step.error))), 4),
  };
}

/**
 * A calibration record. The expiry is stored rather than computed on read, so
 * a record printed a year later still says what it said on the day.
 */
export function calibrationRecord({ instrument, day, readings, certified, tolerance }) {
  requireDay(day, 'calibration date');
  if (typeof instrument !== 'string' || instrument.trim() === '') {
    throw new BadInput('a calibration names the instrument it was done on', { instrument });
  }
  const comparison = compareSteps(readings, certified, tolerance);
  return {
    instrument: instrument.trim(),
    day,
    expires: addDays(day, VALID_DAYS),
    ...comparison,
  };
}

/** Whether a calibration is still good on a given day. */
export function calibrationCurrent(record, onDay) {
  requireDay(onDay, 'date');
  if (!record.pass) return false;
  return daysBetween(record.day, onDay) >= 0 && onDay <= record.expires;
}

export function daysUntilExpiry(record, onDay) {
  requireDay(onDay, 'date');
  return daysBetween(onDay, record.expires);
}

/**
 * Read a density off the wedge between two steps. The steps are close enough
 * together that a straight line between them is good to well inside the
 * tolerance, so this does not pretend to a curve it cannot justify.
 */
export function interpolateStep(certified, value) {
  requireArray(certified, 'certified densities');
  if (certified.length < 2) {
    throw new BadInput('interpolating a wedge needs at least two steps', { certified });
  }
  const first = certified[0];
  const last = certified[certified.length - 1];
  if (value < Math.min(first, last) || value > Math.max(first, last)) {
    throw new BadInput('that density is off the end of the wedge', { value, first, last });
  }
  for (let i = 0; i < certified.length - 1; i += 1) {
    const low = certified[i];
    const high = certified[i + 1];
    if (value >= Math.min(low, high) && value <= Math.max(low, high)) {
      const span = high - low;
      const into = span === 0 ? 0 : (value - low) / span;
      return round(i + 1 + into, 3);
    }
  }
  throw new BadInput('that density falls between no two steps', { value });
}
