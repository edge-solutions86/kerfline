import { round } from '../util/num.js';
import { requireArray, requireAbove, requireOneOf } from '../util/guard.js';
import { governingSizeIn, worstWindow, cumulativeLengthIn } from '../indication/measure.js';
import { makeVerdict, accept, combine } from './verdict.js';

/**
 * ASME B31.3 process piping, Table 341.3.2.
 *
 * The service the line is in changes the criteria, so nothing here can be
 * evaluated without knowing it. Severe cyclic service tightens the undercut
 * limit and Category D loosens incomplete penetration from "never" to "within
 * a limit", which is the largest single difference between any two columns of
 * that table.
 *
 * Almost every limit is a fraction of Tw, the thickness of the thinner of the
 * two components joined. That includes the window the cumulative limit is
 * measured over, which is twelve Tw rather than a fixed twelve inches. So on
 * a quarter inch wall the code is looking at three inches of weld at a time,
 * and on an inch wall it is looking at a foot. Getting Tw from the thicker
 * component makes every limit on the joint too generous at once.
 */

const CODE = 'B31.3';
const CLAUSE = '341.3.2';

const SERVICES = ['normal', 'severe_cyclic', 'category_d'];

const RULES = {
  normal: {
    undercutIn: 0.0625,
    undercutFraction: 0.25,
    penetrationPermitted: false,
    poreFraction: 0.25,
  },
  severe_cyclic: {
    undercutIn: 0.03125,
    undercutFraction: 0.25,
    penetrationPermitted: false,
    poreFraction: 0.2,
  },
  category_d: {
    undercutIn: 0.0625,
    undercutFraction: 0.25,
    penetrationPermitted: true,
    penetrationWindowMultiple: 1.5,
    poreFraction: 0.25,
  },
};

const INCLUSION_FRACTION = 1 / 3;
const INCLUSION_WIDTH_IN = 0.098;
const WINDOW_MULTIPLE = 12;

export function codeName() {
  return CODE;
}

export function services() {
  return [...SERVICES];
}

export function rulesFor(service) {
  requireOneOf(service, SERVICES, 'fluid service');
  return { ...RULES[service] };
}

/** The window the cumulative limits are read over, which scales with Tw. */
export function windowLengthIn(thinnerWallIn) {
  requireAbove(thinnerWallIn, 0, 'thinner component thickness');
  return round(WINDOW_MULTIPLE * thinnerWallIn, 4);
}

/** The cap on a single inclusion, both ways it is capped. */
export function inclusionLimits(thinnerWallIn) {
  requireAbove(thinnerWallIn, 0, 'thinner component thickness');
  const byThickness = round(thinnerWallIn * INCLUSION_FRACTION, 4);
  return {
    lengthIn: byThickness,
    widthIn: round(Math.min(INCLUSION_WIDTH_IN, byThickness), 4),
    cumulativeIn: round(thinnerWallIn, 4),
    windowIn: windowLengthIn(thinnerWallIn),
  };
}

export function undercutLimitIn(service, thinnerWallIn) {
  const rules = rulesFor(service);
  requireAbove(thinnerWallIn, 0, 'thinner component thickness');
  return round(Math.min(rules.undercutIn, thinnerWallIn * rules.undercutFraction), 4);
}

function reject(indication, reason, clause = CLAUSE) {
  return makeVerdict({
    disposition: 'reject',
    code: CODE,
    clause,
    reason,
    indication,
    measured: governingSizeIn(indication),
  });
}

function repair(indication, reason, measured, clause = CLAUSE) {
  return makeVerdict({
    disposition: 'repair',
    code: CODE,
    clause,
    reason,
    indication,
    measured,
  });
}

export function judgeOne(indication, { service, thinnerWallIn }) {
  const rules = rulesFor(service);
  requireAbove(thinnerWallIn, 0, 'thinner component thickness');
  const size = governingSizeIn(indication);

  if (indication.type === 'CRACK' || indication.type === 'IF') {
    return reject(indication, `${indication.label} is not permitted in any service`);
  }

  if (indication.type === 'IP') {
    if (!rules.penetrationPermitted) {
      return reject(indication, `${indication.label} is not permitted in ${service} service`);
    }
    return accept(CODE, CLAUSE);
  }

  if (indication.type === 'SLAG' || indication.type === 'TUNGSTEN') {
    const limits = inclusionLimits(thinnerWallIn);
    if (indication.widthIn !== null && indication.widthIn > limits.widthIn) {
      return repair(
        indication,
        `inclusion ${round(indication.widthIn, 4)} in wide, cap is ${limits.widthIn} in`,
        indication.widthIn
      );
    }
    if (size > limits.lengthIn) {
      return repair(
        indication,
        `inclusion ${round(size, 4)} in long, cap is ${limits.lengthIn} in`,
        size
      );
    }
    return accept(CODE, CLAUSE);
  }

  if (indication.type === 'UNDERCUT') {
    const limit = undercutLimitIn(service, thinnerWallIn);
    if (size > limit) {
      return repair(indication, `undercut ${round(size, 4)} in, cap is ${limit} in`, size);
    }
    return accept(CODE, CLAUSE);
  }

  if (indication.type === 'POROSITY' || indication.type === 'CLUSTER') {
    const cap = round(thinnerWallIn * rules.poreFraction, 4);
    if (size > cap) {
      return repair(indication, `pore ${round(size, 4)} in across, cap is ${cap} in`, size);
    }
    return accept(CODE, CLAUSE);
  }

  return accept(CODE, CLAUSE);
}

/** The cumulative inclusion cap, read over a window that scales with Tw. */
export function judgeCumulative(indications, thinnerWallIn) {
  const limits = inclusionLimits(thinnerWallIn);
  const inclusions = indications.filter(
    (indication) => indication.type === 'SLAG' || indication.type === 'TUNGSTEN'
  );
  if (inclusions.length === 0) return [];

  const window = worstWindow(inclusions, limits.windowIn);
  const total = window.totalIn > 0 ? window.totalIn : cumulativeLengthIn(inclusions);
  if (total <= limits.cumulativeIn) return [];
  return [
    makeVerdict({
      disposition: 'repair',
      code: CODE,
      clause: CLAUSE,
      reason: `inclusions total ${round(total, 4)} in in ${limits.windowIn} in of weld, cap is ${limits.cumulativeIn} in`,
      measured: total,
    }),
  ];
}

export function judgeJoint({ indications, thinnerWallIn, service = 'normal' }) {
  requireArray(indications, 'indications');
  requireOneOf(service, SERVICES, 'fluid service');
  requireAbove(thinnerWallIn, 0, 'thinner component thickness');
  const single = indications.map((indication) =>
    judgeOne(indication, { service, thinnerWallIn })
  );
  const cumulative = judgeCumulative(indications, thinnerWallIn);
  const all = [...single, ...cumulative];
  return combine(all.length === 0 ? [accept(CODE, CLAUSE)] : all, { code: CODE });
}
