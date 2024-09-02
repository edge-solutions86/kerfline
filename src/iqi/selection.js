import { BadInput } from '../errors.js';
import { isFiniteNumber, round } from '../util/num.js';
import { bandTable, bandRow } from '../util/band.js';
import { holeIqi } from './holetype.js';
import { wireDiameterIn, preferredSetForWire } from './wire.js';

/**
 * Choosing the indicator.
 *
 * Two rules stack here and the order matters. The first works out what
 * thickness the choice is based on, and the second reads the table at that
 * thickness. Getting the first one wrong moves the answer by a whole row and
 * the film still looks fine, so it is worth being exact about:
 *
 *   - the nominal single wall counts, whatever the viewing technique is,
 *   - the weld reinforcement counts, capped at what the job allows,
 *   - a backing ring or strip does not count, ever.
 *
 * The last one is the one that gets argued about on site. A backing ring is
 * not part of the weld and the indicator is not chosen on it.
 *
 * The second rule then splits on which side of the part the indicator sits.
 * The source side is the harder place to show a hole, so it calls for a
 * thicker plaque than the film side does at the same thickness.
 */

const SIDES = ['source', 'film'];

const ROWS = [
  { upTo: 0.25, source: { hole: 12, wire: 5 }, film: { hole: 10, wire: 4 } },
  { upTo: 0.375, source: { hole: 15, wire: 6 }, film: { hole: 12, wire: 5 } },
  { upTo: 0.5, source: { hole: 17, wire: 7 }, film: { hole: 15, wire: 6 } },
  { upTo: 0.75, source: { hole: 20, wire: 8 }, film: { hole: 17, wire: 7 } },
  { upTo: 1, source: { hole: 25, wire: 9 }, film: { hole: 20, wire: 8 } },
  { upTo: 1.5, source: { hole: 30, wire: 10 }, film: { hole: 25, wire: 9 } },
  { upTo: 2, source: { hole: 35, wire: 11 }, film: { hole: 30, wire: 10 } },
  { upTo: 2.5, source: { hole: 40, wire: 12 }, film: { hole: 35, wire: 11 } },
  { upTo: 4, source: { hole: 50, wire: 13 }, film: { hole: 40, wire: 12 } },
  { upTo: 6, source: { hole: 60, wire: 14 }, film: { hole: 50, wire: 13 } },
  { upTo: 8, source: { hole: 80, wire: 16 }, film: { hole: 60, wire: 14 } },
  { upTo: 10, source: { hole: 100, wire: 17 }, film: { hole: 80, wire: 16 } },
  { upTo: 12, source: { hole: 120, wire: 18 }, film: { hole: 100, wire: 17 } },
  { upTo: 16, source: { hole: 160, wire: 20 }, film: { hole: 120, wire: 18 } },
  { upTo: 20, source: { hole: 200, wire: 21 }, film: { hole: 160, wire: 20 } },
];

const TABLE = bandTable(
  ROWS.map((row) => ({ upTo: row.upTo, value: row })),
  { name: 'indicator selection' }
);

const ESSENTIAL_HOLE = '2T';

export function sides() {
  return [...SIDES];
}

export function essentialHole() {
  return ESSENTIAL_HOLE;
}

export function selectionTable() {
  return ROWS.map((row, index) => ({
    over: index === 0 ? 0 : ROWS[index - 1].upTo,
    upTo: row.upTo,
    source: { ...row.source },
    film: { ...row.film },
  }));
}

/**
 * The thickness the indicator is chosen on. Reinforcement is capped at what
 * the job permits before it is counted, because a weld crowned higher than
 * the job allows is a weld to be dressed down and not a reason to fit a
 * thicker plaque.
 */
export function basisThicknessIn({
  wallIn,
  reinforcementIn = 0,
  maximumReinforcementIn = Infinity,
  backingIn = 0,
}) {
  requireAbove(wallIn, 0, 'wall thickness');
  requireAtLeast(reinforcementIn, 0, 'weld reinforcement');
  requireAtLeast(backingIn, 0, 'backing thickness');
  if (!(isFiniteNumber(maximumReinforcementIn) || maximumReinforcementIn === Infinity)) {
    throw new BadInput('maximum reinforcement must be a number', { maximumReinforcementIn });
  }
  const counted = Math.min(reinforcementIn, maximumReinforcementIn);
  return round(wallIn + counted, 4);
}

/** Read the table at a thickness for one side of the part. */
export function selectIqi(basisIn, side = 'source') {
  requireSide(side);
  requireAbove(basisIn, 0, 'basis thickness');
  const row = bandRow(TABLE, basisIn).value;
  const chosen = row[side];
  const plaque = holeIqi(chosen.hole);
  return {
    side,
    basisThicknessIn: basisIn,
    holeIdentity: chosen.hole,
    essentialHole: ESSENTIAL_HOLE,
    essentialHoleIn: plaque.holes[ESSENTIAL_HOLE],
    plaqueThicknessIn: plaque.thicknessIn,
    wireNumber: chosen.wire,
    wireDiameterIn: wireDiameterIn(chosen.wire),
    wireSet: preferredSetForWire(chosen.wire),
  };
}

/**
 * The whole choice from the joint description, so a caller never has to
 * remember to run the basis rule first. This is the entry point the technique
 * sheet uses and the one worth keeping honest.
 */
export function selectForJoint(joint) {
  const basisIn = basisThicknessIn(joint);
  const side = joint.side ?? 'source';
  return { ...selectIqi(basisIn, side), backingIgnoredIn: joint.backingIn ?? 0 };
}

/** True when the table has a row for a thickness at all. */
export function selectionCovers(basisIn) {
  return isFiniteNumber(basisIn) && basisIn > 0 && basisIn <= TABLE.ceiling;
}

function requireSide(side) {
  if (!SIDES.includes(side)) {
    throw new BadInput(`side must be one of ${SIDES.join(' or ')}`, {
      got: side,
      known: SIDES,
    });
  }
  return side;
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
