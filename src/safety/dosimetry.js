import { BadInput } from '../errors.js';
import { round, sum } from '../util/num.js';
import { requireArray, requireAtLeast, requireLabel } from '../util/guard.js';
import { requireDay, withinSpan, addYears } from '../util/dates.js';

/**
 * Dose records for the crew.
 *
 * The annual limit is five thousand millirem to the whole body. The shop does
 * not run anybody up to it: there are two investigation levels well below,
 * and crossing either one starts a conversation rather than stopping work.
 * That is the entire point of setting them where they are.
 *
 * Two things here are deliberately awkward. A quarterly badge that has not
 * come back from the reader is not a zero, so an unreturned badge makes the
 * total unknown rather than making it smaller. And the pocket dosimeter is
 * not the record of dose; it is the thing that tells a technician during the
 * shift that something is wrong. When the two disagree the badge is the
 * record and the disagreement is the finding.
 */

const ANNUAL_LIMIT_MREM = 5000;
const LEVEL_ONE = 0.1;
const LEVEL_TWO = 0.3;

export function annualLimitMrem() {
  return ANNUAL_LIMIT_MREM;
}

export function investigationLevels() {
  return {
    levelOneMrem: ANNUAL_LIMIT_MREM * LEVEL_ONE,
    levelTwoMrem: ANNUAL_LIMIT_MREM * LEVEL_TWO,
  };
}

export function makeBadgeRecord({ person, from, to, mrem, returned = true }) {
  requireLabel(person, 'a dose record names the person it belongs to');
  requireDay(from, 'period start');
  requireDay(to, 'period end');
  if (to < from) {
    throw new BadInput('a badge period cannot end before it starts', { from, to });
  }
  if (returned) {
    requireAtLeast(mrem, 0, 'recorded dose');
  } else if (mrem !== null && mrem !== undefined) {
    throw new BadInput('a badge that has not come back cannot carry a reading', { mrem });
  }
  return {
    person: person.trim(),
    from,
    to,
    mrem: returned ? round(mrem, 1) : null,
    returned,
  };
}

/**
 * The dose in a window. An unreturned badge makes the total unknown, which is
 * reported rather than quietly dropped.
 */
export function doseInPeriod(records, { from, to }) {
  requireArray(records, 'dose records');
  requireDay(from, 'period start');
  requireDay(to, 'period end');
  const inWindow = records.filter(
    (record) => withinSpan(record.from, from, to) || withinSpan(record.to, from, to)
  );
  const outstanding = inWindow.filter((record) => !record.returned);
  const known = inWindow.filter((record) => record.returned);
  return {
    from,
    to,
    records: inWindow.length,
    mrem: round(sum(known.map((record) => record.mrem)), 1),
    outstandingBadges: outstanding.length,
    complete: outstanding.length === 0,
  };
}

/** The rolling twelve month dose ending on a day. */
export function annualDose(records, endingOn) {
  requireDay(endingOn, 'date');
  return doseInPeriod(records, { from: addYears(endingOn, -1), to: endingOn });
}

/**
 * Judge a person's standing. An incomplete record never reports "within the
 * limit", because the missing badge is exactly where the dose would be.
 */
export function judgeAnnual(records, endingOn) {
  const period = annualDose(records, endingOn);
  const levels = investigationLevels();
  let standing = 'within limits';
  if (period.mrem > ANNUAL_LIMIT_MREM) standing = 'over the annual limit';
  else if (period.mrem > levels.levelTwoMrem) standing = 'level two investigation';
  else if (period.mrem > levels.levelOneMrem) standing = 'level one investigation';

  return {
    ...period,
    limitMrem: ANNUAL_LIMIT_MREM,
    ...levels,
    standing: period.complete ? standing : 'incomplete record',
    remainingMrem: round(Math.max(0, ANNUAL_LIMIT_MREM - period.mrem), 1),
    fractionUsed: round(period.mrem / ANNUAL_LIMIT_MREM, 4),
  };
}

/**
 * Compare a pocket dosimeter against the badge for the same period. A large
 * disagreement is a finding on the equipment or the record keeping, and the
 * badge is what stands either way.
 */
export function comparePocket({ badgeMrem, pocketMrem, tolerance = 0.3 }) {
  requireAtLeast(badgeMrem, 0, 'badge dose');
  requireAtLeast(pocketMrem, 0, 'pocket dose');
  requireAtLeast(tolerance, 0, 'tolerance');
  const larger = Math.max(badgeMrem, pocketMrem);
  if (larger === 0) {
    return { agrees: true, differenceMrem: 0, ofRecord: 0, note: 'both read nothing' };
  }
  const difference = Math.abs(badgeMrem - pocketMrem);
  return {
    agrees: difference / larger <= tolerance,
    differenceMrem: round(difference, 1),
    ofRecord: badgeMrem,
    note: 'the badge is the record of dose whatever the pocket meter said',
  };
}
