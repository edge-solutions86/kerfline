import { BadInput, OutOfRange } from '../errors.js';
import { round, interpolate } from '../util/num.js';
import { requireArray, requireAbove, requireAtLeast } from '../util/guard.js';

/**
 * The darkroom.
 *
 * Development is a time and a temperature together and neither means anything
 * on its own. Five minutes is right at sixty eight degrees and badly wrong at
 * seventy eight, and the film that comes out of the wrong one is not merely
 * light or dark: it has the wrong contrast, which is worse, because a light
 * film is obviously light and a low contrast film just quietly hides things.
 *
 * The temperature band this shop will process in is sixty to eighty. Outside
 * it the developer either will not work or works too fast to control by hand,
 * and the answer is to fix the darkroom rather than to adjust the clock.
 */

const CURVE = [
  [60, 8],
  [65, 6],
  [68, 5],
  [70, 4.5],
  [72, 4],
  [75, 3.5],
  [78, 3],
  [80, 2.75],
];

const REFERENCE_F = 68;
const TOLERANCE_MINUTES = 0.25;

export function referenceTemperatureF() {
  return REFERENCE_F;
}

export function temperatureBandF() {
  return { minimumF: CURVE[0][0], maximumF: CURVE[CURVE.length - 1][0] };
}

export function timeTemperatureCurve() {
  return CURVE.map(([temperatureF, minutes]) => ({ temperatureF, minutes }));
}

/** The development time for a bath at a temperature. */
export function developmentMinutes(temperatureF) {
  requireAbove(temperatureF, 0, 'developer temperature');
  const band = temperatureBandF();
  if (temperatureF < band.minimumF || temperatureF > band.maximumF) {
    throw new OutOfRange(`${temperatureF} F is outside the band this shop develops in`, {
      temperatureF,
      ...band,
    });
  }
  for (let i = 0; i < CURVE.length - 1; i += 1) {
    const [lowT, lowM] = CURVE[i];
    const [highT, highM] = CURVE[i + 1];
    if (temperatureF >= lowT && temperatureF <= highT) {
      return round(interpolate(temperatureF, lowT, lowM, highT, highM), 2);
    }
  }
  throw new OutOfRange('that temperature falls between no two points', { temperatureF });
}

/** The temperature at which a wanted time is the right time. */
export function temperatureForMinutes(minutes) {
  requireAbove(minutes, 0, 'development time');
  const fastest = CURVE[CURVE.length - 1][1];
  const slowest = CURVE[0][1];
  if (minutes < fastest || minutes > slowest) {
    throw new OutOfRange(`${minutes} minutes is off the time temperature curve`, {
      minutes,
      fastest,
      slowest,
    });
  }
  for (let i = 0; i < CURVE.length - 1; i += 1) {
    const [lowT, lowM] = CURVE[i];
    const [highT, highM] = CURVE[i + 1];
    if (minutes <= lowM && minutes >= highM) {
      return round(interpolate(minutes, lowM, lowT, highM, highT), 2);
    }
  }
  throw new OutOfRange('that time falls between no two points', { minutes });
}

/**
 * Judge a processing cycle. The verdict names which way it went, because an
 * overdeveloped film and an underdeveloped one look nothing alike on the
 * light box and get confused with exposure faults in opposite directions.
 */
export function judgeCycle({ temperatureF, minutes, tolerance = TOLERANCE_MINUTES }) {
  requireAtLeast(minutes, 0, 'development time');
  requireAtLeast(tolerance, 0, 'tolerance');
  const wanted = developmentMinutes(temperatureF);
  const difference = round(minutes - wanted, 2);
  let verdict = 'correct';
  if (difference > tolerance) verdict = 'overdeveloped';
  else if (difference < -tolerance) verdict = 'underdeveloped';
  return {
    temperatureF,
    minutes,
    wantedMinutes: wanted,
    differenceMinutes: difference,
    verdict,
    pass: verdict === 'correct',
    effect:
      verdict === 'overdeveloped'
        ? 'density and fog both up, contrast down'
        : verdict === 'underdeveloped'
          ? 'density down and contrast down, which hides indications'
          : null,
  };
}

/**
 * Replenisher to add for a run of film. Quoted per hundred square inches
 * because that is how the tank is dosed, and it does not scale with the
 * number of films, only with their area.
 */
export function replenisherMl(areaSquareInches, { mlPerHundred = 75 } = {}) {
  requireAtLeast(areaSquareInches, 0, 'film area');
  requireAbove(mlPerHundred, 0, 'replenishment rate');
  return round((areaSquareInches / 100) * mlPerHundred, 1);
}

/** The area a batch of films comes to. */
export function batchAreaSquareInches(films) {
  requireArray(films, 'films');
  return round(
    films.reduce((total, film) => {
      if (!(film.widthIn > 0) || !(film.lengthIn > 0)) {
        throw new BadInput('every film needs a width and a length', { got: film });
      }
      return total + film.widthIn * film.lengthIn;
    }, 0),
    2
  );
}

/** The whole darkroom cycle, for the log sheet. */
export function cycleRecord({
  temperatureF,
  minutes,
  films,
  fixMinutes = 10,
  washMinutes = 20,
}) {
  const development = judgeCycle({ temperatureF, minutes });
  const area = batchAreaSquareInches(films);
  return {
    ...development,
    films: films.length,
    areaSquareInches: area,
    replenisherMl: replenisherMl(area),
    fixMinutes,
    washMinutes,
    totalMinutes: round(minutes + fixMinutes + washMinutes, 2),
  };
}
