import { BadInput, NotFound } from '../errors.js';
import { round } from '../util/num.js';
import { requireArray, requireAbove, requireOneOf } from '../util/guard.js';

/**
 * Welding procedures and the range each one qualifies.
 *
 * A procedure is qualified by welding one test coupon, and that single coupon
 * then covers a band of thicknesses, a band of diameters and a set of
 * positions. None of those bands is centred on the coupon: the thickness band
 * runs from half the coupon to twice it, the diameter band runs upward only,
 * and the position set is a lattice rather than a range, because welding
 * uphill on a fixed pipe qualifies more than welding flat on a rolled one and
 * not the other way round.
 *
 * Getting any of the three wrong produces a joint welded outside procedure,
 * which is a finding against the whole job rather than against that joint.
 */

const POSITIONS = ['1G', '2G', '5G', '6G'];

// What welding one coupon in each position qualifies you to weld.
const POSITION_COVER = {
  '1G': ['1G'],
  '2G': ['1G', '2G'],
  '5G': ['1G', '5G'],
  '6G': ['1G', '2G', '5G', '6G'],
};

export function positions() {
  return [...POSITIONS];
}

export function positionsCoveredBy(position) {
  requireOneOf(position, POSITIONS, 'test position');
  return [...POSITION_COVER[position]];
}

/**
 * The thickness band one coupon qualifies. Below three eighths the floor is
 * the shop minimum rather than half the coupon, because half of a very thin
 * coupon is thinner than anything that gets welded.
 */
export function thicknessRange(couponIn) {
  requireAbove(couponIn, 0, 'coupon thickness');
  const floor = couponIn < 0.375 ? Math.min(0.0625, couponIn) : couponIn / 2;
  const ceiling = couponIn >= 0.75 ? 8 : couponIn * 2;
  return { minimumIn: round(floor, 4), maximumIn: round(ceiling, 4) };
}

/**
 * The diameter band. It runs upward only: a procedure proved on small bore
 * covers everything larger, and one proved on a large pipe does not come back
 * down to small bore, where the geometry is the hard part.
 */
export function diameterRange(couponOdIn) {
  requireAbove(couponOdIn, 0, 'coupon diameter');
  if (couponOdIn < 1) return { minimumIn: round(couponOdIn, 4), maximumIn: Infinity };
  if (couponOdIn < 2.875) return { minimumIn: 1, maximumIn: Infinity };
  return { minimumIn: 2.875, maximumIn: Infinity };
}

export function makeProcedure({
  id,
  process,
  couponThicknessIn,
  couponOdIn,
  testPosition,
  materialGroup = 'P1',
}) {
  if (typeof id !== 'string' || id.trim() === '') {
    throw new BadInput('a procedure is identified by its number', { got: id });
  }
  requireOneOf(testPosition, POSITIONS, 'test position');
  const thickness = thicknessRange(couponThicknessIn);
  const diameter = diameterRange(couponOdIn);
  return {
    id: id.trim(),
    process,
    materialGroup,
    couponThicknessIn: round(couponThicknessIn, 4),
    couponOdIn: round(couponOdIn, 4),
    testPosition,
    thicknessRange: thickness,
    diameterRange: diameter,
    positionsCovered: positionsCoveredBy(testPosition),
  };
}

/**
 * Whether a procedure covers a joint, and when it does not, which of the four
 * things is out. Reporting all of them at once matters because a joint welded
 * on the wrong procedure is usually out on more than one.
 */
export function coversJoint(procedure, joint) {
  if (typeof joint !== 'object' || joint === null) {
    throw new BadInput('a coverage check needs a joint', { got: joint });
  }
  const reasons = [];

  if (joint.wallIn < procedure.thicknessRange.minimumIn) {
    reasons.push(
      `wall ${joint.wallIn} in is under the qualified ${procedure.thicknessRange.minimumIn} in`
    );
  }
  if (joint.wallIn > procedure.thicknessRange.maximumIn) {
    reasons.push(
      `wall ${joint.wallIn} in is over the qualified ${procedure.thicknessRange.maximumIn} in`
    );
  }
  if (joint.odIn < procedure.diameterRange.minimumIn) {
    reasons.push(
      `diameter ${joint.odIn} in is under the qualified ${procedure.diameterRange.minimumIn} in`
    );
  }
  if (joint.position && !procedure.positionsCovered.includes(joint.position)) {
    reasons.push(
      `position ${joint.position} is not covered by a ${procedure.testPosition} test`
    );
  }
  if (joint.process && procedure.process && joint.process !== procedure.process) {
    reasons.push(`process ${joint.process} is not ${procedure.process}`);
  }

  return { procedure: procedure.id, covers: reasons.length === 0, reasons };
}

/** The procedures on file that cover a joint, best fit first. */
export function proceduresFor(procedures, joint) {
  requireArray(procedures, 'procedures');
  return procedures
    .map((procedure) => ({ procedure, check: coversJoint(procedure, joint) }))
    .filter(({ check }) => check.covers)
    .map(({ procedure }) => procedure)
    .sort(
      (a, b) =>
        Math.abs(a.couponThicknessIn - joint.wallIn) -
        Math.abs(b.couponThicknessIn - joint.wallIn)
    );
}

/** The one procedure to use, or a refusal naming why nothing fits. */
export function chooseProcedure(procedures, joint) {
  const fitting = proceduresFor(procedures, joint);
  if (fitting.length === 0) {
    const tried = procedures.map((procedure) => coversJoint(procedure, joint));
    throw new NotFound('no qualified procedure covers this joint', {
      jointId: joint.id ?? null,
      tried,
    });
  }
  return fitting[0];
}
