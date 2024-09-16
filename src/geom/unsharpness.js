import { round } from '../util/num.js';
import { bandTable, bandValue } from '../util/band.js';
import { requireAbove, requireAtLeast } from '../util/guard.js';

/**
 * Geometric unsharpness. The source is not a point, so every edge in the weld
 * is projected onto the film as a band rather than as a line:
 *
 *     Ug = F t / d
 *
 * with F the source size, t the distance from the back of the object to the
 * film, and d the distance from the source to the front of the object. The
 * limit is banded on material thickness and the bands are wider than people
 * expect, which is why the check is worth doing rather than eyeballing.
 */

const LIMIT_TABLE = bandTable(
  [
    { upTo: 2, value: 0.02 },
    { upTo: 3, value: 0.03 },
    { upTo: 4, value: 0.04 },
    { upTo: Infinity, value: 0.07 },
  ],
  { name: 'unsharpness limit' }
);

/**
 * The limit in inches for a material thickness in inches. The thickness meant
 * here is the one the image quality indicator is chosen on, not the sum of
 * both walls on a double wall shot, and getting that wrong moves the limit by
 * a whole band on anything over an inch.
 */
export function unsharpnessLimit(materialThicknessIn) {
  requireAbove(materialThicknessIn, 0, 'material thickness');
  return bandValue(LIMIT_TABLE, materialThicknessIn);
}

export function unsharpnessBands() {
  return LIMIT_TABLE.rows.map((row) => ({
    over: row.over,
    upTo: row.upTo,
    limitIn: row.value,
  }));
}

export function geometricUnsharpness({ sourceSizeIn, objectToFilmIn, sourceToObjectIn }) {
  requireAbove(sourceSizeIn, 0, 'source size');
  requireAtLeast(objectToFilmIn, 0, 'object to film distance');
  requireAbove(sourceToObjectIn, 0, 'source to object distance');
  return (sourceSizeIn * objectToFilmIn) / sourceToObjectIn;
}

/**
 * The shortest source to object distance that still meets a limit. This is
 * the number a crew actually wants, because the source size and the standoff
 * are fixed by the camera and the pipe and the only free variable is how far
 * back the crank goes.
 */
export function minimumSourceToObject({ sourceSizeIn, objectToFilmIn, limitIn }) {
  requireAbove(sourceSizeIn, 0, 'source size');
  requireAtLeast(objectToFilmIn, 0, 'object to film distance');
  requireAbove(limitIn, 0, 'unsharpness limit');
  return (sourceSizeIn * objectToFilmIn) / limitIn;
}

/**
 * Evaluate a setup. Returns the computed unsharpness, the limit it was judged
 * against, and how much room is left, so a technique sheet can print all
 * three rather than a bare pass.
 */
export function checkUnsharpness(setup) {
  const { sourceSizeIn, objectToFilmIn, sourceToObjectIn, materialThicknessIn } = setup;
  const ug = geometricUnsharpness({ sourceSizeIn, objectToFilmIn, sourceToObjectIn });
  const limitIn = unsharpnessLimit(materialThicknessIn);
  const minimum = minimumSourceToObject({ sourceSizeIn, objectToFilmIn, limitIn });
  return {
    unsharpnessIn: round(ug, 4),
    limitIn,
    pass: ug <= limitIn,
    marginIn: round(limitIn - ug, 4),
    minimumSourceToObjectIn: round(minimum, 2),
    shortfallIn: ug <= limitIn ? 0 : round(minimum - sourceToObjectIn, 2),
  };
}
