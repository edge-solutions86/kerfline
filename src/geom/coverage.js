import { BadInput } from '../errors.js';
import { round } from '../util/num.js';
import { outsideRadius, clockFromDegrees } from './pipe.js';
import { requireAbove, requireNumber } from '../util/guard.js';

/**
 * How much of a circumferential weld one shot actually covers.
 *
 * Straight under the source the beam crosses the wall square on. Move round
 * the pipe and the same beam crosses at an angle, so it passes through more
 * metal and the film there is lighter than it should be. The technique
 * procedure caps that increase, and the cap is what decides how many shots go
 * round the joint.
 *
 * With the pipe centre at the origin, outer radius R and the source at
 * distance L from the centre, a point at angle phi off the source axis sees
 * the beam arrive at an incidence angle b where
 *
 *     cos b = (L cos phi - R) / sqrt(R^2 + L^2 - 2 R L cos phi)
 *
 * and the ray crosses about t / cos b of wall. The tangent point sits at
 * phi = acos(R / L); past there the beam misses the pipe altogether.
 */

const DEG = 180 / Math.PI;

export function incidenceAngleDegrees({ odIn, sourceToObjectIn, offsetDegrees }) {
  const { radius, axis } = geometry(odIn, sourceToObjectIn);
  requireNumber(offsetDegrees, 'offset in degrees');
  const phi = Math.abs(offsetDegrees) / DEG;
  const tangent = Math.acos(radius / axis);
  if (phi >= tangent) {
    throw new BadInput('that offset is past the tangent point, the beam misses the pipe', {
      offsetDegrees,
      tangentDegrees: round(tangent * DEG, 3),
    });
  }
  const numerator = axis * Math.cos(phi) - radius;
  const denominator = Math.sqrt(
    radius * radius + axis * axis - 2 * radius * axis * Math.cos(phi)
  );
  return Math.acos(numerator / denominator) * DEG;
}

/** How much longer the path through the wall is than the wall itself. */
export function thicknessRatio({ odIn, sourceToObjectIn, offsetDegrees }) {
  const beta = incidenceAngleDegrees({ odIn, sourceToObjectIn, offsetDegrees });
  return 1 / Math.cos(beta / DEG);
}

/** The offset at which the beam grazes the pipe and stops entering it. */
export function tangentOffsetDegrees(odIn, sourceToObjectIn) {
  const { radius, axis } = geometry(odIn, sourceToObjectIn);
  return Math.acos(radius / axis) * DEG;
}

/**
 * The largest offset either side of the film centre that stays inside the
 * allowed thickness increase. Solved by bisection because the relation is not
 * worth inverting by hand and the tangent point gives a hard bracket.
 */
export function maxHalfAngleDegrees({ odIn, sourceToObjectIn, maxThicknessRatio }) {
  requireAbove(maxThicknessRatio, 1, 'maximum thickness ratio');
  const tangent = tangentOffsetDegrees(odIn, sourceToObjectIn);
  const at = (offset) => thicknessRatio({ odIn, sourceToObjectIn, offsetDegrees: offset });

  let low = 0;
  let high = tangent * 0.999999;
  if (at(high) <= maxThicknessRatio) return round(high, 4);
  for (let i = 0; i < 80; i += 1) {
    const mid = (low + high) / 2;
    if (at(mid) <= maxThicknessRatio) low = mid;
    else high = mid;
  }
  return round(low, 4);
}

/**
 * Shots needed to walk the whole joint. Each shot covers twice the half
 * angle, and the count is rounded up because a fraction of a shot leaves a
 * gap in the coverage that nobody signs for.
 */
export function exposuresForCircumference({ odIn, sourceToObjectIn, maxThicknessRatio }) {
  const half = maxHalfAngleDegrees({ odIn, sourceToObjectIn, maxThicknessRatio });
  if (half <= 0) {
    throw new BadInput('no offset meets that thickness ratio', { maxThicknessRatio });
  }
  return Math.max(1, Math.ceil(360 / (2 * half)));
}

/**
 * The shot plan: where each film centre sits, evenly spread, given as both
 * degrees and the clock position that goes on the lead tape.
 */
export function shotPlan({ odIn, sourceToObjectIn, maxThicknessRatio, startDegrees = 0 }) {
  requireNumber(startDegrees, 'start position in degrees');
  const count = exposuresForCircumference({ odIn, sourceToObjectIn, maxThicknessRatio });
  const step = 360 / count;
  const half = maxHalfAngleDegrees({ odIn, sourceToObjectIn, maxThicknessRatio });
  return Array.from({ length: count }, (_unused, index) => {
    const centre = startDegrees + index * step;
    return {
      shot: index + 1,
      centreDegrees: round(((centre % 360) + 360) % 360, 3),
      clock: clockFromDegrees(centre),
      coversDegrees: round(Math.min(step, 2 * half), 3),
      overlapDegrees: round(Math.max(0, 2 * half - step), 3),
    };
  });
}

/**
 * A source cranked out far enough behaves almost like a parallel beam, so
 * this is the distance at which one shot would cover the whole joint. It is
 * always unreachable on a real pipe and the function says so by refusing,
 * which is more useful than returning a number nobody can set up.
 */
export function requiredDistanceForShots({ odIn, shots, maxThicknessRatio }) {
  if (!Number.isInteger(shots) || shots < 1) {
    throw new BadInput('shot count must be a whole number of shots', { shots });
  }
  const wanted = 360 / (2 * shots);
  let low = 0.01;
  let high = odIn * 1000;
  const covers = (sod) =>
    maxHalfAngleDegrees({ odIn, sourceToObjectIn: sod, maxThicknessRatio });
  if (covers(high) < wanted) {
    throw new BadInput('that many shots cannot cover the joint at any distance', {
      shots,
      maxThicknessRatio,
    });
  }
  for (let i = 0; i < 80; i += 1) {
    const mid = (low + high) / 2;
    if (covers(mid) >= wanted) high = mid;
    else low = mid;
  }
  return round(high, 3);
}

function geometry(odIn, sourceToObjectIn) {
  const radius = outsideRadius(odIn);
  requireAbove(sourceToObjectIn, 0, 'source to object distance');
  return { radius, axis: radius + sourceToObjectIn };
}
