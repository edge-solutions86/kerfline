import { BadInput } from '../errors.js';
import { isFiniteNumber, round } from '../util/num.js';
import { requireOneOf } from '../util/guard.js';
import { isotope } from '../isotope/catalog.js';

/**
 * Film density limits.
 *
 * The floor depends on what made the radiograph. A gamma source lays down
 * less contrast than an X-ray head at the same density, so the gamma floor is
 * higher to make up for it. The ceiling is the same for both and it is set by
 * the viewer rather than by the physics: past four there is not enough light
 * coming through the film to read it on the illuminators this shop owns.
 *
 * Composite viewing is the exception in both directions. Two films stacked
 * add their densities, so each single film of the pair is allowed down to
 * 1.3, and it is the stacked pair that has to clear the usual floor.
 */

const SINGLE = { xray: 1.8, gamma: 2 };
const COMPOSITE_EACH = 1.3;
const CEILING = 4;

const KINDS = ['xray', 'gamma'];

export function sourceKinds() {
  return [...KINDS];
}

/** The kind a source counts as, so a caller can pass an isotope id straight in. */
export function sourceKindOf(source) {
  if (KINDS.includes(source)) return source;
  return isotope(source).kind === 'gamma' ? 'gamma' : 'xray';
}

/**
 * The limits for one film. Composite viewing lowers only the floor, and only
 * for the individual films of the set.
 */
export function densityLimits(source, { composite = false } = {}) {
  const kind = sourceKindOf(source);
  requireOneOf(kind, KINDS, 'source kind');
  return {
    kind,
    minimum: composite ? COMPOSITE_EACH : SINGLE[kind],
    maximum: CEILING,
    composite,
  };
}

export function ceilingDensity() {
  return CEILING;
}

export function withinLimits(value, source, options) {
  if (!isFiniteNumber(value)) return false;
  const limits = densityLimits(source, options);
  return value >= limits.minimum && value <= limits.maximum;
}

/**
 * Judge one reading, saying which way it failed. A film that is too light and
 * one that is too dark need opposite corrections, so a bare false would make
 * the caller work it out again.
 */
export function judgeDensity(value, source, options) {
  if (!isFiniteNumber(value)) {
    throw new BadInput('density must be a finite number', { got: value });
  }
  if (value <= 0) {
    throw new BadInput('density must be greater than zero', { got: value });
  }
  const limits = densityLimits(source, options);
  let verdict = 'accept';
  if (value < limits.minimum) verdict = 'light';
  else if (value > limits.maximum) verdict = 'dark';
  return {
    density: value,
    verdict,
    pass: verdict === 'accept',
    minimum: limits.minimum,
    maximum: limits.maximum,
    kind: limits.kind,
  };
}

/**
 * Judge a composite set. Each film has to clear the composite floor on its
 * own, and the sum of the set has to clear the ordinary single film floor,
 * which is the rule people forget when they stack two thin films.
 */
export function judgeComposite(values, source) {
  if (!Array.isArray(values) || values.length < 2) {
    throw new BadInput('a composite set is two or more films', { got: values });
  }
  values.forEach((value, index) => {
    if (!isFiniteNumber(value) || value <= 0) {
      throw new BadInput(`film ${index + 1} density must be greater than zero`, { got: value });
    }
  });
  const each = values.map((value) => judgeDensity(value, source, { composite: true }));
  const stacked = round(
    values.reduce((sum, value) => sum + value, 0),
    3
  );
  const single = densityLimits(source);
  return {
    films: each,
    stackedDensity: stacked,
    everyFilmPasses: each.every((film) => film.pass),
    stackedPasses: stacked >= single.minimum && stacked <= single.maximum,
    pass:
      each.every((film) => film.pass) && stacked >= single.minimum && stacked <= single.maximum,
  };
}
