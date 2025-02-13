import { BadInput } from '../errors.js';
import { requireArray } from '../util/guard.js';
import { requireDay, withinSpan } from '../util/dates.js';
import { compareIds } from '../util/ids.js';

/**
 * Reading the journal back.
 *
 * Nothing here caches. Every query walks the entries, because the journal is
 * a few thousand lines on the largest job this shop has run and an index that
 * can go stale is a worse problem than a walk that takes a millisecond.
 *
 * The one function worth reading carefully is latestPerSubject. A joint that
 * was accepted, then reopened, then accepted again has three verdict entries
 * and only the last one is its disposition. Anything that counts joints by
 * filtering on kind and tallying will count that joint three times, and the
 * number it produces looks entirely reasonable.
 */

function entriesOf(journal) {
  if (typeof journal !== 'object' || journal === null || !Array.isArray(journal.entries)) {
    throw new BadInput('not a journal', { got: journal });
  }
  return journal.entries;
}

export function where(journal, predicate) {
  if (typeof predicate !== 'function') {
    throw new BadInput('a query needs a predicate', { got: typeof predicate });
  }
  return entriesOf(journal).filter((entry) => predicate(entry));
}

export function byKind(journal, kind) {
  return where(journal, (entry) => entry.kind === kind);
}

export function bySubject(journal, subject) {
  return where(journal, (entry) => entry.subject === subject);
}

export function between(journal, from, to) {
  requireDay(from, 'period start');
  requireDay(to, 'period end');
  return where(journal, (entry) => withinSpan(entry.day, from, to));
}

/** Every subject the journal mentions, in register order. */
export function subjects(journal) {
  const seen = new Set(entriesOf(journal).map((entry) => entry.subject));
  return [...seen].sort(compareIds);
}

/** Everything about one subject, oldest first, which is its history. */
export function timeline(journal, subject) {
  return bySubject(journal, subject);
}

/**
 * The last entry of a kind for each subject. This is how current state is
 * read, and it is a different question from "every entry of that kind".
 */
export function latestPerSubject(journal, kind) {
  const latest = new Map();
  for (const entry of byKind(journal, kind)) {
    latest.set(entry.subject, entry);
  }
  return [...latest.values()].sort((a, b) => compareIds(a.subject, b.subject));
}

/** The last entry of a kind for one subject, or null when there is none. */
export function latestFor(journal, { subject, kind }) {
  const mine = where(journal, (entry) => entry.subject === subject && entry.kind === kind);
  return mine.length === 0 ? null : mine[mine.length - 1];
}

/** Group entries by whatever key a caller wants, keeping journal order. */
export function groupBy(journal, keyOf) {
  if (typeof keyOf !== 'function') {
    throw new BadInput('grouping needs a key function', { got: typeof keyOf });
  }
  const groups = new Map();
  for (const entry of entriesOf(journal)) {
    const key = keyOf(entry);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(entry);
  }
  return groups;
}

/** How many entries of each kind, for the front page of a job report. */
export function countByKind(journal) {
  const counts = {};
  for (const entry of entriesOf(journal)) {
    counts[entry.kind] = (counts[entry.kind] ?? 0) + 1;
  }
  return counts;
}

/**
 * The dispositions as they stand, one per joint. Built from the last verdict
 * for each subject rather than from every verdict, which is the whole reason
 * this function exists instead of a filter at the call site.
 */
export function currentDispositions(journal) {
  return latestPerSubject(journal, 'verdict').map((entry) => ({
    subject: entry.subject,
    disposition: entry.payload.disposition ?? null,
    day: entry.day,
    seq: entry.seq,
  }));
}

/** Subjects that have been shot but never had a verdict recorded. */
export function awaitingVerdict(journal) {
  const judged = new Set(byKind(journal, 'verdict').map((entry) => entry.subject));
  const shot = new Set(byKind(journal, 'shot').map((entry) => entry.subject));
  return [...shot].filter((subject) => !judged.has(subject)).sort(compareIds);
}

/** The span of days the journal covers, or null when it is empty. */
export function coveredSpan(journal) {
  const entries = entriesOf(journal);
  if (entries.length === 0) return null;
  return { from: entries[0].day, to: entries[entries.length - 1].day };
}

/** Merge several journals' entries into one list in day order. */
export function mergeEntries(journals) {
  requireArray(journals, 'journals');
  return journals
    .flatMap((journal) => entriesOf(journal).map((entry) => ({ ...entry, job: journal.name })))
    .sort((a, b) => (a.day === b.day ? a.seq - b.seq : a.day < b.day ? -1 : 1));
}
