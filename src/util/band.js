import { BadInput, OutOfRange } from '../errors.js';
import { isFiniteNumber } from './num.js';

/**
 * Banded lookup tables. Almost every table in a welding code reads "over 3/4
 * in. through 1-1/2 in.", which is an open lower bound and a closed upper
 * one. Writing that convention once, here, is the difference between a table
 * that agrees with the printed code and one that disagrees with it by exactly
 * one boundary value in a way nobody notices for a year.
 */

/**
 * Build a table from rows given lowest first. Each row is
 * { over, upTo, value }; over defaults to the previous row's upTo, and the
 * first row's over defaults to zero. upTo may be Infinity on the last row.
 */
export function bandTable(rows, options = {}) {
  if (!Array.isArray(rows) || rows.length === 0) {
    throw new BadInput('a band table needs at least one row', { got: rows });
  }
  const name = options.name ?? 'band table';
  const built = [];
  let previousUpTo = null;

  rows.forEach((row, index) => {
    if (typeof row !== 'object' || row === null) {
      throw new BadInput(`${name} row ${index} is not an object`, { row });
    }
    const over = row.over ?? previousUpTo ?? 0;
    const upTo = row.upTo;
    if (!isFiniteNumber(over)) {
      throw new BadInput(`${name} row ${index} has a bad lower bound`, { over });
    }
    if (!(isFiniteNumber(upTo) || upTo === Infinity)) {
      throw new BadInput(`${name} row ${index} has a bad upper bound`, { upTo });
    }
    if (upTo <= over) {
      throw new BadInput(`${name} row ${index} does not go upwards`, { over, upTo });
    }
    if (previousUpTo !== null && over !== previousUpTo) {
      throw new BadInput(`${name} row ${index} leaves a gap or overlaps the row below`, {
        previousUpTo,
        over,
      });
    }
    if (upTo === Infinity && index !== rows.length - 1) {
      throw new BadInput(`${name} row ${index} is open ended but is not the last row`, {
        index,
      });
    }
    built.push(Object.freeze({ over, upTo, value: row.value }));
    previousUpTo = upTo;
  });

  return Object.freeze({
    name,
    rows: Object.freeze(built),
    floor: built[0].over,
    ceiling: built[built.length - 1].upTo,
  });
}

/**
 * The row covering x, under the code convention: over the lower bound, up to
 * and including the upper. The very bottom of the table is inclusive, since a
 * table that starts at zero has to accept zero.
 */
export function bandRow(table, x) {
  assertTable(table);
  if (!isFiniteNumber(x)) {
    throw new BadInput(`${table.name} needs a finite lookup value`, { got: x });
  }
  if (x < table.floor) {
    throw new OutOfRange(`${x} is below the bottom of ${table.name}`, {
      value: x,
      floor: table.floor,
    });
  }
  if (x > table.ceiling) {
    throw new OutOfRange(`${x} is above the top of ${table.name}`, {
      value: x,
      ceiling: table.ceiling,
    });
  }
  const found = table.rows.find((row, index) => {
    const lowerOk = index === 0 ? x >= row.over : x > row.over;
    return lowerOk && x <= row.upTo;
  });
  if (!found) {
    throw new OutOfRange(`${x} falls in no band of ${table.name}`, { value: x });
  }
  return found;
}

/** The value alone, which is what nearly every caller wants. */
export function bandValue(table, x) {
  return bandRow(table, x).value;
}

/**
 * The value, or a fallback when x sits off the end of the table. Some code
 * tables genuinely stop and the caller has to decide what that means, so this
 * is separate from bandValue rather than being an option on it.
 */
export function bandValueOr(table, x, fallback) {
  try {
    return bandValue(table, x);
  } catch (err) {
    if (err instanceof OutOfRange) return fallback;
    throw err;
  }
}

export function covers(table, x) {
  assertTable(table);
  return isFiniteNumber(x) && x >= table.floor && x <= table.ceiling;
}

/** Render the bands for a technique sheet appendix. */
export function describeBands(table, format = (v) => String(v)) {
  assertTable(table);
  return table.rows.map((row) => {
    const upper = row.upTo === Infinity ? 'and above' : `through ${format(row.upTo)}`;
    return `over ${format(row.over)} ${upper}: ${row.value}`;
  });
}

function assertTable(table) {
  if (typeof table !== 'object' || table === null || !Array.isArray(table.rows)) {
    throw new BadInput('not a band table', { got: table });
  }
}
