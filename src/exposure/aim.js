import { BadInput, NotFound } from '../errors.js';
import { isFiniteNumber, round } from '../util/num.js';
import { film, filmIds } from './filmclass.js';

/**
 * Aiming an exposure at a density.
 *
 * Film responds to the logarithm of exposure, so shifting the density a shot
 * lands at is a multiplication and not an addition. Over the straight part of
 * the characteristic curve
 *
 *     exposure ratio = 10 ^ ((wanted - got) / gradient)
 *
 * where the gradient is the slope of that curve. The finer the grain the
 * steeper the slope, so the same half a density point costs a fine film far
 * less extra exposure than a fast one. That is the opposite of what people
 * expect and it is why the gradient is per film rather than a constant.
 *
 * The gradients below are averaged over density 1.5 to 3.5, which is the only
 * span this shop reads film in.
 */

const GRADIENTS = { D2: 5, D3: 4.6, D4: 4.2, D5: 3.8, D7: 3.2, D8: 2.6 };

const READABLE = { minimum: 1.5, maximum: 4 };

export function readableDensityRange() {
  return { ...READABLE };
}

export function filmGradient(filmId) {
  const record = film(filmId);
  const gradient = GRADIENTS[record.id];
  if (gradient === undefined) {
    throw new NotFound(`no measured gradient for film ${record.id}`, {
      film: record.id,
      known: filmIds(),
    });
  }
  return gradient;
}

/** How much more exposure it takes to move from one density to another. */
export function exposureRatioForDensity(filmId, fromDensity, toDensity) {
  requireDensity(fromDensity, 'starting density');
  requireDensity(toDensity, 'wanted density');
  return 10 ** ((toDensity - fromDensity) / filmGradient(filmId));
}

/** The density a shot lands at when the exposure is scaled by a ratio. */
export function densityForExposureRatio(filmId, fromDensity, ratio) {
  requireDensity(fromDensity, 'starting density');
  if (!isFiniteNumber(ratio) || ratio <= 0) {
    throw new BadInput('exposure ratio must be greater than zero', { got: ratio });
  }
  return round(fromDensity + filmGradient(filmId) * Math.log10(ratio), 3);
}

/**
 * Scale an exposure worked out for one density onto another. This is the
 * function a technique sheet calls when the job asks for density 2.5 and the
 * chart was shot at 2.0.
 */
export function aimAtDensity({ exposure, filmId, chartDensity, wantedDensity }) {
  if (!isFiniteNumber(exposure) || exposure < 0) {
    throw new BadInput('exposure must not be negative', { got: exposure });
  }
  return exposure * exposureRatioForDensity(filmId, chartDensity, wantedDensity);
}

/**
 * Whether a density reading is worth reading at all. Below the floor the film
 * has too little contrast to show anything and above the ceiling the viewer
 * cannot get light through it.
 */
export function densityIsReadable(value) {
  return isFiniteNumber(value) && value >= READABLE.minimum && value <= READABLE.maximum;
}

/**
 * The correction a reshoot needs after a film came out at the wrong density,
 * expressed as the factor to put on the time that was used.
 */
export function reshootFactor({ filmId, gotDensity, wantedDensity }) {
  requireDensity(gotDensity, 'measured density');
  requireDensity(wantedDensity, 'wanted density');
  return round(exposureRatioForDensity(filmId, gotDensity, wantedDensity), 4);
}

function requireDensity(value, what) {
  if (!isFiniteNumber(value)) {
    throw new BadInput(`${what} must be a finite number`, { got: value });
  }
  if (value <= 0) {
    throw new BadInput(`${what} must be greater than zero`, { got: value });
  }
  return value;
}
