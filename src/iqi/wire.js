import { BadInput, NotFound } from '../errors.js';
import { isFiniteNumber } from '../util/num.js';

/**
 * Wire image quality indicators.
 *
 * A wire indicator is six parallel wires of graded diameter sealed in a
 * plastic envelope. The wires are numbered straight through from the finest
 * to the coarsest across the whole system, and the four stocked sets overlap
 * by one wire at each join, so wire 6 is the last wire of set A and the first
 * of set B. That overlap is deliberate and it is also the thing that gets
 * mis-ordered: asking for "the first wire of set B" and "wire 6" are the same
 * request and a stores clerk reading the first phrasing sends set B.
 */

const DIAMETERS = {
  1: 0.0032,
  2: 0.004,
  3: 0.005,
  4: 0.0063,
  5: 0.008,
  6: 0.01,
  7: 0.0126,
  8: 0.016,
  9: 0.02,
  10: 0.025,
  11: 0.032,
  12: 0.04,
  13: 0.05,
  14: 0.063,
  15: 0.08,
  16: 0.1,
  17: 0.126,
  18: 0.16,
  19: 0.2,
  20: 0.25,
  21: 0.32,
};

const SETS = {
  A: [1, 2, 3, 4, 5, 6],
  B: [6, 7, 8, 9, 10, 11],
  C: [11, 12, 13, 14, 15, 16],
  D: [16, 17, 18, 19, 20, 21],
};

export function wireNumbers() {
  return Object.keys(DIAMETERS).map(Number);
}

export function wireSetLetters() {
  return Object.keys(SETS);
}

export function wireDiameterIn(number) {
  requireWire(number);
  return DIAMETERS[number];
}

export function wireSet(letter) {
  const found = SETS[String(letter).toUpperCase()];
  if (!found) {
    throw new NotFound(`no wire set ${letter}`, { asked: letter, known: wireSetLetters() });
  }
  return [...found];
}

/**
 * The sets a wire appears in. The join wires belong to two sets and returning
 * a list rather than a letter is the only honest way to say so.
 */
export function setsForWire(number) {
  requireWire(number);
  return wireSetLetters().filter((letter) => SETS[letter].includes(number));
}

/** The set a crew should draw for a wire, taking the finer set at a join. */
export function preferredSetForWire(number) {
  return setsForWire(number)[0];
}

export function hasWire(number) {
  return isFiniteNumber(number) && Object.prototype.hasOwnProperty.call(DIAMETERS, number);
}

/** The finest wire whose diameter reaches a size, which is how a call is met. */
export function wireForDiameterIn(diameterIn) {
  if (!isFiniteNumber(diameterIn) || diameterIn <= 0) {
    throw new BadInput('wire diameter must be a positive number', { got: diameterIn });
  }
  const found = wireNumbers().find((n) => DIAMETERS[n] >= diameterIn - 1e-12);
  if (found === undefined) {
    throw new NotFound(`no wire reaches ${diameterIn} in`, { diameterIn });
  }
  return found;
}

/** The set record for a sheet: its letter, its wires and their diameters. */
export function wireSetRecord(letter) {
  const wires = wireSet(letter);
  return {
    letter: String(letter).toUpperCase(),
    wires,
    diametersIn: wires.map((n) => DIAMETERS[n]),
    finestIn: DIAMETERS[wires[0]],
    coarsestIn: DIAMETERS[wires[wires.length - 1]],
  };
}

function requireWire(number) {
  if (!isFiniteNumber(number)) {
    throw new BadInput('a wire must be named by its number', { got: number });
  }
  if (!Object.prototype.hasOwnProperty.call(DIAMETERS, number)) {
    throw new NotFound(`no wire numbered ${number}`, { number, known: wireNumbers() });
  }
  return number;
}
