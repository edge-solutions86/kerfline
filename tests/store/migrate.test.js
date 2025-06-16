import { describe, it, expect } from 'vitest';
import {
  knownKinds,
  currentVersion,
  needsMigration,
  migrateEntry,
  migrateJournal,
  surveyJournal,
} from '../../src/store/migrate.js';

const old = (kind, payload, version = 1) => ({
  seq: 1,
  kind,
  day: '2023-05-10',
  subject: 'J-118',
  payload,
  version,
  checksum: 'abcd1234',
});

describe('the version table', () => {
  it('knows every entry kind', () => {
    expect(knownKinds()).toContain('joint');
    expect(knownKinds()).toContain('verdict');
  });

  it('reports the current version of each', () => {
    expect(currentVersion('joint')).toBe(2);
    expect(currentVersion('note')).toBe(1);
  });

  it('refuses a kind it does not carry', () => {
    expect(() => currentVersion('invoice')).toThrow(/no record kind invoice/);
  });

  it('spots an entry that needs bringing forward', () => {
    expect(needsMigration(old('joint', {}))).toBe(true);
    expect(needsMigration(old('joint', {}, 2))).toBe(false);
    expect(needsMigration(old('note', {}))).toBe(false);
  });

  it('treats an entry with no version as version one', () => {
    expect(needsMigration({ kind: 'joint', payload: {} })).toBe(true);
  });
});

describe('migrateEntry', () => {
  it('renames the old thickness field on a joint', () => {
    const migrated = migrateEntry(old('joint', { thickness: 0.28, id: 'J-118' }));
    expect(migrated.payload.wallIn).toBe(0.28);
    expect(migrated.payload.thickness).toBeUndefined();
    expect(migrated.payload.id).toBe('J-118');
  });

  it('leaves a joint that never had the old field alone', () => {
    const migrated = migrateEntry(old('joint', { wallIn: 0.28 }));
    expect(migrated.payload.wallIn).toBe(0.28);
  });

  it('fills in the missing ready flag on an old shot', () => {
    expect(migrateEntry(old('shot', { films: 3 })).payload.ready).toBe(true);
  });

  it('does not overwrite a field the record already had', () => {
    expect(migrateEntry(old('shot', { films: 3, ready: false })).payload.ready).toBe(false);
  });

  it('marks a verdict that never recorded a clause rather than inventing one', () => {
    const migrated = migrateEntry(old('verdict', { disposition: 'accept' }));
    expect(migrated.payload.clause).toBe('not recorded');
    expect(migrated.payload.reasons).toEqual([]);
  });

  it('lands on the current version and says where it came from', () => {
    const migrated = migrateEntry(old('joint', { thickness: 0.28 }));
    expect(migrated.version).toBe(2);
    expect(migrated.migratedFrom).toEqual([1]);
  });

  it('leaves a current entry untouched and says so', () => {
    const entry = old('joint', { wallIn: 0.28 }, 2);
    const migrated = migrateEntry(entry);
    expect(migrated.payload).toEqual({ wallIn: 0.28 });
    expect(migrated.migratedFrom).toBe(null);
  });

  it('never changes the checksum, which attests to what was written', () => {
    const entry = old('joint', { thickness: 0.28 });
    expect(migrateEntry(entry).checksum).toBe(entry.checksum);
  });

  it('leaves the entry it was given alone', () => {
    const entry = old('joint', { thickness: 0.28 });
    migrateEntry(entry);
    expect(entry.payload.thickness).toBe(0.28);
  });

  it('refuses a record from a newer version than the tool knows', () => {
    expect(() => migrateEntry(old('joint', {}, 5))).toThrow(/version 5 and the tool knows 2/);
  });

  it('refuses when no migration exists for a step', () => {
    expect(() => migrateEntry(old('interpretation', {}, 0))).toThrow(
      /no migration from version/
    );
  });

  it('refuses something that is not a record', () => {
    expect(() => migrateEntry(null)).toThrow(/must be an object/);
  });
});

describe('migrateJournal', () => {
  const journal = {
    name: 'C-4471',
    entries: [
      old('joint', { thickness: 0.28 }),
      old('verdict', { disposition: 'accept' }),
      old('note', { text: 'reshot' }),
    ],
  };

  it('brings every entry forward', () => {
    const migrated = migrateJournal(journal);
    expect(migrated.entries[0].payload.wallIn).toBe(0.28);
    expect(migrated.entries[1].payload.clause).toBe('not recorded');
  });

  it('counts what it moved', () => {
    expect(migrateJournal(journal).migrated).toBe(2);
  });

  it('leaves an already current journal alone', () => {
    const current = { name: 'C-1', entries: [old('note', { text: 'x' })] };
    expect(migrateJournal(current).migrated).toBe(0);
  });

  it('keeps the journal name', () => {
    expect(migrateJournal(journal).name).toBe('C-4471');
  });

  it('refuses something that is not a journal', () => {
    expect(() => migrateJournal({})).toThrow(/not a journal/);
  });
});

describe('surveyJournal', () => {
  it('counts the entries by kind and version without changing anything', () => {
    const journal = {
      name: 'C-4471',
      entries: [old('joint', {}), old('joint', {}, 2), old('note', {})],
    };
    const survey = surveyJournal(journal);
    expect(survey.entries).toBe(3);
    expect(survey.byVersion['joint v1']).toBe(1);
    expect(survey.byVersion['joint v2']).toBe(1);
    expect(survey.byVersion['note v1']).toBe(1);
  });

  it('counts how many need bringing forward', () => {
    const journal = { name: 'C-1', entries: [old('joint', {}), old('note', {})] };
    expect(surveyJournal(journal).needing).toBe(1);
  });

  it('surveys an empty journal', () => {
    expect(surveyJournal({ name: 'C-0', entries: [] })).toEqual({
      entries: 0,
      byVersion: {},
      needing: 0,
    });
  });
});
