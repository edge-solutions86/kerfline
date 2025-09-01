import { BadInput } from '../errors.js';
import { round } from '../util/num.js';
import { requireAbove, requireWhole } from '../util/guard.js';
import { viewingPlan } from './viewing.js';
import { resolveDistances } from './distance.js';
import { unsharpnessLimit, checkUnsharpness, minimumSourceToObject } from './unsharpness.js';
import { exposuresForCircumference, requiredDistanceForShots, shotPlan } from './coverage.js';

/**
 * Working out where the source goes.
 *
 * The crew's question is never "what is the unsharpness at 24 inches". It is
 * "how far out do I have to crank it", and the answer is governed by two
 * things at once. Cranking out improves the unsharpness and it also widens
 * what one shot covers, so both constraints push the same way and the answer
 * is whichever of them is further out.
 *
 * That both push the same way is worth stating, because it is what makes the
 * answer a maximum of two numbers rather than a search. It also means there
 * is no trade to make: a setup that fails unsharpness is not fixed by
 * accepting more shots, and one that needs too many shots is not fixed by
 * accepting a blurrier film.
 */

/** The standoff the viewing technique implies, which is not always the wall. */
export function standoffIn({ odIn, wallIn, technique }) {
  requireAbove(odIn, 0, 'outside diameter');
  requireAbove(wallIn, 0, 'wall thickness');
  // A double wall shot puts the film on the far side of the pipe, so the
  // standoff is the whole diameter. A source inside sits against the wall.
  return technique.startsWith('SWSI') ? round(wallIn, 4) : round(odIn, 4);
}

/** The closest the source can be and still hold the unsharpness limit. */
export function distanceForUnsharpness({ odIn, wallIn, sourceSizeIn, sourceCanEnter = false }) {
  const viewing = viewingPlan({ odIn, wallIn, sourceCanEnter });
  const objectToFilmIn = standoffIn({ odIn, wallIn, technique: viewing.technique });
  const limitIn = unsharpnessLimit(viewing.penetratedThicknessIn);
  return {
    technique: viewing.technique,
    objectToFilmIn,
    limitIn,
    minimumSourceToObjectIn: round(
      minimumSourceToObject({ sourceSizeIn, objectToFilmIn, limitIn }),
      2
    ),
  };
}

/**
 * The shortest crank that satisfies both constraints. Returns which of the
 * two governed, because that is what tells a crew whether to fetch a smaller
 * source or accept more shots when the answer is impractical.
 */
export function shortestWorkingDistance({
  odIn,
  wallIn,
  sourceSizeIn,
  maxShots = 4,
  maxThicknessRatio = 1.2,
  sourceCanEnter = false,
}) {
  requireWhole(maxShots, 'shot count');
  requireAbove(maxShots, 0, 'shot count');
  const sharpness = distanceForUnsharpness({ odIn, wallIn, sourceSizeIn, sourceCanEnter });

  let forShots = null;
  let shotsReachable = true;
  try {
    forShots = requiredDistanceForShots({ odIn, shots: maxShots, maxThicknessRatio });
  } catch {
    shotsReachable = false;
  }

  if (!shotsReachable) {
    return {
      ...sharpness,
      shotsReachable: false,
      requiredForShotsIn: null,
      sourceToObjectIn: null,
      governedBy: 'coverage',
      reason: `${maxShots} shots cannot cover this joint at any distance with a ${maxThicknessRatio} ratio`,
    };
  }

  const governed = Math.max(sharpness.minimumSourceToObjectIn, forShots);
  return {
    ...sharpness,
    shotsReachable: true,
    requiredForShotsIn: forShots,
    sourceToObjectIn: round(governed, 2),
    governedBy: governed === forShots ? 'coverage' : 'unsharpness',
    reason: null,
  };
}

/**
 * The whole setup, resolved. Takes whatever distances the caller has and
 * fills in the rest, then checks the two constraints against what it ended up
 * with rather than against what was asked for.
 */
export function resolveSetup(input) {
  if (typeof input !== 'object' || input === null) {
    throw new BadInput('a setup is resolved from an object', { got: input });
  }
  const {
    odIn,
    wallIn,
    sourceSizeIn,
    maxThicknessRatio = 1.2,
    sourceCanEnter = false,
    ...distances
  } = input;

  const viewing = viewingPlan({ odIn, wallIn, sourceCanEnter });
  const objectToFilmIn =
    distances.objectToFilmIn ?? standoffIn({ odIn, wallIn, technique: viewing.technique });
  const resolved = resolveDistances({ ...distances, objectToFilmIn });

  const sharpness = checkUnsharpness({
    sourceSizeIn,
    objectToFilmIn: resolved.objectToFilmIn,
    sourceToObjectIn: resolved.sourceToObjectIn,
    materialThicknessIn: viewing.penetratedThicknessIn,
  });

  const shots = exposuresForCircumference({
    odIn,
    sourceToObjectIn: resolved.sourceToObjectIn,
    maxThicknessRatio,
  });

  return {
    viewing,
    distances: resolved,
    sharpness,
    shots,
    plan: shotPlan({ odIn, sourceToObjectIn: resolved.sourceToObjectIn, maxThicknessRatio }),
    workable: sharpness.pass,
  };
}
