import { round } from '../util/num.js';
import { requireAbove, requireAtLeast } from '../util/guard.js';
import { isotope } from '../isotope/catalog.js';
import { transmission } from '../isotope/attenuation.js';
import { convert } from '../util/units.js';

/**
 * Dose rate from an unshielded source, and what a shield does to it.
 *
 * The gamma ray constant in the catalog is roentgen per hour at one metre per
 * curie, so the bare relation is
 *
 *     rate = constant * activity / distance^2
 *
 * with distance in metres. Field work is in feet, so the conversion happens
 * here rather than in anybody's head. One foot is 0.3048 m and the square of
 * that is 0.0929, which is why a source reads about eleven times hotter at a
 * foot than at a metre.
 *
 * These are narrow beam numbers with no build up factor. Behind a thick
 * shield the real reading will be higher than this says, so a barricade set
 * from the shielded figure alone gets surveyed before anybody trusts it. That
 * is not a caveat on the arithmetic, it is the reason the survey exists.
 */

const M_PER_FT = 0.3048;

/** Rate in milliroentgen per hour at a distance in feet. */
export function doseRateAt({ isotopeId, activityCi, distanceFt }) {
  const source = isotope(isotopeId);
  requireAbove(activityCi, 0, 'source activity');
  requireAbove(distanceFt, 0, 'distance');
  const metres = distanceFt * M_PER_FT;
  const roentgenPerHour = (source.gammaFactor * activityCi) / (metres * metres);
  return round(roentgenPerHour * 1000, 3);
}

/** The same at a distance given in metres, for a procedure written that way. */
export function doseRateAtMetres({ isotopeId, activityCi, distanceM }) {
  requireAbove(distanceM, 0, 'distance');
  return doseRateAt({ isotopeId, activityCi, distanceFt: distanceM / M_PER_FT });
}

/** The distance at which the source reads a given rate. */
export function distanceForRateFt({ isotopeId, activityCi, rateMrPerHour }) {
  const source = isotope(isotopeId);
  requireAbove(activityCi, 0, 'source activity');
  requireAbove(rateMrPerHour, 0, 'dose rate');
  const roentgenPerHour = rateMrPerHour / 1000;
  const metres = Math.sqrt((source.gammaFactor * activityCi) / roentgenPerHour);
  return round(metres / M_PER_FT, 2);
}

/** Rate behind a shield, narrow beam, no build up. */
export function shieldedRateAt({ isotopeId, activityCi, distanceFt, material, thicknessMm }) {
  const bare = doseRateAt({ isotopeId, activityCi, distanceFt });
  requireAtLeast(thicknessMm, 0, 'shield thickness');
  return round(bare * transmission(isotopeId, material, thicknessMm), 4);
}

/**
 * The dose somebody picks up standing at a distance for a while. Exposure
 * time is in minutes because that is what the shot log records.
 */
export function doseForExposure({ isotopeId, activityCi, distanceFt, minutes }) {
  requireAtLeast(minutes, 0, 'exposure time');
  const rate = doseRateAt({ isotopeId, activityCi, distanceFt });
  return round((rate * minutes) / 60, 4);
}

/** The same, converted to the unit a dosimeter reads in. */
export function doseForExposureMicroSievert(args) {
  return round(convert(doseForExposure(args), 'mR/h', 'uSv/h'), 3);
}

/**
 * How long somebody may stand at a distance before reaching a dose limit.
 * Returns Infinity when the rate is already below anything that accumulates,
 * which the caller has to handle rather than printing.
 */
export function minutesToDose({ isotopeId, activityCi, distanceFt, limitMr }) {
  requireAbove(limitMr, 0, 'dose limit');
  const rate = doseRateAt({ isotopeId, activityCi, distanceFt });
  if (rate === 0) return Infinity;
  return round((limitMr / rate) * 60, 2);
}

/**
 * Compare two sources at the same distance. Useful when a crew is deciding
 * which camera to take on a job with a tight boundary.
 */
export function compareSources({ isotopeIds, activityCi, distanceFt }) {
  return isotopeIds
    .map((isotopeId) => ({
      isotope: isotope(isotopeId).id,
      rateMrPerHour: doseRateAt({ isotopeId, activityCi, distanceFt }),
    }))
    .sort((a, b) => a.rateMrPerHour - b.rateMrPerHour);
}
