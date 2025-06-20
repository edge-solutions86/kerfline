import { BadInput } from '../errors.js';
import { round } from '../util/num.js';
import { requireAbove } from '../util/guard.js';
import { impliedHalfValueLayerIn } from './chart.js';
import { filmGradient, readableDensityRange } from './aim.js';
import { filmSpeed, film } from './filmclass.js';

/**
 * Film latitude.
 *
 * A single shot covers a band of thickness, not a single thickness. The
 * thickest part of the section lands at the light end of the readable density
 * range and the thinnest at the dark end, and how wide that band is falls out
 * of two things already in this package: how fast the exposure climbs with
 * thickness, which is the chart, and how fast density climbs with exposure,
 * which is the film gradient.
 *
 *     dD = gradient * log10(2) * dt / HVL
 *
 * so the thickness a density band covers is
 *
 *     dt = dD * HVL / (gradient * log10(2))
 *
 * A fine grained film has a steeper gradient, so it has LESS latitude, not
 * more. That is the trade nobody remembers: the film that resolves the most
 * covers the least section in one shot.
 */

const LOG10_2 = Math.log10(2);

/** The thickness band one shot covers on a film, at a source. */
export function latitudeIn({ isotopeId, filmId, densitySpan }) {
  const hvl = impliedHalfValueLayerIn(isotopeId);
  const gradient = filmGradient(filmId);
  const span = densitySpan ?? readableSpan();
  requireAbove(span, 0, 'density span');
  return round((span * hvl) / (gradient * LOG10_2), 4);
}

function readableSpan() {
  const range = readableDensityRange();
  return range.maximum - range.minimum;
}

/** The band of thickness a shot aimed at one thickness actually covers. */
export function coveredBand({ isotopeId, filmId, thicknessIn, densitySpan }) {
  requireAbove(thicknessIn, 0, 'thickness');
  const width = latitudeIn({ isotopeId, filmId, densitySpan });
  return {
    thicknessIn,
    latitudeIn: width,
    minimumIn: round(Math.max(0, thicknessIn - width / 2), 4),
    maximumIn: round(thicknessIn + width / 2, 4),
  };
}

/** Whether one shot can cover a section that varies in thickness. */
export function coversRange({ isotopeId, filmId, minimumIn, maximumIn, densitySpan }) {
  requireAbove(minimumIn, 0, 'thinnest section');
  requireAbove(maximumIn, 0, 'thickest section');
  if (maximumIn < minimumIn) {
    throw new BadInput('the thickest section is thinner than the thinnest', {
      minimumIn,
      maximumIn,
    });
  }
  const needed = round(maximumIn - minimumIn, 4);
  const available = latitudeIn({ isotopeId, filmId, densitySpan });
  return {
    neededIn: needed,
    availableIn: available,
    covers: needed <= available,
    shortIn: needed <= available ? 0 : round(needed - available, 4),
  };
}

/**
 * Two films in one cassette, a slow one and a fast one. The pair covers a
 * wider band than either alone, because the thin end is read on the slow film
 * and the thick end on the fast one, and the widening is the ratio of their
 * speeds expressed as density.
 */
export function doubleLoadBand({ isotopeId, slowFilmId, fastFilmId, densitySpan }) {
  const slow = film(slowFilmId);
  const fast = film(fastFilmId);
  if (filmSpeed(fast.id) <= filmSpeed(slow.id)) {
    throw new BadInput('the second film has to be faster than the first', {
      slow: slow.id,
      fast: fast.id,
    });
  }
  const hvl = impliedHalfValueLayerIn(isotopeId);
  const slowBand = latitudeIn({ isotopeId, filmId: slow.id, densitySpan });
  const fastBand = latitudeIn({ isotopeId, filmId: fast.id, densitySpan });
  const offset = round(
    (Math.log10(filmSpeed(fast.id) / filmSpeed(slow.id)) * hvl) / LOG10_2,
    4
  );
  return {
    slow: slow.id,
    fast: fast.id,
    slowLatitudeIn: slowBand,
    fastLatitudeIn: fastBand,
    offsetIn: offset,
    combinedIn: round(Math.max(slowBand, fastBand) + offset, 4),
  };
}

/**
 * The films that will cover a section in one shot, finest first. Finest is
 * the right order because the finest film that covers the range is the one
 * that shows the most, and latitude is a constraint to satisfy rather than a
 * quantity to maximise.
 */
export function filmsCovering({ isotopeId, minimumIn, maximumIn, densitySpan }) {
  const needed = maximumIn - minimumIn;
  return ['D2', 'D3', 'D4', 'D5', 'D7', 'D8']
    .map((filmId) => ({ filmId, latitudeIn: latitudeIn({ isotopeId, filmId, densitySpan }) }))
    .filter((entry) => entry.latitudeIn >= needed);
}
