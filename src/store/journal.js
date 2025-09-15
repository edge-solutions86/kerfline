import { BadInput, Conflict, NotFound } from '../errors.js';
import { requireArray, requireLabel } from '../util/guard.js';
import { requireDay, daysBetween } from '../util/dates.js';
import { chain, stableStringify, digestOf } from '../util/checksum.js';

/**
 * The record store.
 *
 * Everything this package writes down goes in a journal and nothing is ever
 * edited. A joint that was accepted and then found to have been read wrong
 * gets a second entry saying so; the first entry stays exactly as it was.
 * That is not a storage preference, it is the requirement: the question asked
 * in an audit is not what the record says now, it is what it said in March
 * and who changed it since.
 *
 * Each entry carries a checksum chained onto the one before it, so an entry
 * altered in the middle of the file invalidates every entry after it and
 * cannot be quietly repaired by recomputing one line.
 */

const KINDS = [
  'joint',
  'shot',
  'interpretation',
  'verdict',
  'repair',
  'survey',
  'source',
  'certification',
  'note',
];

export function entryKinds() {
  return [...KINDS];
}

export function makeJournal(name) {
  requireLabel(name, 'a journal is named after the job it belongs to');
  return Object.freeze({ name: name.trim(), entries: Object.freeze([]) });
}

/**
 * Append an entry. Days must not run backwards: a record dated before the one
 * it follows is either a mistake or a rewrite, and both are worth stopping.
 */
export function append(journal, { kind, day, subject, payload = {} }) {
  assertJournal(journal);
  if (!KINDS.includes(kind)) {
    throw new BadInput(`a journal entry is one of ${KINDS.join(', ')}`, {
      got: kind,
      known: KINDS,
    });
  }
  requireDay(day, 'entry date');
  requireLabel(subject, 'every entry names what it is about');

  const previous = journal.entries[journal.entries.length - 1] ?? null;
  if (previous && daysBetween(previous.day, day) < 0) {
    throw new Conflict('an entry cannot be dated before the one it follows', {
      previous: previous.day,
      entry: day,
    });
  }

  const seq = journal.entries.length + 1;
  const body = stableStringify({ seq, kind, day, subject, payload });
  const entry = Object.freeze({
    seq,
    kind,
    day,
    subject: subject.trim(),
    payload: Object.freeze({ ...payload }),
    id: digestOf({ seq, kind, day, subject, payload }),
    checksum: chain(previous ? previous.checksum : null, body),
  });

  return Object.freeze({
    name: journal.name,
    entries: Object.freeze([...journal.entries, entry]),
  });
}

/** Append several entries in order, stopping on the first one that is refused. */
export function appendAll(journal, entries) {
  requireArray(entries, 'entries');
  return entries.reduce((current, entry) => append(current, entry), journal);
}

/**
 * Recompute the chain and report the first entry that does not match. An
 * entry altered in place breaks at itself; one removed breaks at the entry
 * that followed it.
 */
export function verify(journal) {
  assertJournal(journal);
  let previous = null;
  for (const entry of journal.entries) {
    const body = stableStringify({
      seq: entry.seq,
      kind: entry.kind,
      day: entry.day,
      subject: entry.subject,
      payload: entry.payload,
    });
    const expected = chain(previous ? previous.checksum : null, body);
    if (expected !== entry.checksum) {
      return {
        intact: false,
        brokenAt: entry.seq,
        reason: `entry ${entry.seq} does not match the chain`,
        entries: journal.entries.length,
      };
    }
    previous = entry;
  }
  return { intact: true, brokenAt: null, reason: null, entries: journal.entries.length };
}

export function entryCount(journal) {
  assertJournal(journal);
  return journal.entries.length;
}

export function entryAt(journal, seq) {
  assertJournal(journal);
  const found = journal.entries.find((entry) => entry.seq === seq);
  if (!found) {
    throw new NotFound(`no entry ${seq} in ${journal.name}`, {
      seq,
      entries: journal.entries.length,
    });
  }
  return found;
}

/**
 * Fold the journal into whatever shape a caller wants. This is the only way
 * current state is derived, so there is never a stored total that can drift
 * away from the entries it was supposed to summarise.
 */
export function replay(journal, reducer, initial) {
  assertJournal(journal);
  if (typeof reducer !== 'function') {
    throw new BadInput('replay needs a reducer', { got: typeof reducer });
  }
  return journal.entries.reduce((state, entry) => reducer(state, entry), initial);
}

/** The journal as lines of text, one entry each, for an export or a diff. */
export function serialise(journal) {
  assertJournal(journal);
  return journal.entries
    .map((entry) =>
      stableStringify({
        seq: entry.seq,
        kind: entry.kind,
        day: entry.day,
        subject: entry.subject,
        payload: entry.payload,
        checksum: entry.checksum,
      })
    )
    .join('\n');
}

function assertJournal(journal) {
  if (typeof journal !== 'object' || journal === null || !Array.isArray(journal.entries)) {
    throw new BadInput('not a journal', { got: journal });
  }
}
