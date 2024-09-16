import { BadInput } from '../errors.js';
import { isFiniteNumber } from './num.js';

/**
 * Argument guards.
 *
 * Twelve modules had grown their own copy of these four functions, which was
 * fine until two of the copies drifted apart on the wording of the message
 * and a test somewhere started asserting on the wrong one. They live here
 * now. The message wording is part of the contract because callers match on
 * it, so the two ways of saying "above zero" are both kept rather than
 * merged: requireAbove names the bound it was given, requirePositive says
 * zero in words.
 */

export function requireNumber(value, what) {
  if (!isFiniteNumber(value)) {
    throw new BadInput(`${what} must be a finite number`, { got: value });
  }
  return value;
}

export function requireAbove(value, floor, what) {
  requireNumber(value, what);
  if (value <= floor) {
    throw new BadInput(`${what} must be greater than ${floor}`, { got: value });
  }
  return value;
}

export function requireAtLeast(value, floor, what) {
  requireNumber(value, what);
  if (value < floor) {
    throw new BadInput(`${what} must not be below ${floor}`, { got: value });
  }
  return value;
}

export function requirePositive(value, what) {
  requireNumber(value, what);
  if (value <= 0) {
    throw new BadInput(`${what} must be greater than zero`, { got: value });
  }
  return value;
}

export function requireWhole(value, what) {
  if (!Number.isInteger(value)) {
    throw new BadInput(`${what} must be a whole number`, { got: value });
  }
  return value;
}

export function requireOneOf(value, options, what) {
  if (!options.includes(value)) {
    throw new BadInput(`${what} must be one of ${options.join(' or ')}`, {
      got: value,
      known: options,
    });
  }
  return value;
}

export function requireArray(value, what) {
  if (!Array.isArray(value)) {
    throw new BadInput(`${what} must be an array`, { got: value });
  }
  return value;
}
