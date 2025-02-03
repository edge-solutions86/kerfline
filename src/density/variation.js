import { BadInput } from '../errors.js';
import { round, percentChange } from '../util/num.js';
import { requireArray, requirePositive } from '../util/guard.js';

/**
 * The density variation rule.
 *
 * Density anywhere through the area of interest is judged against the density
 * through the body of the indicator, and it may fall no more than fifteen
 * percent below it or rise more than thirty percent above it. The asymmetry
 * is not arbitrary: a light patch hides indications and a dark one only makes
 * the film harder to read, so the rule is tighter on the side that loses
 * information.
 *
 * The thing worth being careful about is that the reference is a measurement
 * and not a constant. Two films of the same joint shot at different densities
 * have different allowed bands, and a reading that passes on one fails on the
 * other. Every function here takes the reference explicitly for that reason.
 */

const BELOW = 0.15;
const ABOVE = 0.3;

export function variationFractions() {
  return { below: BELOW, above: ABOVE };
}

/** The band a reading has to land in, given the density at the indicator. */
export function variationLimits(referenceDensity) {
  requirePositive(referenceDensity, 'reference density');
  return {
    reference: referenceDensity,
    minimum: round(referenceDensity * (1 - BELOW), 4),
    maximum: round(referenceDensity * (1 + ABOVE), 4),
  };
}

export function withinVariation(value, referenceDensity) {
  const limits = variationLimits(referenceDensity);
  return value >= limits.minimum && value <= limits.maximum;
}

/**
 * Judge one reading, saying which way it went and by how much. The percentage
 * is signed against the reference so a report can print it straight.
 */
export function judgeVariation(value, referenceDensity) {
  requirePositive(value, 'density reading');
  const limits = variationLimits(referenceDensity);
  const change = percentChange(referenceDensity, value);
  let verdict = 'accept';
  if (value < limits.minimum) verdict = 'too light';
  else if (value > limits.maximum) verdict = 'too dark';
  return {
    density: value,
    percentFromReference: round(change, 2),
    verdict,
    pass: verdict === 'accept',
    minimum: limits.minimum,
    maximum: limits.maximum,
  };
}

/**
 * Judge a whole area of interest. A single reading outside the band condemns
 * the film, but the report still carries every reading, because the pattern
 * of where it went wrong is what tells the crew whether to reshoot the joint
 * or add an exposure at the ends.
 */
export function judgeArea(readings, referenceDensity) {
  requireArray(readings, 'readings');
  if (readings.length === 0) {
    throw new BadInput('an area of interest needs at least one reading', { got: readings });
  }
  const judged = readings.map((reading, index) => {
    const value = typeof reading === 'object' && reading !== null ? reading.density : reading;
    const at = typeof reading === 'object' && reading !== null ? (reading.at ?? null) : null;
    if (typeof value !== 'number') {
      throw new BadInput(`reading ${index + 1} has no density on it`, { got: reading });
    }
    return { at, ...judgeVariation(value, referenceDensity) };
  });
  const failures = judged.filter((reading) => !reading.pass);
  return {
    reference: referenceDensity,
    readings: judged,
    pass: failures.length === 0,
    failures,
    lightest: judged.reduce((low, r) => (r.density < low.density ? r : low)),
    darkest: judged.reduce((high, r) => (r.density > high.density ? r : high)),
  };
}

/**
 * The extra exposures a failing area needs. A film that is light only at one
 * end is fixed by shooting that end again; one that is out at both ends is a
 * technique problem and the whole joint gets reshot.
 */
export function remedyFor(area) {
  if (area.pass) return { action: 'none', reshoot: false };
  const ends = area.failures.map((reading) => reading.at).filter((at) => at !== null);
  const distinct = new Set(ends);
  if (distinct.size === 0 || distinct.size > 2) {
    return { action: 'reshoot the joint', reshoot: true, at: [] };
  }
  return { action: 'add an exposure', reshoot: false, at: [...distinct] };
}
