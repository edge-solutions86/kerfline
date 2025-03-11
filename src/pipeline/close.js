import { BadInput, Conflict } from '../errors.js';
import { requireDay } from '../util/dates.js';
import { append } from '../store/journal.js';
import { timeline, latestFor } from '../store/query.js';
import { makeAttempt, addAttempt, chainState, canRepairAt } from '../repair/tracking.js';

/**
 * Closing a joint out.
 *
 * A joint is closed when its last verdict is an acceptance, or when it has
 * been cut out. Everything else is open, including a joint that was accepted
 * and later reopened, which is why the state is derived from the journal
 * every time rather than stored on the joint.
 *
 * The repair chain is rebuilt from the journal for the same reason. A stored
 * repair counter and a journal that disagree is a problem nobody can resolve
 * from the record, and the journal is the thing the client has a copy of.
 */

export function attemptsFor(journal, jointId) {
  const history = timeline(journal, jointId);
  const attempts = [];
  let repairs = 0;
  for (const entry of history) {
    if (entry.kind !== 'verdict') continue;
    const label = attempts.length === 0 ? 'original' : `R${repairs}`;
    attempts.push(
      makeAttempt({
        label,
        day: entry.day,
        disposition: entry.payload.disposition ?? 'repair',
        at: entry.payload.at ?? null,
      })
    );
    if (entry.payload.disposition === 'repair') repairs += 1;
  }
  return attempts;
}

/** The state of a joint, derived from the journal and nothing else. */
export function jointState(journal, jointId) {
  const attempts = attemptsFor(journal, jointId);
  const shot = latestFor(journal, { subject: jointId, kind: 'shot' });
  const state = chainState(attempts);
  return {
    jointId,
    ...state,
    everShot: shot !== null,
    attempts: attempts.length,
  };
}

/**
 * Record a repair having been carried out. Refuses when the chain will not
 * take another repair at that place, which is the rule that stops a joint
 * being ground out a third time instead of being cut out.
 */
export function recordRepair(journal, { jointId, day, at, welder = null, note = null }) {
  requireDay(day, 'repair date');
  if (typeof jointId !== 'string' || jointId.trim() === '') {
    throw new BadInput('a repair names the joint it is on', { got: jointId });
  }
  const attempts = attemptsFor(journal, jointId);
  if (at !== null && at !== undefined) {
    const check = canRepairAt(attempts, at);
    if (!check.allowed) {
      throw new Conflict(check.reason, { jointId, at });
    }
  }
  // Rebuilding through addAttempt applies the ordering rules as well.
  addAttempt(attempts, makeAttempt({ label: 'pending', day, disposition: 'repair', at }));

  return append(journal, {
    kind: 'repair',
    day,
    subject: jointId,
    payload: { at: at ?? null, welder, note },
  });
}

/**
 * Close a joint. A joint whose last verdict was not an acceptance cannot be
 * closed as accepted, because that would be a second answer to a question the
 * journal has already answered.
 */
export function closeJoint(journal, { jointId, day, disposition, by = null }) {
  requireDay(day, 'closing date');
  if (!['accept', 'reject'].includes(disposition)) {
    throw new BadInput('a joint closes as accepted or cut out', { got: disposition });
  }
  const state = jointState(journal, jointId);
  if (!state.everShot) {
    throw new Conflict('a joint that was never shot cannot be closed', { jointId });
  }
  if (!state.open) {
    throw new Conflict(`${jointId} is already ${state.status}`, {
      jointId,
      status: state.status,
    });
  }
  if (disposition === 'accept' && state.status === 'awaiting repair') {
    throw new Conflict('the last verdict called for a repair that has not been shot again', {
      jointId,
    });
  }
  return append(journal, {
    kind: 'verdict',
    day,
    subject: jointId,
    payload: { disposition, closedBy: by, closing: true },
  });
}

/** Every joint the journal has touched that is still open. */
export function openJoints(journal) {
  const seen = new Set(journal.entries.map((entry) => entry.subject));
  return [...seen]
    .map((jointId) => jointState(journal, jointId))
    .filter((state) => state.everShot && state.open);
}
