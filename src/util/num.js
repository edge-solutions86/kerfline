import { BadInput } from '../errors.js';

/**
 * Arithmetic the rest of the package leans on. Radiography numbers are read
 * off charts and calipers, so nearly everything here is about controlling
 * where a value gets rounded rather than about speed.
 */

const EPS = 1e-9;

export function isFiniteNumber(value) {
  return typeof value === 'number' && Number.isFinite(value);
}

function need(value, what) {
  if (!isFiniteNumber(value)) {
    throw new BadInput(`${what} must be a finite number`, { got: value });
  }
  return value;
}

/**
 * Round half away from zero at a decimal place. The obvious
 * Math.round(x * 10 ** dp) / 10 ** dp gets 1.005 wrong at two places because
 * the literal is already below 1.005, so the shift goes through a string.
 */
export function round(value, dp = 0) {
  need(value, 'value');
  if (!Number.isInteger(dp) || dp < 0 || dp > 12) {
    throw new BadInput('decimal places must be an integer between 0 and 12', { dp });
  }
  if (value === 0) return 0;
  const shifted = Number(`${value}e${dp}`);
  const rounded = Math.sign(shifted) * Math.round(Math.abs(shifted));
  const back = Number(`${rounded}e${-dp}`);
  return back === 0 ? 0 : back;
}

/** Round to the nearest multiple of step, which is how a caliper reads. */
export function roundTo(value, step) {
  need(value, 'value');
  need(step, 'step');
  if (step <= 0) throw new BadInput('step must be positive', { step });
  const n = Math.sign(value / step) * Math.round(Math.abs(value / step));
  return round(n * step, 12);
}

export function ceilTo(value, step) {
  need(value, 'value');
  need(step, 'step');
  if (step <= 0) throw new BadInput('step must be positive', { step });
  return round(Math.ceil(value / step - EPS) * step, 12);
}

export function floorTo(value, step) {
  need(value, 'value');
  need(step, 'step');
  if (step <= 0) throw new BadInput('step must be positive', { step });
  return round(Math.floor(value / step + EPS) * step, 12);
}

export function clamp(value, lo, hi) {
  need(value, 'value');
  need(lo, 'lower bound');
  need(hi, 'upper bound');
  if (lo > hi) throw new BadInput('lower bound is above upper bound', { lo, hi });
  return Math.min(hi, Math.max(lo, value));
}

export function approxEqual(a, b, tolerance = 1e-9) {
  need(a, 'a');
  need(b, 'b');
  need(tolerance, 'tolerance');
  if (tolerance < 0) throw new BadInput('tolerance must not be negative', { tolerance });
  return Math.abs(a - b) <= tolerance;
}

export function lerp(a, b, t) {
  need(a, 'a');
  need(b, 'b');
  need(t, 't');
  return a + (b - a) * t;
}

/** Straight line through two chart points, evaluated at x. */
export function interpolate(x, x0, y0, x1, y1) {
  need(x, 'x');
  need(x0, 'x0');
  need(y0, 'y0');
  need(x1, 'x1');
  need(y1, 'y1');
  if (approxEqual(x0, x1)) {
    throw new BadInput('cannot interpolate between two points at the same x', { x0, x1 });
  }
  return y0 + ((x - x0) / (x1 - x0)) * (y1 - y0);
}

/**
 * Exposure charts plot thickness against the log of exposure, so a straight
 * line on the printed chart is this and not the linear one above.
 */
export function logInterpolate(x, x0, y0, x1, y1) {
  if (y0 <= 0 || y1 <= 0) {
    throw new BadInput('log interpolation needs positive endpoints', { y0, y1 });
  }
  return Math.exp(interpolate(x, x0, Math.log(y0), x1, Math.log(y1)));
}

export function sum(values) {
  return asNumbers(values, 'values').reduce((acc, v) => acc + v, 0);
}

export function mean(values) {
  const list = asNumbers(values, 'values');
  if (list.length === 0) throw new BadInput('mean of an empty list is undefined');
  return sum(list) / list.length;
}

export function median(values) {
  const list = asNumbers(values, 'values')
    .slice()
    .sort((a, b) => a - b);
  if (list.length === 0) throw new BadInput('median of an empty list is undefined');
  const mid = Math.floor(list.length / 2);
  return list.length % 2 === 1 ? list[mid] : (list[mid - 1] + list[mid]) / 2;
}

export function maxOf(values) {
  const list = asNumbers(values, 'values');
  if (list.length === 0) throw new BadInput('max of an empty list is undefined');
  return list.reduce((a, b) => (b > a ? b : a));
}

export function minOf(values) {
  const list = asNumbers(values, 'values');
  if (list.length === 0) throw new BadInput('min of an empty list is undefined');
  return list.reduce((a, b) => (b < a ? b : a));
}

function asNumbers(values, what) {
  if (!Array.isArray(values)) throw new BadInput(`${what} must be an array`, { got: values });
  values.forEach((v, i) => need(v, `${what}[${i}]`));
  return values;
}

/**
 * A fixed decimal string with trailing zeros kept, because a wall thickness
 * on a report reads as 0.280 and not 0.28.
 */
export function fixed(value, dp) {
  need(value, 'value');
  if (!Number.isInteger(dp) || dp < 0 || dp > 12) {
    throw new BadInput('decimal places must be an integer between 0 and 12', { dp });
  }
  return round(value, dp).toFixed(dp);
}

/** Percent change from a reference, signed, as used by the density rule. */
export function percentChange(from, to) {
  need(from, 'from');
  need(to, 'to');
  if (from === 0) throw new BadInput('percent change from zero is undefined');
  return ((to - from) / from) * 100;
}
