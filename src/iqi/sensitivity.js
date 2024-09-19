import { round } from '../util/num.js';
import { requireAbove, requireAtLeast } from '../util/guard.js';
import { holeIqiThicknessIn, holeDiameterIn } from './holetype.js';
import { wireDiameterIn } from './wire.js';

/**
 * Sensitivity: how small a change in thickness the radiograph can show.
 *
 * A wire indicator answers this directly, because a wire that shows is a
 * thickness change of its own diameter that showed. A hole type plaque does
 * not, because what showed was a hole of one diameter drilled in a plaque of
 * another thickness, and those are two different numbers. The equivalent
 * penetrameter sensitivity puts the two on the same scale:
 *
 *     EPS = 100 * sqrt(T h / 2) / t
 *
 * with T the plaque thickness, h the hole diameter and t the thickness the
 * shot went through. Note it is the drilled hole diameter and not the
 * nominal multiple, which is exactly where the ten thousandth floor on the
 * thin plaques comes back to bite.
 */

const CALLED_FOR_PERCENT = 2;

export function calledForSensitivityPercent() {
  return CALLED_FOR_PERCENT;
}

/** Sensitivity from a plaque and hole that were actually seen on the film. */
export function holeSensitivityPercent({ iqiThicknessIn, holeDiameterIn: hole, thicknessIn }) {
  requireAbove(iqiThicknessIn, 0, 'indicator thickness');
  requireAbove(hole, 0, 'hole diameter');
  requireAbove(thicknessIn, 0, 'penetrated thickness');
  return (100 * Math.sqrt((iqiThicknessIn * hole) / 2)) / thicknessIn;
}

/** The same, looked up from the stocked plaque rather than measured. */
export function holeSensitivityFor({ identity, hole, thicknessIn }) {
  return holeSensitivityPercent({
    iqiThicknessIn: holeIqiThicknessIn(identity),
    holeDiameterIn: holeDiameterIn(identity, hole),
    thicknessIn,
  });
}

/** A wire that showed is a thickness change of its own diameter that showed. */
export function wireSensitivityPercent({ wireDiameterIn: diameter, thicknessIn }) {
  requireAbove(diameter, 0, 'wire diameter');
  requireAbove(thicknessIn, 0, 'penetrated thickness');
  return (100 * diameter) / thicknessIn;
}

export function wireSensitivityFor({ wireNumber, thicknessIn }) {
  return wireSensitivityPercent({ wireDiameterIn: wireDiameterIn(wireNumber), thicknessIn });
}

/**
 * Judge a film. The verdict is on the sensitivity that was achieved against
 * the one the job called for, and it reports both so a rejected film can be
 * argued about with numbers rather than opinions.
 */
export function judgeHoleSensitivity({ identity, hole, thicknessIn, calledFor }) {
  const wanted = calledFor ?? CALLED_FOR_PERCENT;
  requireAbove(wanted, 0, 'called for sensitivity');
  const achieved = holeSensitivityFor({ identity, hole, thicknessIn });
  return {
    achievedPercent: round(achieved, 3),
    calledForPercent: wanted,
    pass: achieved <= wanted,
    marginPercent: round(wanted - achieved, 3),
  };
}

export function judgeWireSensitivity({ wireNumber, thicknessIn, calledFor }) {
  const wanted = calledFor ?? CALLED_FOR_PERCENT;
  requireAbove(wanted, 0, 'called for sensitivity');
  const achieved = wireSensitivityFor({ wireNumber, thicknessIn });
  return {
    achievedPercent: round(achieved, 3),
    calledForPercent: wanted,
    pass: achieved <= wanted,
    marginPercent: round(wanted - achieved, 3),
  };
}

/**
 * The shorthand a reader writes on the sheet. A 25 plaque with its 2T hole
 * showing is "2-2T" when the plaque is two percent of the thickness, and the
 * first number is the plaque as a percentage rather than a fixed 2.
 */
export function sensitivityLabel({ identity, hole, thicknessIn }) {
  requireAbove(thicknessIn, 0, 'penetrated thickness');
  const plaquePercent = (100 * holeIqiThicknessIn(identity)) / thicknessIn;
  return `${round(plaquePercent, 1)}-${hole}`;
}

/**
 * How much thinner a wall would have to get before the shot stopped meeting
 * the called for sensitivity. Useful when a crew is deciding whether one
 * technique can cover a run of joints of slightly differing wall.
 */
export function thicknessHeadroomIn({ identity, hole, thicknessIn, calledFor }) {
  const wanted = calledFor ?? CALLED_FOR_PERCENT;
  requireAtLeast(thicknessIn, 0, 'penetrated thickness');
  const numerator =
    100 * Math.sqrt((holeIqiThicknessIn(identity) * holeDiameterIn(identity, hole)) / 2);
  const limitIn = numerator / wanted;
  return round(thicknessIn - limitIn, 4);
}
