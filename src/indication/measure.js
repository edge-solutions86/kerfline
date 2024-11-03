import { BadInput } from '../errors.js';
import { round, sum } from '../util/num.js';
import { requireArray, requireAtLeast, requireAbove } from '../util/guard.js';
import { indicationType, measuredBy } from './types.js';
import { actualLength } from '../geom/distance.js';

/**
 * Sizing an indication.
 *
 * Everything measured on a film is measured on a projection, so a length read
 * off the light box is longer than the thing in the weld by the magnification
 * of the setup. On a contact shot that factor is 1 and it does not matter. On
 * a double wall shot of small bore it is well over 1.2 and a quarter inch
 * indication reads as three tenths, which is the difference between a weld
 * that stays in and a weld that comes out.
 *
 * So a measurement carries the factor it was taken at, and the true size is
 * derived rather than stored. Storing the true size would lose the ability to
 * go back to the film and check.
 */

export function makeIndication(input) {
  if (typeof input !== 'object' || input === null) {
    throw new BadInput('an indication must be described by an object', { got: input });
  }
  const type = indicationType(input.type);
  const magnification = input.magnification ?? 1;
  requireAtLeast(magnification, 1, 'magnification');

  const measuredIn = input.measuredIn;
  requireAtLeast(measuredIn, 0, 'measured size');

  const widthIn = input.widthIn === undefined || input.widthIn === null ? null : input.widthIn;
  if (widthIn !== null) requireAtLeast(widthIn, 0, 'measured width');

  return {
    type: type.id,
    label: type.label,
    planar: type.planar,
    measure: type.measure,
    measuredIn: round(measuredIn, 4),
    magnification: round(magnification, 4),
    sizeIn: round(actualLength(measuredIn, magnification), 4),
    widthIn: widthIn === null ? null : round(actualLength(widthIn, magnification), 4),
    at: input.at ?? null,
    film: input.film ?? null,
    note: input.note ?? null,
  };
}

/** The dimension the code limit for this indication is read against. */
export function governingSizeIn(indication) {
  if (measuredBy(indication.type) === 'diameter' && indication.widthIn !== null) {
    return Math.max(indication.sizeIn, indication.widthIn);
  }
  return indication.sizeIn;
}

/**
 * Total length of a set of indications. Used by every code paragraph that
 * caps cumulative length in a run of weld, and it deliberately counts only
 * the ones measured by length: adding a pore diameter to a slag line length
 * gives a number with no meaning.
 */
export function cumulativeLengthIn(indications) {
  requireArray(indications, 'indications');
  const lengths = indications
    .filter((indication) => measuredBy(indication.type) === 'length')
    .map((indication) => indication.sizeIn);
  return round(sum(lengths), 4);
}

/** The longest single indication in a set, or zero when there are none. */
export function longestIn(indications) {
  requireArray(indications, 'indications');
  if (indications.length === 0) return 0;
  return round(Math.max(...indications.map(governingSizeIn)), 4);
}

/** Every indication whose governing size is over a limit. */
export function overLimit(indications, limitIn) {
  requireArray(indications, 'indications');
  requireAtLeast(limitIn, 0, 'limit');
  return indications.filter((indication) => governingSizeIn(indication) > limitIn);
}

/**
 * Cumulative length inside a sliding window of weld, which is the shape most
 * cumulative limits actually take: not "in the whole joint" but "in any six
 * inches of it". Returns the worst window found and where it started.
 */
export function worstWindow(indications, windowIn) {
  requireArray(indications, 'indications');
  requireAbove(windowIn, 0, 'window length');
  const placed = indications.filter((i) => typeof i.at === 'number');
  if (placed.length === 0) {
    return { totalIn: 0, startIn: 0, indications: [] };
  }
  let best = { totalIn: -1, startIn: 0, indications: [] };
  for (const anchor of placed) {
    const startIn = anchor.at;
    const inside = placed.filter((i) => i.at >= startIn && i.at < startIn + windowIn);
    const totalIn = cumulativeLengthIn(inside);
    if (totalIn > best.totalIn) {
      best = { totalIn, startIn: round(startIn, 4), indications: inside };
    }
  }
  return best;
}
