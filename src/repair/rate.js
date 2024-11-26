import { BadInput } from '../errors.js';
import { round } from '../util/num.js';
import { requireArray, requireWhole, requireAbove } from '../util/guard.js';

/**
 * Welder repair rate.
 *
 * The number itself is easy. What makes it worth a module is that a rate is
 * meaningless without a denominator big enough to carry it: one repair in the
 * first three joints of a spread is a hundred percent above the limit and
 * says nothing at all about the welder. So every threshold here needs a
 * minimum sample before it will report an exceedance, and the rate is
 * reported alongside the count it was computed from rather than on its own.
 *
 * The rolling window is the one the job actually runs on. A welder who had a
 * bad afternoon in March and has been clean since is not the same as one who
 * is drifting now, and a rate over the whole job cannot tell them apart.
 */

const THRESHOLD = 0.1;
const MINIMUM_SAMPLE = 20;

export function defaultThreshold() {
  return THRESHOLD;
}

export function minimumSample() {
  return MINIMUM_SAMPLE;
}

function countable(joints) {
  requireArray(joints, 'joints');
  return joints.filter((joint) => {
    if (typeof joint !== 'object' || joint === null) {
      throw new BadInput('every entry must be a joint record', { got: joint });
    }
    return joint.disposition !== undefined && joint.disposition !== null;
  });
}

/** Repairs over joints shot, with the counts it came from. */
export function repairRate(joints) {
  const counted = countable(joints);
  const repaired = counted.filter((joint) => joint.disposition !== 'accept').length;
  return {
    shot: counted.length,
    repaired,
    rate: counted.length === 0 ? 0 : round(repaired / counted.length, 4),
  };
}

/** The same, split by welder stencil. */
export function welderRates(joints) {
  const counted = countable(joints);
  const byWelder = new Map();
  for (const joint of counted) {
    const welder = joint.welder ?? 'unknown';
    if (!byWelder.has(welder)) byWelder.set(welder, []);
    byWelder.get(welder).push(joint);
  }
  return [...byWelder.entries()]
    .map(([welder, theirs]) => ({ welder, ...repairRate(theirs) }))
    .sort((a, b) => b.rate - a.rate || a.welder.localeCompare(b.welder));
}

/** The rate over the most recent joints only. */
export function rollingRate(joints, windowSize = MINIMUM_SAMPLE) {
  requireWhole(windowSize, 'window size');
  requireAbove(windowSize, 0, 'window size');
  const counted = countable(joints);
  return repairRate(counted.slice(-windowSize));
}

/**
 * Whether a welder is over the limit. Returns false while the sample is too
 * small to say, and says so, rather than reporting an exceedance nobody
 * should act on.
 */
export function exceedsThreshold(joints, options = {}) {
  const threshold = options.threshold ?? THRESHOLD;
  const sample = options.minimumSample ?? MINIMUM_SAMPLE;
  requireAbove(threshold, 0, 'threshold');
  const measured = options.windowSize
    ? rollingRate(joints, options.windowSize)
    : repairRate(joints);
  if (measured.shot < sample) {
    return {
      ...measured,
      threshold,
      exceeds: false,
      enoughToSay: false,
      shortBy: sample - measured.shot,
    };
  }
  return {
    ...measured,
    threshold,
    exceeds: measured.rate > threshold,
    enoughToSay: true,
    shortBy: 0,
  };
}

/** Every welder currently over the limit, worst first. */
export function weldersOverThreshold(joints, options = {}) {
  const counted = countable(joints);
  const welders = new Set(counted.map((joint) => joint.welder ?? 'unknown'));
  return [...welders]
    .map((welder) => {
      const theirs = counted.filter((joint) => (joint.welder ?? 'unknown') === welder);
      return { welder, ...exceedsThreshold(theirs, options) };
    })
    .filter((entry) => entry.exceeds)
    .sort((a, b) => b.rate - a.rate);
}

/**
 * How many more clean joints it would take to bring a welder back under the
 * limit. This is the number a foreman actually asks for.
 */
export function cleanJointsToRecover(joints, options = {}) {
  const threshold = options.threshold ?? THRESHOLD;
  const measured = repairRate(joints);
  if (measured.shot === 0 || measured.rate <= threshold) return 0;
  // repaired / (shot + n) <= threshold
  const needed = Math.ceil(measured.repaired / threshold - measured.shot);
  return Math.max(0, needed);
}
