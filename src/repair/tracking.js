import { BadInput, Conflict } from '../errors.js';
import { requireArray, requireWhole } from '../util/guard.js';
import { requireDay, daysBetween } from '../util/dates.js';
import { clockSpanDegrees } from '../geom/pipe.js';

/**
 * Repair chains.
 *
 * A joint that fails is ground out and rewelded, and the repair is shot
 * again. The rule that matters is that the same piece of weld is not repaired
 * twice: a second failure at the same spot means the joint comes out. What
 * counts as "the same spot" is a distance along the weld, not an exact
 * position, because the ground out area is wider than the indication was.
 *
 * Everything here is derived from the list of attempts rather than stored as
 * a counter, so a chain reconstructed from an archive gives the same answers
 * as one built live.
 */

const SAME_SPOT_DEGREES = 30;
const MAXIMUM_REPAIRS = 2;

export function sameSpotDegrees() {
  return SAME_SPOT_DEGREES;
}

export function maximumRepairs() {
  return MAXIMUM_REPAIRS;
}

/** One attempt on a joint: the original shot or a repair of it. */
export function makeAttempt({ label, day, disposition, at = null, note = null }) {
  if (typeof label !== 'string' || label.trim() === '') {
    throw new BadInput('every attempt carries a label', { got: label });
  }
  requireDay(day, 'attempt date');
  if (!['accept', 'repair', 'reject'].includes(disposition)) {
    throw new BadInput('an attempt ends in accept, repair or reject', { got: disposition });
  }
  if (at !== null && typeof at !== 'string') {
    throw new BadInput('a repair location is written as a clock position', { got: at });
  }
  return { label: label.trim(), day, disposition, at, note };
}

/** The label the next attempt gets: the original shot, then R1, then R2. */
export function nextLabel(attempts) {
  requireArray(attempts, 'attempts');
  if (attempts.length === 0) return 'original';
  const repairs = attempts.filter((attempt) => attempt.label !== 'original').length;
  return `R${repairs + 1}`;
}

/** Repairs already carried out at the same place on the weld. */
export function repairsAt(attempts, clock) {
  requireArray(attempts, 'attempts');
  if (typeof clock !== 'string') {
    throw new BadInput('a location is written as a clock position', { got: clock });
  }
  return attempts.filter(
    (attempt) =>
      attempt.label !== 'original' &&
      attempt.at !== null &&
      clockSpanDegrees(attempt.at, clock) <= SAME_SPOT_DEGREES
  );
}

/**
 * Whether the joint can be repaired again at a place, or has to come out.
 * A joint already rejected is not repaired at all.
 */
export function canRepairAt(attempts, clock) {
  requireArray(attempts, 'attempts');
  if (attempts.some((attempt) => attempt.disposition === 'reject')) {
    return { allowed: false, reason: 'the joint has already been rejected' };
  }
  const already = repairsAt(attempts, clock);
  if (already.length >= MAXIMUM_REPAIRS) {
    return {
      allowed: false,
      reason: `${already.length} repairs have already been made within ${SAME_SPOT_DEGREES} degrees of ${clock}`,
    };
  }
  return { allowed: true, reason: null, priorRepairs: already.length };
}

/** Add an attempt, refusing one the chain does not permit. */
export function addAttempt(attempts, attempt) {
  requireArray(attempts, 'attempts');
  if (attempts.length > 0) {
    const last = attempts[attempts.length - 1];
    if (daysBetween(last.day, attempt.day) < 0) {
      throw new Conflict('an attempt cannot be dated before the one it follows', {
        previous: last.day,
        attempt: attempt.day,
      });
    }
    if (last.disposition === 'accept') {
      throw new Conflict('the joint was already accepted', { at: last.day });
    }
  }
  if (attempt.label !== 'original' && attempt.at !== null) {
    const check = canRepairAt(attempts, attempt.at);
    if (!check.allowed) {
      throw new Conflict(check.reason, { at: attempt.at });
    }
  }
  return [...attempts, attempt];
}

/** The state of a joint after everything that has been done to it. */
export function chainState(attempts) {
  requireArray(attempts, 'attempts');
  if (attempts.length === 0) {
    return { status: 'not shot', repairs: 0, open: true, days: 0 };
  }
  const last = attempts[attempts.length - 1];
  const repairs = attempts.filter((attempt) => attempt.label !== 'original').length;
  const days = daysBetween(attempts[0].day, last.day);
  if (last.disposition === 'accept') {
    return { status: 'accepted', repairs, open: false, days, closedOn: last.day };
  }
  if (last.disposition === 'reject') {
    return { status: 'cut out', repairs, open: false, days, closedOn: last.day };
  }
  return { status: 'awaiting repair', repairs, open: true, days };
}

/** Joints whose chain has been open longer than a job allows. */
export function overdue(chains, days, today) {
  requireWhole(days, 'day limit');
  requireDay(today, 'date');
  return chains.filter((chain) => {
    const state = chainState(chain.attempts);
    if (!state.open || chain.attempts.length === 0) return false;
    const since = chain.attempts[chain.attempts.length - 1].day;
    return daysBetween(since, today) > days;
  });
}
