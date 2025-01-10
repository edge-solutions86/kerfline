import { round, maxOf } from '../util/num.js';
import { requireAbove, requireAtLeast, requireArray } from '../util/guard.js';
import { doseRateAt, distanceForRateFt } from './doserate.js';

/**
 * Where the rope goes.
 *
 * Two boundaries matter on a radiography job. The high radiation boundary is
 * where the rate falls to 100 mR/h, and inside it nobody stands. The
 * restricted area boundary is where it falls to 2 mR/h, and that is the rope
 * the public is kept behind. The second one is much further out than people
 * expect: a forty curie iridium camera reads 2 mR/h at about three hundred
 * feet, which on a plant job is most of a unit.
 *
 * A calculated distance is a starting point and not the boundary. The
 * boundary is where the survey meter says it is, and the calculation exists
 * to tell the crew roughly where to start walking with the meter and to catch
 * the case where the job cannot be done at all in the space available.
 */

const HIGH_RADIATION_MR = 100;
const RESTRICTED_MR = 2;
const DEFAULT_MARGIN = 1.2;

export function highRadiationRate() {
  return HIGH_RADIATION_MR;
}

export function restrictedAreaRate() {
  return RESTRICTED_MR;
}

/** The two boundary distances for a source, before any margin is added. */
export function boundaryDistancesFt({ isotopeId, activityCi }) {
  return {
    highRadiationFt: distanceForRateFt({
      isotopeId,
      activityCi,
      rateMrPerHour: HIGH_RADIATION_MR,
    }),
    restrictedFt: distanceForRateFt({ isotopeId, activityCi, rateMrPerHour: RESTRICTED_MR }),
  };
}

/**
 * Where to put the rope, with the shop margin on the calculated distance. The
 * margin is not a safety factor on the physics, which is exact; it covers
 * scatter off surrounding steel, which this calculation does not model at
 * all.
 */
export function barricadeDistanceFt({ isotopeId, activityCi, margin = DEFAULT_MARGIN }) {
  requireAtLeast(margin, 1, 'margin');
  const { restrictedFt } = boundaryDistancesFt({ isotopeId, activityCi });
  return round(restrictedFt * margin, 1);
}

/** Whether a spot is inside a boundary. */
export function isInsideBoundary({
  isotopeId,
  activityCi,
  distanceFt,
  boundary = 'restricted',
}) {
  requireAbove(distanceFt, 0, 'distance');
  const rate = doseRateAt({ isotopeId, activityCi, distanceFt });
  const limit = boundary === 'high' ? HIGH_RADIATION_MR : RESTRICTED_MR;
  return rate > limit;
}

/**
 * Whether the job fits in the space there is. A shot that cannot get its
 * restricted boundary inside the available space needs shielding, a smaller
 * source, or the area cleared, and knowing which before the crew mobilises is
 * worth a lot.
 */
export function fitsInSpace({ isotopeId, activityCi, availableFt, margin = DEFAULT_MARGIN }) {
  requireAbove(availableFt, 0, 'available space');
  const needed = barricadeDistanceFt({ isotopeId, activityCi, margin });
  return {
    neededFt: needed,
    availableFt,
    fits: needed <= availableFt,
    shortFt: needed <= availableFt ? 0 : round(needed - availableFt, 1),
  };
}

/**
 * The largest activity that would fit a boundary into the space available.
 * Since rate goes as activity over distance squared, the activity scales with
 * the square of the distance the space allows.
 */
export function largestActivityFor({ isotopeId, availableFt, margin = DEFAULT_MARGIN }) {
  requireAbove(availableFt, 0, 'available space');
  requireAtLeast(margin, 1, 'margin');
  const trialCi = 10;
  const trialFt = barricadeDistanceFt({ isotopeId, activityCi: trialCi, margin });
  return round(trialCi * (availableFt / trialFt) ** 2, 2);
}

/**
 * The boundary for several sources being used at once. Rates add, so the
 * boundary for two cameras is not the further of the two boundaries and
 * taking it as such is the mistake this function exists to prevent.
 */
export function combinedBoundaryFt(sources, { margin = DEFAULT_MARGIN } = {}) {
  requireArray(sources, 'sources');
  if (sources.length === 0) {
    throw new Error('a combined boundary needs at least one source');
  }
  const singles = sources.map((source) => barricadeDistanceFt({ ...source, margin: 1 }));
  let low = maxOf(singles);
  let high = low * 10;
  const totalAt = (distanceFt) =>
    sources.reduce((sum, source) => sum + doseRateAt({ ...source, distanceFt }), 0);
  for (let i = 0; i < 80; i += 1) {
    const mid = (low + high) / 2;
    if (totalAt(mid) > RESTRICTED_MR) low = mid;
    else high = mid;
  }
  return round(high * margin, 1);
}
