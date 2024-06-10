import { BadInput, Unsupported } from '../errors.js';
import { isFiniteNumber, round } from './num.js';

/**
 * Unit handling. Crews here work in inches and curies, the procedures are
 * written in millimetres and becquerels, and the survey meter reads
 * milliroentgen per hour. Everything converts through a base unit per
 * dimension so a new unit is one table row and not a new function.
 */

const DIMENSIONS = {
  length: {
    base: 'in',
    units: {
      in: 1,
      mil: 0.001,
      ft: 12,
      mm: 1 / 25.4,
      cm: 10 / 25.4,
      m: 1000 / 25.4,
    },
  },
  activity: {
    base: 'Ci',
    units: {
      Ci: 1,
      mCi: 0.001,
      Bq: 1 / 3.7e10,
      kBq: 1 / 3.7e7,
      MBq: 1 / 3.7e4,
      GBq: 1 / 37,
      TBq: 1000 / 37,
    },
  },
  doseRate: {
    base: 'mR/h',
    units: {
      'R/h': 1000,
      'mR/h': 1,
      'uSv/h': 0.1,
      'mSv/h': 100,
      'Sv/h': 100000,
    },
  },
  time: {
    base: 'min',
    units: {
      s: 1 / 60,
      min: 1,
      h: 60,
      d: 1440,
    },
  },
};

/** The dimension a unit belongs to, or null when we do not know the unit. */
export function dimensionOf(unit) {
  for (const [name, spec] of Object.entries(DIMENSIONS)) {
    if (Object.prototype.hasOwnProperty.call(spec.units, unit)) return name;
  }
  return null;
}

export function unitsFor(dimension) {
  const spec = DIMENSIONS[dimension];
  if (!spec) throw new Unsupported(`no such dimension ${dimension}`, { dimension });
  return Object.keys(spec.units).sort();
}

export function dimensions() {
  return Object.keys(DIMENSIONS).sort();
}

/**
 * Convert between two units of the same dimension. Crossing dimensions is a
 * mistake worth stopping rather than coercing, because a curie is not a
 * distance no matter how convenient that would be at the call site.
 */
export function convert(value, from, to) {
  if (!isFiniteNumber(value)) {
    throw new BadInput('value must be a finite number', { got: value });
  }
  const fromDim = dimensionOf(from);
  const toDim = dimensionOf(to);
  if (fromDim === null) throw new Unsupported(`unknown unit ${from}`, { unit: from });
  if (toDim === null) throw new Unsupported(`unknown unit ${to}`, { unit: to });
  if (fromDim !== toDim) {
    throw new BadInput(`cannot convert ${from} to ${to}`, { from, to, fromDim, toDim });
  }
  if (from === to) return value;
  const units = DIMENSIONS[fromDim].units;
  return (value * units[from]) / units[to];
}

export function inches(value, from) {
  return convert(value, from, 'in');
}

export function curies(value, from) {
  return convert(value, from, 'Ci');
}

export function minutes(value, from) {
  return convert(value, from, 'min');
}

/**
 * Parse a written measurement such as "0.280 in" or "12.7mm". Field forms are
 * filled in by hand and the space is not reliable.
 */
export function parseMeasure(text) {
  if (typeof text !== 'string') {
    throw new BadInput('measurement must be a string', { got: text });
  }
  const match = /^\s*(-?\d+(?:\.\d+)?)\s*([A-Za-z/]+)\s*$/.exec(text);
  if (!match) throw new BadInput(`cannot read measurement ${text}`, { text });
  const value = Number(match[1]);
  const unit = normaliseUnit(match[2]);
  if (dimensionOf(unit) === null) throw new Unsupported(`unknown unit ${match[2]}`, { text });
  return { value, unit };
}

/**
 * Forms come back with "IN", "Mm" and "mr/h" in them. Case is folded against
 * the known unit list rather than blindly lowercased, because mCi and MBq
 * differ only by case and both are real.
 */
export function normaliseUnit(text) {
  const known = Object.values(DIMENSIONS).flatMap((spec) => Object.keys(spec.units));
  const exact = known.find((u) => u === text);
  if (exact) return exact;
  const folded = known.filter((u) => u.toLowerCase() === String(text).toLowerCase());
  if (folded.length === 1) return folded[0];
  return text;
}

/** Format for a report line: the rounded value and the unit, space separated. */
export function formatMeasure(value, unit, dp = 3) {
  if (dimensionOf(unit) === null) throw new Unsupported(`unknown unit ${unit}`, { unit });
  return `${round(value, dp)} ${unit}`;
}
