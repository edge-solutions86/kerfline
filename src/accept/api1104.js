import { round } from '../util/num.js';
import { requireArray, requireAbove } from '../util/guard.js';
import { governingSizeIn, cumulativeLengthIn, worstWindow } from '../indication/measure.js';
import { makeVerdict, accept } from './verdict.js';
import { settle } from './common.js';

/**
 * API 1104 acceptance, for girth welds in pipeline service.
 *
 * The shape of this code is worth understanding before reading the numbers.
 * Almost every defect is capped three ways at once: the length of any single
 * one, the total in any twelve inches of weld, and a percentage of the whole
 * weld length. A joint can pass the first two and fail the third, and on a
 * small diameter joint the third is the one that bites, because twelve inches
 * is most of the way round a two inch pipe.
 *
 * The percentage cap is what makes this code different from the pressure
 * vessel ones, and it is why weldLengthIn is a required argument here rather
 * than an optional extra.
 */

const CODE = 'API1104';
const WINDOW_IN = 12;
const PERCENT_CAP = 0.08;

const RULES = {
  IP: {
    clause: '9.3.2',
    singleIn: 1,
    windowIn: 1,
    percent: PERCENT_CAP,
    label: 'incomplete penetration',
  },
  IF: {
    clause: '9.3.4',
    singleIn: 1,
    windowIn: 1,
    percent: PERCENT_CAP,
    label: 'incomplete fusion',
  },
  SLAG: { clause: '9.3.6', singleIn: 2, windowIn: 2, percent: null, label: 'slag inclusion' },
  HOLLOW_BEAD: {
    clause: '9.3.7',
    singleIn: 0.5,
    windowIn: 2,
    percent: null,
    label: 'hollow bead',
  },
  BURN_THROUGH: {
    clause: '9.3.5',
    singleIn: 0.25,
    windowIn: 0.5,
    percent: null,
    label: 'burn through',
  },
  UNDERCUT: {
    clause: '9.3.8',
    singleIn: 2,
    windowIn: 2,
    percent: PERCENT_CAP,
    label: 'undercut',
  },
};

const SLAG_WIDTH_IN = 0.0625;
const PORE_IN = 0.125;
const PORE_WALL_FRACTION = 0.25;
const CLUSTER_IN = 0.5;
const CRATER_CRACK_IN = 5 / 32;

export function codeName() {
  return CODE;
}

export function windowLengthIn() {
  return WINDOW_IN;
}

export function ruleFor(typeId) {
  return RULES[typeId] ?? null;
}

/**
 * A crack comes out. The only survivor is a shallow crater or star crack
 * under five thirty seconds of an inch, and even that is only tolerated
 * because grinding it out costs more than it is worth.
 */
export function judgeCrack(indication) {
  const size = governingSizeIn(indication);
  const isCrater = indication.note === 'crater' || indication.note === 'star';
  if (isCrater && size < CRATER_CRACK_IN) {
    return accept(CODE, '9.3.9');
  }
  return makeVerdict({
    disposition: 'reject',
    code: CODE,
    clause: '9.3.9',
    reason: `crack ${round(size, 3)} in long`,
    indication,
    measured: size,
  });
}

/** A single pore is capped both absolutely and against the wall it is in. */
export function judgePorosity(indication, wallIn) {
  requireAbove(wallIn, 0, 'wall thickness');
  const size = governingSizeIn(indication);
  const wallCap = round(wallIn * PORE_WALL_FRACTION, 4);
  const cap = Math.min(PORE_IN, wallCap);
  if (size <= cap) return accept(CODE, '9.3.3');
  return makeVerdict({
    disposition: 'repair',
    code: CODE,
    clause: '9.3.3',
    reason: `pore ${round(size, 3)} in across, cap is ${cap} in`,
    indication,
    measured: size,
  });
}

export function judgeCluster(indication) {
  const size = governingSizeIn(indication);
  if (size <= CLUSTER_IN) return accept(CODE, '9.3.3');
  return makeVerdict({
    disposition: 'repair',
    code: CODE,
    clause: '9.3.3',
    reason: `cluster ${round(size, 3)} in across, cap is ${CLUSTER_IN} in`,
    indication,
    measured: size,
  });
}

/** Slag is capped on length and, separately, on width. */
export function judgeSlag(indication) {
  const rule = RULES.SLAG;
  const size = governingSizeIn(indication);
  if (indication.widthIn !== null && indication.widthIn > SLAG_WIDTH_IN) {
    return makeVerdict({
      disposition: 'repair',
      code: CODE,
      clause: rule.clause,
      reason: `slag ${round(indication.widthIn, 4)} in wide, cap is ${SLAG_WIDTH_IN} in`,
      indication,
      measured: indication.widthIn,
    });
  }
  if (size <= rule.singleIn) return accept(CODE, rule.clause);
  return makeVerdict({
    disposition: 'repair',
    code: CODE,
    clause: rule.clause,
    reason: `slag ${round(size, 3)} in long, cap is ${rule.singleIn} in`,
    indication,
    measured: size,
  });
}

/** Everything covered by a plain single length cap. */
export function judgeByLength(indication) {
  const rule = RULES[indication.type];
  if (!rule) return accept(CODE, '9.3');
  const size = governingSizeIn(indication);
  if (size <= rule.singleIn) return accept(CODE, rule.clause);
  return makeVerdict({
    disposition: 'repair',
    code: CODE,
    clause: rule.clause,
    reason: `${rule.label} ${round(size, 3)} in long, cap is ${rule.singleIn} in`,
    indication,
    measured: size,
  });
}

export function judgeOne(indication, wallIn) {
  if (indication.type === 'CRACK') return judgeCrack(indication);
  if (indication.type === 'POROSITY') return judgePorosity(indication, wallIn);
  if (indication.type === 'CLUSTER') return judgeCluster(indication);
  if (indication.type === 'SLAG') return judgeSlag(indication);
  return judgeByLength(indication);
}

/**
 * The two caps that are not about any single indication: the total in any
 * twelve inches, and the total as a fraction of the whole weld.
 */
export function judgeAggregate(indications, typeId, weldLengthIn) {
  const rule = RULES[typeId];
  requireAbove(weldLengthIn, 0, 'weld length');
  const ofType = indications.filter((indication) => indication.type === typeId);
  if (!rule || ofType.length === 0) return [];

  const verdicts = [];
  const window = worstWindow(ofType, WINDOW_IN);
  const total = cumulativeLengthIn(ofType);
  const inWindow = window.totalIn > 0 ? window.totalIn : total;

  if (inWindow > rule.windowIn) {
    verdicts.push(
      makeVerdict({
        disposition: 'repair',
        code: CODE,
        clause: rule.clause,
        reason: `${rule.label} totals ${round(inWindow, 3)} in in ${WINDOW_IN} in, cap is ${rule.windowIn} in`,
        measured: inWindow,
      })
    );
  }

  if (rule.percent !== null) {
    const cap = round(weldLengthIn * rule.percent, 4);
    if (total > cap) {
      verdicts.push(
        makeVerdict({
          disposition: 'repair',
          code: CODE,
          clause: rule.clause,
          reason: `${rule.label} totals ${round(total, 3)} in, over ${rule.percent * 100} percent of the weld`,
          measured: total,
        })
      );
    }
  }

  return verdicts;
}

/** Judge a whole joint against the code. */
export function judgeJoint({ indications, wallIn, weldLengthIn }) {
  requireArray(indications, 'indications');
  requireAbove(wallIn, 0, 'wall thickness');
  requireAbove(weldLengthIn, 0, 'weld length');

  const perIndication = indications.map((indication) => judgeOne(indication, wallIn));
  const aggregate = Object.keys(RULES).flatMap((typeId) =>
    judgeAggregate(indications, typeId, weldLengthIn)
  );
  return settle([...perIndication, ...aggregate], { code: CODE, clause: '9.3' });
}
