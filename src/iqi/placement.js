import { BadInput, Unsupported } from '../errors.js';
import { isFiniteNumber, round } from '../util/num.js';

/**
 * Where the indicator goes, and the shim that has to go under it.
 *
 * A hole type plaque laid on the base metal beside the weld is sitting on a
 * thinner section than the weld it is standing in for, so it would show its
 * hole too easily and report a sensitivity the weld never had. The shim under
 * it makes up that difference. A wire indicator does not have the problem
 * because it goes on the weld itself, across it, so the wires lie over the
 * same thickness the weld does.
 *
 * The other half of the rule is the lead F. An indicator on the film side is
 * doing an easier job than one on the source side, and a reader has no way to
 * tell from the image which side it was on. So it is marked, and an unmarked
 * indicator is read as source side by default, which is the strict reading.
 */

const PLACEMENTS = ['on_weld', 'beside_weld'];

export function placements() {
  return [...PLACEMENTS];
}

/**
 * A shim is needed when a hole type plaque sits on the base metal rather than
 * on the weld. Wire indicators never need one.
 */
export function shimRequired({ iqiType, placement }) {
  requirePlacement(placement);
  if (iqiType === 'wire') return false;
  if (iqiType !== 'hole') {
    throw new Unsupported(`no placement rule for indicator type ${iqiType}`, { iqiType });
  }
  return placement === 'beside_weld';
}

/**
 * The shim makes the plaque sit at the same radiographic thickness the weld
 * presents, so it is the reinforcement on both faces of the weld. It has to
 * be of a material radiographically similar to the weld and it has to stand
 * out past the plaque so the reader can see it is there.
 */
export function shimThicknessIn({ reinforcementIn = 0, secondFaceIn = 0 }) {
  requireAtLeast(reinforcementIn, 0, 'weld reinforcement');
  requireAtLeast(secondFaceIn, 0, 'second face reinforcement');
  return round(reinforcementIn + secondFaceIn, 4);
}

/**
 * The plan for one indicator: where it goes, whether it needs a shim and how
 * thick, and what has to be written next to it in lead.
 */
export function placementPlan({
  iqiType,
  placement = 'on_weld',
  side = 'source',
  reinforcementIn = 0,
  secondFaceIn = 0,
}) {
  requirePlacement(placement);
  if (side !== 'source' && side !== 'film') {
    throw new BadInput('side must be source or film', { got: side });
  }
  const needsShim = shimRequired({ iqiType, placement });
  return {
    iqiType,
    placement,
    side,
    shimRequired: needsShim,
    shimThicknessIn: needsShim ? shimThicknessIn({ reinforcementIn, secondFaceIn }) : 0,
    leadMarking: side === 'film' ? 'F' : null,
    marksRequired: side === 'film' ? ['F'] : [],
  };
}

/**
 * Read back what an indicator on a film means. An unmarked indicator is taken
 * as source side, which is the strict reading and the one that fails a film
 * rather than passing it on a guess.
 */
export function sideFromMarking(marking) {
  if (marking === null || marking === undefined || marking === '') return 'source';
  const text = String(marking).trim().toUpperCase();
  if (text === 'F') return 'film';
  throw new BadInput(`cannot read ${marking} as an indicator side marking`, { marking });
}

/** Whether the marking on a film agrees with the side the sheet claims. */
export function markingAgrees({ marking, side }) {
  return sideFromMarking(marking) === side;
}

function requirePlacement(placement) {
  if (!PLACEMENTS.includes(placement)) {
    throw new BadInput(`placement must be one of ${PLACEMENTS.join(' or ')}`, {
      got: placement,
      known: PLACEMENTS,
    });
  }
  return placement;
}

function requireAtLeast(value, floor, what) {
  if (!isFiniteNumber(value)) {
    throw new BadInput(`${what} must be a finite number`, { got: value });
  }
  if (value < floor) {
    throw new BadInput(`${what} must not be below ${floor}`, { got: value });
  }
  return value;
}
