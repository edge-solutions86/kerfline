import { OutOfRange } from '../errors.js';
import { round, interpolate, logInterpolate } from '../util/num.js';
import { requireAbove, requireAtLeast } from '../util/guard.js';
import { minutesForMilliampereMinutes, scaleExposure } from './law.js';

/**
 * The X-ray head.
 *
 * An isotope has one energy and the only variables are time and distance. A
 * tube has a third, the kilovoltage, and it does something neither of the
 * others does: it changes the contrast of the radiograph as well as the
 * density. Turning the kV up gets through the wall faster and produces a
 * flatter image, so the technique is always the lowest kV that will penetrate
 * in a workable time rather than the fastest one available.
 *
 * That is why kilovoltsFor returns the shop minimum for a thickness and not a
 * midpoint. Going above it is a decision somebody makes and writes down.
 */

const KV_CURVE = [
  [0.125, 100],
  [0.25, 120],
  [0.5, 150],
  [0.75, 180],
  [1, 200],
  [1.5, 250],
  [2, 300],
  [3, 400],
];

// Milliampere minutes at the reference distance and density on D7 film.
const MAMIN_CURVE = [
  [0.125, 4],
  [0.25, 6],
  [0.5, 9],
  [0.75, 12],
  [1, 15],
  [1.5, 22],
  [2, 30],
  [3, 48],
];

const REFERENCE = { distanceIn: 40, film: 'D7', density: 2 };
const MAX_KV = 400;

export function xrayReference() {
  return { ...REFERENCE };
}

export function maximumKilovolts() {
  return MAX_KV;
}

export function thicknessRange() {
  return { minimumIn: KV_CURVE[0][0], maximumIn: KV_CURVE[KV_CURVE.length - 1][0] };
}

function readCurve(curve, thicknessIn, how) {
  requireAbove(thicknessIn, 0, 'thickness');
  const range = thicknessRange();
  if (thicknessIn < range.minimumIn || thicknessIn > range.maximumIn) {
    throw new OutOfRange(`${thicknessIn} in is outside the range this head covers`, {
      thicknessIn,
      ...range,
    });
  }
  for (let i = 0; i < curve.length - 1; i += 1) {
    const [lowT, lowV] = curve[i];
    const [highT, highV] = curve[i + 1];
    if (thicknessIn >= lowT && thicknessIn <= highT) {
      return how(thicknessIn, lowT, lowV, highT, highV);
    }
  }
  throw new OutOfRange('that thickness falls between no two points', { thicknessIn });
}

/** The lowest kilovoltage that will penetrate, rounded up to a settable step. */
export function kilovoltsFor(thicknessIn, { step = 5 } = {}) {
  const exact = readCurve(KV_CURVE, thicknessIn, interpolate);
  return Math.min(MAX_KV, Math.ceil(exact / step) * step);
}

/** The thickest section a given kilovoltage will reach. */
export function thicknessAtKilovolts(kilovolts) {
  requireAbove(kilovolts, 0, 'kilovoltage');
  const lowest = KV_CURVE[0][1];
  const highest = KV_CURVE[KV_CURVE.length - 1][1];
  if (kilovolts < lowest || kilovolts > highest) {
    throw new OutOfRange(`${kilovolts} kV is outside the range of this head`, {
      kilovolts,
      lowest,
      highest,
    });
  }
  for (let i = 0; i < KV_CURVE.length - 1; i += 1) {
    const [lowT, lowV] = KV_CURVE[i];
    const [highT, highV] = KV_CURVE[i + 1];
    if (kilovolts >= lowV && kilovolts <= highV) {
      return round(interpolate(kilovolts, lowV, lowT, highV, highT), 4);
    }
  }
  throw new OutOfRange('that kilovoltage falls between no two points', { kilovolts });
}

/** Milliampere minutes at the reference distance. */
export function milliampereMinutesFor(thicknessIn) {
  return round(readCurve(MAMIN_CURVE, thicknessIn, logInterpolate), 3);
}

/**
 * A whole tube technique. The kilovoltage is the shop minimum unless one is
 * given, and giving one is recorded so that a flatter than usual radiograph
 * can be traced back to the decision that produced it.
 */
export function planXray({ thicknessIn, distanceIn, milliamps, kilovolts = null, step = 5 }) {
  requireAbove(distanceIn, 0, 'distance');
  requireAbove(milliamps, 0, 'tube current');
  const minimum = kilovoltsFor(thicknessIn, { step });
  const chosen = kilovolts === null ? minimum : kilovolts;
  requireAtLeast(chosen, minimum, 'kilovoltage');

  const base = milliampereMinutesFor(thicknessIn);
  const forDistance = scaleExposure({
    curieMinutesAt: base,
    chartDistanceIn: REFERENCE.distanceIn,
    actualDistanceIn: distanceIn,
  });
  const minutes = minutesForMilliampereMinutes(forDistance, milliamps);

  return {
    thicknessIn,
    kilovolts: chosen,
    minimumKilovolts: minimum,
    aboveMinimum: chosen > minimum,
    distanceIn,
    milliamps,
    milliampereMinutes: round(forDistance, 3),
    minutes: round(minutes, 3),
    seconds: Math.round(minutes * 60),
  };
}

/**
 * Whether a tube can do a job at all. A head rated below what the wall needs
 * is not a slow option, it is the wrong tool, and saying so before the crew
 * mobilises is the point.
 */
export function headCanReach({ ratedKilovolts, thicknessIn }) {
  requireAbove(ratedKilovolts, 0, 'rated kilovoltage');
  const needed = kilovoltsFor(thicknessIn);
  return {
    neededKilovolts: needed,
    ratedKilovolts,
    canReach: ratedKilovolts >= needed,
    shortBy: ratedKilovolts >= needed ? 0 : needed - ratedKilovolts,
  };
}
