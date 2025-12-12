import { BadInput } from '../errors.js';
import { round } from '../util/num.js';
import { requireArray, requireLabel, requireAbove, requireAtLeast } from '../util/guard.js';
import { requireDay } from '../util/dates.js';
import { compareIds } from '../util/ids.js';
import { renderTable, keyValueBlock, wrap } from '../util/text.js';
import { heading, block, page, pageWidth } from './layout.js';
import { shuffle, seedFrom } from '../sampling/select.js';
import { rateOf } from '../util/rate.js';

/**
 * Second reading.
 *
 * A share of every reader's interpretations is read again by somebody
 * senior, without being told what the first reader said. The point is not to
 * catch a bad reader, which almost never happens; it is to catch a drift,
 * where a reader has quietly moved where they put a borderline call and
 * nobody has noticed because every individual film looked reasonable.
 *
 * So the number that matters is not how many disagreements there were but
 * which way they went. Ten disagreements evenly split is two people reading
 * borderline films differently. Ten disagreements all in the same direction
 * is a reader who has moved, and that is a finding.
 */

const WIDTH = pageWidth();
const DEFAULT_FRACTION = 0.1;

export function defaultFraction() {
  return DEFAULT_FRACTION;
}

/**
 * Pick the interpretations to read again. Seeded off the job so an auditor
 * can rerun the selection and get the same films.
 */
export function selectForReview(interpretations, { job, fraction = DEFAULT_FRACTION }) {
  requireArray(interpretations, 'interpretations');
  requireLabel(job, 'a review selection belongs to a job');
  requireAbove(fraction, 0, 'review fraction');
  if (fraction > 1) {
    throw new BadInput('a review fraction cannot be more than all of them', { fraction });
  }
  const wanted = Math.min(interpretations.length, Math.ceil(interpretations.length * fraction));
  return shuffle(interpretations, seedFrom(job))
    .slice(0, wanted)
    .sort((a, b) => compareIds(a.jointId ?? '', b.jointId ?? ''));
}

export function makeReview({ jointId, day, reviewer, firstCall, secondCall, note = null }) {
  requireDay(day, 'review date');
  requireLabel(jointId, 'a review names the joint');
  requireLabel(reviewer, 'a review names who read it again');
  requireLabel(firstCall, 'a review records what the first reader called it');
  requireLabel(secondCall, 'a review records what the second reader called it');
  return {
    jointId: jointId.trim(),
    day,
    reviewer: reviewer.trim(),
    firstCall: firstCall.trim(),
    secondCall: secondCall.trim(),
    agrees: firstCall.trim() === secondCall.trim(),
    note,
  };
}

const SEVERITY = ['accept', 'repair', 'reject'];

/** Which way a disagreement went, from the first reader's point of view. */
export function direction(review) {
  if (review.agrees) return 'agreed';
  const first = SEVERITY.indexOf(review.firstCall);
  const second = SEVERITY.indexOf(review.secondCall);
  if (first === -1 || second === -1) return 'not comparable';
  return second > first ? 'first reader was lenient' : 'first reader was strict';
}

/**
 * The summary that matters. Reports the split as well as the rate, because
 * an even split and a one sided one mean different things.
 */
export function agreement(reviews) {
  requireArray(reviews, 'reviews');
  const agreed = reviews.filter((review) => review.agrees).length;
  const directions = reviews.map((review) => direction(review));
  const lenient = directions.filter((d) => d === 'first reader was lenient').length;
  const strict = directions.filter((d) => d === 'first reader was strict').length;
  const disagreements = reviews.length - agreed;

  return {
    reviewed: reviews.length,
    agreed,
    disagreements,
    // Nothing disagreed with is full agreement, not none of it.
    rate: rateOf(agreed, reviews.length, { whenEmpty: 1 }),
    lenient,
    strict,
    oneSided: disagreements > 0 && (lenient === 0 || strict === 0),
    drifting: disagreements >= 3 && (lenient === 0 || strict === 0),
  };
}

/** Reviews by reader, so a drift belongs to somebody. */
export function byReader(reviews, readerOf) {
  requireArray(reviews, 'reviews');
  if (typeof readerOf !== 'function') {
    throw new BadInput('grouping by reader needs a way to find the reader', {
      got: typeof readerOf,
    });
  }
  const groups = new Map();
  for (const review of reviews) {
    const reader = readerOf(review) ?? 'unknown';
    if (!groups.has(reader)) groups.set(reader, []);
    groups.get(reader).push(review);
  }
  return [...groups.entries()]
    .map(([reader, theirs]) => ({ reader, ...agreement(theirs) }))
    .sort((a, b) => a.rate - b.rate || a.reader.localeCompare(b.reader));
}

export function disagreements(reviews) {
  requireArray(reviews, 'reviews');
  return reviews
    .filter((review) => !review.agrees)
    .map((review) => ({ ...review, direction: direction(review) }));
}

export function renderReview(reviews, { job = null, fraction = DEFAULT_FRACTION } = {}) {
  requireAtLeast(fraction, 0, 'review fraction');
  const summary = agreement(reviews);
  const found = disagreements(reviews);

  const blocks = [
    heading('SECOND READING', WIDTH),
    block(
      'SUMMARY',
      keyValueBlock([
        ['Job', job ?? ''],
        ['Share reviewed', `${round(fraction * 100, 1)} percent`],
        ['Reviewed', String(summary.reviewed)],
        ['Agreed', `${summary.agreed} of ${summary.reviewed}`],
        ['Lenient', String(summary.lenient)],
        ['Strict', String(summary.strict)],
      ]),
      WIDTH
    ),
    block(
      'DISAGREEMENTS',
      found.length === 0
        ? 'None.'
        : renderTable(found, [
            { key: 'jointId', label: 'joint' },
            { key: 'firstCall', label: 'first' },
            { key: 'secondCall', label: 'second' },
            { key: 'direction', label: 'direction' },
          ]),
      WIDTH
    ),
  ];

  if (summary.drifting) {
    blocks.push(
      block(
        'FINDING',
        wrap(
          'Every disagreement went the same way. That is a reader who has moved, not a run of borderline films.',
          WIDTH
        ),
        WIDTH
      )
    );
  }
  return page(blocks);
}
