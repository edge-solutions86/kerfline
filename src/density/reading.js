import { BadInput, Conflict } from '../errors.js';
import { round, mean, maxOf, minOf } from '../util/num.js';
import { requireArray, requireAtLeast, requireLabel } from '../util/guard.js';
import { requireDay } from '../util/dates.js';
import { calibrationCurrent } from './stepwedge.js';
import { judgeDensity } from './limits.js';
import { judgeArea } from './variation.js';

/**
 * A set of density readings off one film.
 *
 * The readings are only worth what the instrument that took them is worth, so
 * a set carries the calibration it was taken under and refuses to be built on
 * an instrument that was out of calibration on the day. That is a stricter
 * rule than it sounds: a densitometer whose ninety days ran out yesterday
 * produces readings that look exactly like good ones, and the only thing that
 * separates them is this check.
 *
 * The reading at the indicator is kept apart from the readings through the
 * area of interest, because the first is the reference the second is judged
 * against and mixing them into one list loses that.
 */

export function makeReadingSet({
  film,
  day,
  instrument,
  calibration,
  indicatorDensity,
  area = [],
  sourceKind = 'gamma',
}) {
  requireDay(day, 'reading date');
  requireLabel(film, 'a reading set names the film it came off');
  requireLabel(instrument, 'a reading set names the instrument');
  requireArray(area, 'area readings');
  requireAtLeast(indicatorDensity, 0, 'indicator density');

  if (calibration) {
    if (calibration.instrument !== instrument.trim()) {
      throw new Conflict('the calibration on file is for a different instrument', {
        instrument: instrument.trim(),
        calibration: calibration.instrument,
      });
    }
    const current = calibrationCurrent(calibration, day);
    if (!current) {
      throw new Conflict(`${instrument.trim()} was not in calibration on ${day}`, {
        instrument: instrument.trim(),
        expires: calibration.expires,
        day,
      });
    }
  }

  const readings = area.map((reading, index) => {
    const density = typeof reading === 'object' && reading !== null ? reading.density : reading;
    const at = typeof reading === 'object' && reading !== null ? (reading.at ?? null) : null;
    if (typeof density !== 'number' || !(density > 0)) {
      throw new BadInput(`area reading ${index + 1} has no usable density`, { got: reading });
    }
    return { at, density: round(density, 2) };
  });

  return {
    film: film.trim(),
    day,
    instrument: instrument.trim(),
    calibrated: Boolean(calibration),
    sourceKind,
    indicatorDensity: round(indicatorDensity, 2),
    area: readings,
  };
}

/** The shape of the film, as numbers a report can print. */
export function profile(set) {
  if (set.area.length === 0) {
    return {
      readings: 0,
      lightestDensity: set.indicatorDensity,
      darkestDensity: set.indicatorDensity,
      meanDensity: set.indicatorDensity,
      spread: 0,
    };
  }
  const values = set.area.map((reading) => reading.density);
  const lightest = minOf(values);
  const darkest = maxOf(values);
  return {
    readings: values.length,
    lightestDensity: lightest,
    darkestDensity: darkest,
    meanDensity: round(mean(values), 2),
    spread: round(darkest - lightest, 2),
  };
}

/** Judge the set: the reference against the code, then the area against it. */
export function judgeSet(set) {
  const reference = judgeDensity(set.indicatorDensity, set.sourceKind);
  const area = set.area.length === 0 ? null : judgeArea(set.area, set.indicatorDensity);
  return {
    film: set.film,
    reference,
    area,
    pass: reference.pass && (area === null || area.pass),
    calibrated: set.calibrated,
  };
}

/** The readings that put the film outside the variation band. */
export function failingReadings(set) {
  const judged = judgeSet(set);
  return judged.area === null ? [] : judged.area.failures;
}

/**
 * Compare two sets of the same film taken on different instruments. Used when
 * a client disputes a reading, which is the only time anybody does it.
 */
export function compareSets(a, b, tolerance = 0.05) {
  requireAtLeast(tolerance, 0, 'tolerance');
  if (a.film !== b.film) {
    throw new BadInput('those two sets are off different films', { a: a.film, b: b.film });
  }
  const difference = round(Math.abs(a.indicatorDensity - b.indicatorDensity), 3);
  return {
    film: a.film,
    instruments: [a.instrument, b.instrument],
    difference,
    agree: difference <= tolerance,
  };
}
