import { NotFound } from '../errors.js';
import { round } from '../util/num.js';
import { requireAbove, requireAtLeast } from '../util/guard.js';
import { doseRateAt } from './doserate.js';
import { barricadeDistanceFt, restrictedAreaRate } from './boundary.js';

/**
 * Collimators.
 *
 * A collimator is a lead sleeve that lets the beam out in the direction the
 * film is and stops most of it going anywhere else. It does not make the shot
 * safer in the beam; it makes the ninety percent of the sphere that is not
 * the beam very much quieter, and on a plant job that is the difference
 * between roping off a unit and roping off a bay.
 *
 * The attenuation figures below are the shop's own, measured with a meter
 * behind each sleeve on a known source. They are deliberately conservative,
 * and the boundary they produce is still surveyed like any other, because a
 * collimator that has been dropped is a collimator with a crack in it and no
 * calculation can see that.
 */

const COLLIMATORS = {
  NONE: {
    label: 'bare source',
    beamDegrees: 360,
    outsideFactor: 1,
    weightLb: 0,
  },
  PARTIAL: {
    label: 'partial sleeve',
    beamDegrees: 120,
    outsideFactor: 0.1,
    weightLb: 14,
  },
  DIRECTIONAL: {
    label: 'directional cone',
    beamDegrees: 60,
    outsideFactor: 0.03,
    weightLb: 26,
  },
};

export function collimatorTypes() {
  return Object.keys(COLLIMATORS);
}

export function collimator(name) {
  const found = COLLIMATORS[name];
  if (!found) {
    throw new NotFound(`no collimator called ${name}`, {
      asked: name,
      known: collimatorTypes(),
    });
  }
  return { name, ...found };
}

/** True when a bearing falls inside the open side of the sleeve. */
export function inBeam(name, offsetDegrees) {
  const sleeve = collimator(name);
  const wrapped = Math.abs(((offsetDegrees % 360) + 360) % 360);
  const off = wrapped > 180 ? 360 - wrapped : wrapped;
  return off <= sleeve.beamDegrees / 2;
}

/** The multiplier on the bare dose rate at a bearing. */
export function factorAt(name, offsetDegrees) {
  return inBeam(name, offsetDegrees) ? 1 : collimator(name).outsideFactor;
}

/** The rate at a distance and a bearing with the sleeve fitted. */
export function collimatedRateAt({
  isotopeId,
  activityCi,
  distanceFt,
  collimator: name,
  offsetDegrees,
}) {
  const bare = doseRateAt({ isotopeId, activityCi, distanceFt });
  return round(bare * factorAt(name, offsetDegrees), 4);
}

/**
 * The boundary in each direction. The beam side is unchanged, because a
 * collimator does nothing there, and that is the part crews forget: the rope
 * still goes a long way out in front of the camera.
 */
export function collimatedBoundaryFt({
  isotopeId,
  activityCi,
  collimator: name,
  margin = 1.2,
}) {
  const sleeve = collimator(name);
  const bare = barricadeDistanceFt({ isotopeId, activityCi, margin });
  return {
    collimator: sleeve.name,
    beamDegrees: sleeve.beamDegrees,
    inBeamFt: bare,
    outsideBeamFt: round(bare * Math.sqrt(sleeve.outsideFactor), 1),
    limitMrPerHour: restrictedAreaRate(),
  };
}

/**
 * The smallest sleeve that gets the boundary outside the beam into the space
 * available. Returns null when even the best one will not do it, which means
 * the job needs shielding or a smaller source rather than a different sleeve.
 */
export function smallestFitting({ isotopeId, activityCi, availableFt, margin = 1.2 }) {
  requireAbove(availableFt, 0, 'available space');
  requireAtLeast(margin, 1, 'margin');
  const ranked = collimatorTypes()
    .map((name) => ({ name, ...collimator(name) }))
    .sort((a, b) => a.weightLb - b.weightLb);
  for (const sleeve of ranked) {
    const bounds = collimatedBoundaryFt({
      isotopeId,
      activityCi,
      collimator: sleeve.name,
      margin,
    });
    if (bounds.outsideBeamFt <= availableFt) {
      return { ...bounds, weightLb: sleeve.weightLb };
    }
  }
  return null;
}

/**
 * How much of the sphere a sleeve quietens, as a fraction. Useful for saying
 * plainly what a collimator buys, which is not "half the dose" but "most of
 * the directions".
 */
export function quietFraction(name) {
  const sleeve = collimator(name);
  return round(1 - sleeve.beamDegrees / 360, 4);
}
