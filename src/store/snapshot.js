import { BadInput } from '../errors.js';
import { requireArray } from '../util/guard.js';
import { requireDay, daysBetween } from '../util/dates.js';
import { compareIds } from '../util/ids.js';
import { verify } from './journal.js';

/**
 * What the record said on a day.
 *
 * An append only journal can answer this and a mutable store cannot, which is
 * most of the reason the journal is the shape it is. A client asking why a
 * joint was signed off in March is not asking what the register says now.
 *
 * A snapshot is the journal with the later entries dropped, so it verifies
 * against the same chain: the checksums of the entries that remain are
 * exactly what they were, and a snapshot that fails verification is a
 * snapshot of a journal that was already broken.
 */

export function snapshotAt(journal, day) {
  assertJournal(journal);
  requireDay(day, 'date');
  const entries = journal.entries.filter((entry) => daysBetween(entry.day, day) >= 0);
  return Object.freeze({
    name: journal.name,
    asOf: day,
    entries: Object.freeze(entries),
    dropped: journal.entries.length - entries.length,
  });
}

/** A snapshot verifies exactly as the journal it came from did. */
export function verifySnapshot(snapshot) {
  return verify(snapshot);
}

/** The disposition of every subject as the record stood on a day. */
export function stateAt(journal, day) {
  const snapshot = snapshotAt(journal, day);
  const latest = new Map();
  for (const entry of snapshot.entries) {
    if (entry.kind !== 'verdict') continue;
    latest.set(entry.subject, entry.payload.disposition ?? null);
  }
  return Object.fromEntries([...latest.entries()].sort((a, b) => compareIds(a[0], b[0])));
}

/**
 * What changed between two states. Three kinds of change matter and they are
 * reported separately, because a joint that went from repair to accept is
 * ordinary progress and one that went from accept to repair is a reopening
 * somebody has to explain.
 */
export function diffStates(before, after) {
  if (
    typeof before !== 'object' ||
    before === null ||
    typeof after !== 'object' ||
    after === null
  ) {
    throw new BadInput('two states are needed to compare', { before, after });
  }
  const added = [];
  const changed = [];
  const removed = [];

  for (const [subject, disposition] of Object.entries(after)) {
    if (!(subject in before)) added.push({ subject, disposition });
    else if (before[subject] !== disposition) {
      changed.push({ subject, from: before[subject], to: disposition });
    }
  }
  for (const subject of Object.keys(before)) {
    if (!(subject in after)) removed.push({ subject, disposition: before[subject] });
  }

  const reopened = changed.filter((row) => row.from === 'accept' && row.to !== 'accept');
  return { added, changed, removed, reopened, quiet: added.length + changed.length === 0 };
}

/** What moved between two days. */
export function changedBetween(journal, from, to) {
  requireDay(from, 'period start');
  requireDay(to, 'period end');
  if (daysBetween(from, to) < 0) {
    throw new BadInput('the period runs backwards', { from, to });
  }
  return diffStates(stateAt(journal, from), stateAt(journal, to));
}

/**
 * Every day the record changed, which is where an audit starts. Reading only
 * the days something happened is far quicker than walking a year of them.
 */
export function activeDays(journal) {
  assertJournal(journal);
  return [...new Set(journal.entries.map((entry) => entry.day))].sort();
}

/** The record as it stood at the end of each active day. */
export function history(journal) {
  return activeDays(journal).map((day) => ({ day, state: stateAt(journal, day) }));
}

/** Anything that was accepted and later stopped being accepted. */
export function reopenings(journal) {
  const days = activeDays(journal);
  const found = [];
  for (let i = 1; i < days.length; i += 1) {
    const diff = diffStates(stateAt(journal, days[i - 1]), stateAt(journal, days[i]));
    for (const row of diff.reopened) {
      found.push({ day: days[i], ...row });
    }
  }
  return found;
}

function assertJournal(journal) {
  if (typeof journal !== 'object' || journal === null || !Array.isArray(journal.entries)) {
    throw new BadInput('not a journal', { got: journal });
  }
  requireArray(journal.entries, 'entries');
}
