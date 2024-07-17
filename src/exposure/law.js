import { BadInput } from '../errors.js';
import { isFiniteNumber } from '../util/num.js';

/**
 * The two laws every exposure calculation goes through.
 *
 * Intensity falls with the square of the distance, so the time needed to lay
 * down the same exposure rises with the square of the distance. Those are the
 * same statement and people still get the direction wrong under a truck at
 * two in the morning, which is why they are two named functions here rather
 * than one exponent used in both directions.
 *
 * Exposure itself is held in curie minutes for a gamma source and in
 * milliampere minutes for an X-ray head. Both are a rate multiplied by a
 * time, and both assume reciprocity holds, which it does over the range this
 * shop works in.
 */

export function intensityAtDistance(intensity, fromIn, toIn) {
  requireAtLeast(intensity, 0, 'intensity');
  requireAbove(fromIn, 0, 'reference distance');
  requireAbove(toIn, 0, 'new distance');
  return intensity * (fromIn / toIn) ** 2;
}

/** Time needed at a new distance to lay down the same exposure. */
export function timeAtDistance(minutes, fromIn, toIn) {
  requireAtLeast(minutes, 0, 'exposure time');
  requireAbove(fromIn, 0, 'reference distance');
  requireAbove(toIn, 0, 'new distance');
  return minutes * (toIn / fromIn) ** 2;
}

/** The distance at which a stated time gives the same exposure. */
export function distanceForTime(minutes, fromIn, newMinutes) {
  requireAbove(minutes, 0, 'reference exposure time');
  requireAbove(fromIn, 0, 'reference distance');
  requireAtLeast(newMinutes, 0, 'new exposure time');
  return fromIn * Math.sqrt(newMinutes / minutes);
}

export function curieMinutes(activityCi, minutes) {
  requireAbove(activityCi, 0, 'source activity');
  requireAtLeast(minutes, 0, 'exposure time');
  return activityCi * minutes;
}

export function minutesForCurieMinutes(curieMinutesWanted, activityCi) {
  requireAtLeast(curieMinutesWanted, 0, 'curie minutes');
  requireAbove(activityCi, 0, 'source activity');
  return curieMinutesWanted / activityCi;
}

export function milliampereMinutes(milliamps, minutes) {
  requireAbove(milliamps, 0, 'tube current');
  requireAtLeast(minutes, 0, 'exposure time');
  return milliamps * minutes;
}

export function minutesForMilliampereMinutes(wanted, milliamps) {
  requireAtLeast(wanted, 0, 'milliampere minutes');
  requireAbove(milliamps, 0, 'tube current');
  return wanted / milliamps;
}

/**
 * Move an exposure from the distance a chart was drawn at to the distance the
 * crew can actually set up at. This is the single most used function in the
 * package and the one worth having exactly one copy of.
 */
export function scaleExposure({ curieMinutesAt, chartDistanceIn, actualDistanceIn }) {
  requireAtLeast(curieMinutesAt, 0, 'chart exposure');
  requireAbove(chartDistanceIn, 0, 'chart distance');
  requireAbove(actualDistanceIn, 0, 'actual distance');
  return curieMinutesAt * (actualDistanceIn / chartDistanceIn) ** 2;
}

/**
 * Two setups that give the same exposure. Useful as a check on a technique
 * sheet somebody has already filled in by hand.
 */
export function equivalentExposure(a, b, tolerance = 0.02) {
  const left = curieMinutes(a.activityCi, a.minutes) / a.distanceIn ** 2;
  const right = curieMinutes(b.activityCi, b.minutes) / b.distanceIn ** 2;
  requireAtLeast(tolerance, 0, 'tolerance');
  const larger = Math.max(left, right);
  if (larger === 0) return true;
  return Math.abs(left - right) / larger <= tolerance;
}

function requireAbove(value, floor, what) {
  requireNumber(value, what);
  if (value <= floor) {
    throw new BadInput(`${what} must be greater than ${floor}`, { got: value });
  }
  return value;
}

function requireAtLeast(value, floor, what) {
  requireNumber(value, what);
  if (value < floor) {
    throw new BadInput(`${what} must not be below ${floor}`, { got: value });
  }
  return value;
}

function requireNumber(value, what) {
  if (!isFiniteNumber(value)) {
    throw new BadInput(`${what} must be a finite number`, { got: value });
  }
  return value;
}
