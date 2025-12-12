import { round } from './num.js';
import { requireAtLeast, requireWhole } from './guard.js';

/**
 * Rates, and what an empty set means.
 *
 * Seven modules were dividing one count by another with a guard on the
 * denominator, and they did not agree about what to return when there was
 * nothing to divide. A repair rate over no joints is nought, because nothing
 * has been repaired. An agreement rate over no reviews is one, because
 * nothing has been disagreed with. Both are right and neither is the default,
 * which is why the answer is an argument here rather than a convention.
 *
 * Getting that wrong is not a crash. It is a report that says a crew with no
 * work yet has a zero percent agreement rate, and somebody acting on it.
 */

export function rateOf(part, whole, { whenEmpty = 0, places = 4 } = {}) {
  requireWhole(part, 'part');
  requireWhole(whole, 'whole');
  requireAtLeast(part, 0, 'part');
  requireAtLeast(whole, 0, 'whole');
  if (part > whole) {
    throw new RangeError(`${part} of ${whole} is more than all of them`);
  }
  return whole === 0 ? whenEmpty : round(part / whole, places);
}

/**
 * The rate with the two counts it came from. Every report in this package
 * prints all three, because a rate on its own invites the reader to treat one
 * repair in three joints and thirty in ninety as the same finding.
 */
export function asRate(part, whole, options) {
  return { part, whole, rate: rateOf(part, whole, options) };
}

/** The rate as a percentage, rounded for a report line. */
export function percentOf(part, whole, { whenEmpty = 0, places = 1 } = {}) {
  const rate = rateOf(part, whole, { whenEmpty, places: 6 });
  return round(rate * 100, places);
}

/**
 * How many more clean items it takes to bring a rate under a target. Returns
 * nought when it is already under, and refuses a target of nought, which no
 * number of clean items can reach once anything has gone wrong.
 */
export function itemsToReach(part, whole, target) {
  requireWhole(part, 'part');
  requireWhole(whole, 'whole');
  if (!(target > 0) || target > 1) {
    throw new RangeError('a target rate is above zero and no more than one');
  }
  if (whole === 0 || part / whole <= target) return 0;
  return Math.max(0, Math.ceil(part / target - whole));
}
