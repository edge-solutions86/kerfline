import { BadInput, Conflict } from '../errors.js';
import { requireArray, requireWhole } from '../util/guard.js';

/**
 * Bringing old records forward.
 *
 * A journal written two years ago has payloads in the shape the tool used
 * then. Those files are the client's copy of the record and they are not
 * going to be rewritten, so the reading side has to know how to bring them
 * forward. The alternative is a growing pile of "if this field is missing
 * then" scattered through every consumer, which is where this started.
 *
 * Two rules make this safe to rely on. A migration only ever moves forward by
 * one version, so the chain is checkable one step at a time. And migrating
 * changes the payload but never the checksum on the stored entry, because the
 * checksum attests to what was written, not to what it means today.
 */

const CURRENT = {
  joint: 2,
  shot: 2,
  interpretation: 1,
  verdict: 2,
  repair: 1,
  survey: 1,
  source: 1,
  certification: 1,
  note: 1,
};

const MIGRATIONS = {
  // Version 1 recorded the wall as "thickness". It was renamed once the
  // difference between wall and penetrated thickness started to matter.
  joint: {
    1: (payload) => {
      const { thickness, ...rest } = payload;
      return thickness === undefined ? { ...rest } : { ...rest, wallIn: thickness };
    },
  },
  // Version 1 recorded a single film count. Version 2 splits the count from
  // whether the shot was ready to fire.
  shot: {
    1: (payload) => ({ ready: true, ...payload }),
  },
  // Version 1 had no clause on a verdict, which made a finding impossible to
  // argue about later. Anything without one is marked as such rather than
  // being given a clause it never had.
  verdict: {
    1: (payload) => ({ clause: 'not recorded', reasons: [], ...payload }),
  },
};

export function knownKinds() {
  return Object.keys(CURRENT).sort();
}

export function currentVersion(kind) {
  const version = CURRENT[kind];
  if (version === undefined) {
    throw new BadInput(`no record kind ${kind}`, { kind, known: knownKinds() });
  }
  return version;
}

export function needsMigration(entry) {
  const version = entry.version ?? 1;
  return version < currentVersion(entry.kind);
}

/**
 * Bring one entry forward to the current version, one step at a time. An
 * entry already at the current version comes back untouched, and one from a
 * newer version than this build knows about is refused rather than guessed.
 */
export function migrateEntry(entry) {
  if (typeof entry !== 'object' || entry === null) {
    throw new BadInput('a record must be an object', { got: entry });
  }
  const target = currentVersion(entry.kind);
  let version = entry.version ?? 1;
  requireWhole(version, 'record version');

  if (version > target) {
    throw new Conflict(`this record is version ${version} and the tool knows ${target}`, {
      kind: entry.kind,
      version,
      known: target,
    });
  }

  let payload = { ...entry.payload };
  const steps = [];
  while (version < target) {
    const step = MIGRATIONS[entry.kind]?.[version];
    if (!step) {
      throw new Conflict(`no migration from version ${version} of a ${entry.kind}`, {
        kind: entry.kind,
        version,
      });
    }
    payload = step(payload);
    steps.push(version);
    version += 1;
  }

  return { ...entry, version, payload, migratedFrom: steps.length > 0 ? steps : null };
}

/** Bring a whole journal forward, leaving the checksums as they were. */
export function migrateJournal(journal) {
  if (typeof journal !== 'object' || journal === null || !Array.isArray(journal.entries)) {
    throw new BadInput('not a journal', { got: journal });
  }
  const entries = journal.entries.map((entry) => migrateEntry(entry));
  return {
    ...journal,
    entries,
    migrated: entries.filter((entry) => entry.migratedFrom !== null).length,
  };
}

/** What a journal would need, without changing anything. */
export function surveyJournal(journal) {
  requireArray(journal.entries, 'entries');
  const counts = {};
  for (const entry of journal.entries) {
    const version = entry.version ?? 1;
    const key = `${entry.kind} v${version}`;
    counts[key] = (counts[key] ?? 0) + 1;
  }
  return {
    entries: journal.entries.length,
    byVersion: counts,
    needing: journal.entries.filter((entry) => needsMigration(entry)).length,
  };
}
