import { BadInput } from '../errors.js';
import { isFiniteNumber, round } from '../util/num.js';
import { isotope } from '../isotope/catalog.js';
import { activityAfter } from '../isotope/decay.js';
import { chartExposure, chartReference } from './chart.js';
import { convertExposure } from './filmclass.js';
import { screenFactor } from './screens.js';
import { aimAtDensity } from './aim.js';
import { scaleExposure, minutesForCurieMinutes } from './law.js';

/**
 * The whole exposure calculation, in the order the shop does it on paper.
 *
 * The chart is the only measurement in the chain. Everything after it is a
 * correction for the ways the real setup differs from the conditions the
 * chart was shot at, and each correction is a multiplication, so the order
 * they are applied in does not change the answer. It does change how easy the
 * sheet is to check, which is why they come out as separate named factors
 * rather than one number.
 */

export function exposureFactors({
  thicknessIn,
  distanceIn,
  filmId,
  screens,
  targetDensity,
  isotopeId,
}) {
  const reference = chartReference();
  const base = chartExposure(isotopeId, thicknessIn);

  const forDistance = scaleExposure({
    curieMinutesAt: base,
    chartDistanceIn: reference.distanceIn,
    actualDistanceIn: distanceIn,
  });
  const forFilm = convertExposure(forDistance, reference.film, filmId);
  const screenRatio =
    screenFactor(screens, isotopeId) / screenFactor(reference.screens, isotopeId);
  const forScreens = forFilm * screenRatio;
  const forDensity = aimAtDensity({
    exposure: forScreens,
    filmId,
    chartDensity: reference.density,
    wantedDensity: targetDensity,
  });

  return {
    chartCurieMinutes: round(base, 3),
    distanceFactor: round((distanceIn / reference.distanceIn) ** 2, 4),
    filmFactor: round(forFilm / forDistance, 4),
    screenFactor: round(screenRatio, 4),
    densityFactor: round(forDensity / forScreens, 4),
    curieMinutes: round(forDensity, 3),
  };
}

/**
 * The exposure in curie minutes and the time it takes on the source as it
 * stands today. Everything the crew needs to set the timer.
 */
export function planExposure(setup) {
  const {
    isotopeId,
    thicknessIn,
    distanceIn,
    filmId = 'D7',
    screens = 'LEAD_STANDARD',
    targetDensity = 2,
    assayCi,
    daysSinceAssay = 0,
  } = setup;

  requireAbove(assayCi, 0, 'assay activity');
  requireNumber(daysSinceAssay, 'days since assay');

  const source = isotope(isotopeId);
  const activityCi = activityAfter(assayCi, source.halfLifeDays, daysSinceAssay);
  const factors = exposureFactors({
    thicknessIn,
    distanceIn,
    filmId,
    screens,
    targetDensity,
    isotopeId: source.id,
  });
  const minutes = minutesForCurieMinutes(factors.curieMinutes, activityCi);

  return {
    isotope: source.id,
    activityCi: round(activityCi, 2),
    film: filmId,
    screens,
    targetDensity,
    thicknessIn,
    distanceIn,
    ...factors,
    minutes: round(minutes, 2),
    seconds: Math.round(minutes * 60),
  };
}

/**
 * The time a crew can actually dial, rounded up to the nearest whole second
 * with the shop's ten percent working margin on it. Rounding down is how a
 * light film happens.
 */
export function timerSetting(minutes, marginFraction = 0.1) {
  requireAbove(minutes, 0, 'exposure time');
  requireAtLeast(marginFraction, 0, 'margin');
  return Math.ceil(minutes * (1 + marginFraction) * 60);
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
