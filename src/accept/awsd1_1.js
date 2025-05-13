import { round } from '../util/num.js';
import { requireArray, requireAbove, requireOneOf } from '../util/guard.js';
import { governingSizeIn } from '../indication/measure.js';
import { makeVerdict, accept, combine } from './verdict.js';

/**
 * AWS D1.1 for structural welds.
 *
 * Three rules stack and they catch different things.
 *
 * The size rule caps any single discontinuity, scaled on the weld thickness
 * and floored so that thin material is not held to something unmeasurable.
 *
 * The sum rule catches a scatter of small ones. Anything under an eighth of
 * an inch is individually acceptable at any size the size rule allows, so
 * without this rule a weld could be full of them. The sum is read over one
 * linear inch, which is a much tighter window than either pressure code uses.
 *
 * The clearance rule catches two acceptable discontinuities sitting on top of
 * each other. Metal between two voids is doing less work than metal beside
 * one, so the code wants a gap proportional to the larger of the pair.
 *
 * Cyclically loaded work is judged on the same three rules with everything
 * roughly halved, because a discontinuity in a member that flexes is a crack
 * starter rather than a void.
 */

const CODE = 'AWSD1.1';
const LOADINGS = ['static', 'cyclic'];

const RULES = {
  static: {
    clause: '6.12.1',
    fraction: 1 / 3,
    floorIn: 0.125,
    ceilingIn: 0.75,
    smallIn: 0.125,
    sumIn: 0.375,
    windowIn: 1,
    clearanceMultiple: 3,
  },
  cyclic: {
    clause: '6.12.2',
    fraction: 1 / 6,
    floorIn: 0.0625,
    ceilingIn: 0.375,
    smallIn: 0.0625,
    sumIn: 0.1875,
    windowIn: 1,
    clearanceMultiple: 6,
  },
};

export function codeName() {
  return CODE;
}

export function loadings() {
  return [...LOADINGS];
}

export function rulesFor(loading) {
  requireOneOf(loading, LOADINGS, 'loading');
  return { ...RULES[loading] };
}

/** The cap on any single discontinuity, scaled on the weld and bracketed. */
export function maxDiscontinuityIn(thicknessIn, loading = 'static') {
  requireAbove(thicknessIn, 0, 'weld thickness');
  const rules = rulesFor(loading);
  const scaled = thicknessIn * rules.fraction;
  return round(Math.min(rules.ceilingIn, Math.max(rules.floorIn, scaled)), 4);
}

/** The clearance a discontinuity needs from the next one along. */
export function requiredClearanceIn(largestIn, loading = 'static') {
  requireAbove(largestIn, 0, 'discontinuity size');
  return round(largestIn * rulesFor(loading).clearanceMultiple, 4);
}

export function judgeOne(indication, { thicknessIn, loading = 'static' }) {
  const rules = rulesFor(loading);
  if (indication.planar) {
    return makeVerdict({
      disposition: 'reject',
      code: CODE,
      clause: rules.clause,
      reason: `${indication.label} is not permitted at any size`,
      indication,
      measured: governingSizeIn(indication),
    });
  }
  const size = governingSizeIn(indication);
  const cap = maxDiscontinuityIn(thicknessIn, loading);
  if (size <= cap) return accept(CODE, rules.clause);
  return makeVerdict({
    disposition: 'repair',
    code: CODE,
    clause: rules.clause,
    reason: `${indication.label} ${round(size, 4)} in, cap is ${cap} in`,
    indication,
    measured: size,
  });
}

/**
 * The sum rule over a sliding inch. Only the small discontinuities count
 * towards it; a large one has already been judged on its own size and
 * counting it twice would be judging the same metal twice.
 */
export function judgeSmallSum(indications, { loading = 'static' } = {}) {
  requireArray(indications, 'indications');
  const rules = rulesFor(loading);
  const small = indications
    .filter((indication) => !indication.planar)
    .filter((indication) => typeof indication.at === 'number')
    .filter((indication) => governingSizeIn(indication) < rules.smallIn);
  if (small.length === 0) return [];

  let worst = { total: 0, startIn: 0 };
  for (const anchor of small) {
    const inside = small.filter(
      (other) => other.at >= anchor.at && other.at < anchor.at + rules.windowIn
    );
    const total = inside.reduce((sum, other) => sum + governingSizeIn(other), 0);
    if (total > worst.total) worst = { total: round(total, 4), startIn: anchor.at };
  }

  if (worst.total <= rules.sumIn) return [];
  return [
    makeVerdict({
      disposition: 'repair',
      code: CODE,
      clause: rules.clause,
      reason: `small discontinuities total ${worst.total} in in one inch of weld, cap is ${rules.sumIn} in`,
      measured: worst.total,
    }),
  ];
}

/** The clearance rule between neighbouring discontinuities. */
export function judgeClearance(indications, { loading = 'static' } = {}) {
  requireArray(indications, 'indications');
  const placed = indications
    .filter((indication) => !indication.planar)
    .filter((indication) => typeof indication.at === 'number')
    .slice()
    .sort((a, b) => a.at - b.at);

  const verdicts = [];
  for (let i = 1; i < placed.length; i += 1) {
    const previous = placed[i - 1];
    const current = placed[i];
    const gap = current.at - (previous.at + governingSizeIn(previous));
    const largest = Math.max(governingSizeIn(previous), governingSizeIn(current));
    const wanted = requiredClearanceIn(largest, loading);
    if (gap < wanted) {
      verdicts.push(
        makeVerdict({
          disposition: 'repair',
          code: CODE,
          clause: rulesFor(loading).clause,
          reason: `${round(Math.max(0, gap), 4)} in clearance where ${wanted} in is needed`,
          measured: round(Math.max(0, gap), 4),
        })
      );
    }
  }
  return verdicts;
}

export function judgeJoint({ indications, thicknessIn, loading = 'static' }) {
  requireArray(indications, 'indications');
  requireAbove(thicknessIn, 0, 'weld thickness');
  requireOneOf(loading, LOADINGS, 'loading');
  const single = indications.map((indication) =>
    judgeOne(indication, { thicknessIn, loading })
  );
  const sums = judgeSmallSum(indications, { loading });
  const clearances = judgeClearance(indications, { loading });
  const all = [...single, ...sums, ...clearances];
  return combine(all.length === 0 ? [accept(CODE, rulesFor(loading).clause)] : all, {
    code: CODE,
  });
}
