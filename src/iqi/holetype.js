import { BadInput, NotFound } from '../errors.js';
import { isFiniteNumber, round } from '../util/num.js';

/**
 * Hole type image quality indicators.
 *
 * A hole type indicator is a flat plaque of the same material group as the
 * weld, with three holes drilled through it at one, two and four times its
 * own thickness. The identity number is that thickness in thousandths of an
 * inch, so a 25 plaque is 0.025 in thick and its 2T hole is 0.050 in across.
 *
 * The one exception that catches everybody: no hole is drilled smaller than
 * ten thousandths whatever the arithmetic says, because a smaller hole cannot
 * be made accurately or seen reliably. On the thin plaques that means the 1T
 * and sometimes the 2T hole are larger than their nominal multiple, and a
 * sensitivity worked out from the multiple rather than the drilled size comes
 * out wrong.
 */

const MINIMUM_HOLE_IN = 0.01;

const IDENTITIES = [
  5, 7, 10, 12, 15, 17, 20, 25, 30, 35, 40, 45, 50, 60, 70, 80, 100, 120, 140, 160, 200, 240,
  280,
];

const HOLES = ['1T', '2T', '4T'];

export function holeIqiIdentities() {
  return [...IDENTITIES];
}

export function holeNames() {
  return [...HOLES];
}

export function hasHoleIqi(identity) {
  return IDENTITIES.includes(identity);
}

/** The plaque thickness in inches, which is the identity over a thousand. */
export function holeIqiThicknessIn(identity) {
  requireIdentity(identity);
  return identity / 1000;
}

/**
 * The drilled diameter of a hole, honouring the ten thousandth floor. Pass
 * the hole by name so a caller cannot quietly hand over a multiple that is
 * not one of the three that exist.
 */
export function holeDiameterIn(identity, hole) {
  const thickness = holeIqiThicknessIn(identity);
  const multiple = holeMultiple(hole);
  return round(Math.max(thickness * multiple, MINIMUM_HOLE_IN), 5);
}

export function holeMultiple(hole) {
  const index = HOLES.indexOf(hole);
  if (index === -1) {
    throw new BadInput(`no such hole ${hole}`, { hole, known: HOLES });
  }
  return [1, 2, 4][index];
}

/** True when the drilled hole had to be opened up past its nominal size. */
export function holeIsAtFloor(identity, hole) {
  const thickness = holeIqiThicknessIn(identity);
  return thickness * holeMultiple(hole) < MINIMUM_HOLE_IN;
}

export function minimumHoleDiameterIn() {
  return MINIMUM_HOLE_IN;
}

/** The whole plaque as one record, which is what a technique sheet prints. */
export function holeIqi(identity) {
  requireIdentity(identity);
  return {
    identity,
    thicknessIn: holeIqiThicknessIn(identity),
    holes: HOLES.reduce((acc, hole) => {
      acc[hole] = holeDiameterIn(identity, hole);
      return acc;
    }, {}),
    atFloor: HOLES.filter((hole) => holeIsAtFloor(identity, hole)),
  };
}

/** The next plaque up or down the stocked set, for a substitution. */
export function nextHoleIqi(identity, direction = 1) {
  requireIdentity(identity);
  if (direction !== 1 && direction !== -1) {
    throw new BadInput('direction must be 1 or -1', { direction });
  }
  const index = IDENTITIES.indexOf(identity) + direction;
  if (index < 0 || index >= IDENTITIES.length) {
    throw new NotFound(`no plaque past ${identity} in that direction`, { identity, direction });
  }
  return IDENTITIES[index];
}

function requireIdentity(identity) {
  if (!isFiniteNumber(identity)) {
    throw new BadInput('an indicator identity must be a number', { got: identity });
  }
  if (!IDENTITIES.includes(identity)) {
    throw new NotFound(`no hole type indicator numbered ${identity} is stocked`, {
      identity,
      known: IDENTITIES,
    });
  }
  return identity;
}
