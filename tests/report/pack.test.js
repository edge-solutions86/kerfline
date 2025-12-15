import { describe, it, expect } from 'vitest';
import {
  jointRows,
  buildPack,
  packContents,
  renderPack,
  packExport,
  issuable,
} from '../../src/report/pack.js';
import { makeRegister, addJoints } from '../../src/joint/register.js';
import { makeJournal, appendAll } from '../../src/store/journal.js';
import { makeReview } from '../../src/report/filmreview.js';
import { verifyChecksum } from '../../src/report/export.js';

const entry = (kind, day, subject, payload = {}) => ({ kind, day, subject, payload });

const register = addJoints(makeRegister('C-4471'), [
  { id: 'J-1', welder: 'A17', spool: 'SP-1', nominalSize: 6, schedule: 40 },
  { id: 'J-2', welder: 'A17', spool: 'SP-1', nominalSize: 6, schedule: 40 },
]);

const finished = appendAll(makeJournal('C-4471'), [
  entry('shot', '2025-03-10', 'J-1', { films: 3 }),
  entry('shot', '2025-03-10', 'J-2', { films: 3 }),
  entry('verdict', '2025-03-11', 'J-1', { disposition: 'accept', clause: '9.3' }),
  entry('verdict', '2025-03-11', 'J-2', { disposition: 'accept', clause: '9.3' }),
]);

const pack = buildPack({
  job: 'C-4471',
  register,
  journal: finished,
  day: '2025-03-20',
  level: 'ten',
  reviews: [
    makeReview({
      jointId: 'J-1',
      day: '2025-03-18',
      reviewer: 'K. Osei',
      firstCall: 'accept',
      secondCall: 'accept',
    }),
  ],
});

describe('jointRows', () => {
  it('reads the state of every registered joint off the journal', () => {
    const rows = jointRows(register, finished);
    expect(rows).toHaveLength(2);
    expect(rows.every((row) => row.shot)).toBe(true);
    expect(rows.every((row) => row.disposition === 'accept')).toBe(true);
  });

  it('leaves a joint that was never shot unjudged', () => {
    const partial = appendAll(makeJournal('C-4471'), [entry('shot', '2025-03-10', 'J-1')]);
    const rows = jointRows(register, partial);
    expect(rows.find((row) => row.id === 'J-2').shot).toBe(false);
    expect(rows.find((row) => row.id === 'J-2').disposition).toBe(null);
  });
});

describe('buildPack', () => {
  it('builds every page from the same register and journal', () => {
    expect(pack.register.rows).toHaveLength(2);
    expect(pack.summary.counts.joints).toBe(2);
    expect(pack.coverage.overall.totalJoints).toBe(2);
    expect(pack.audit.checked.joints).toBe(2);
  });

  it('cannot have two pages disagreeing, because there is one reading behind them', () => {
    expect(pack.summary.counts.shot).toBe(pack.coverage.overall.shotJoints);
    expect(pack.summary.coverage.achievedFraction).toBeCloseTo(
      pack.coverage.overall.achievedFraction,
      6
    );
  });

  it('carries the job and the date onto every page', () => {
    expect(pack.job).toBe('C-4471');
    expect(pack.day).toBe('2025-03-20');
  });

  it('refuses a pack with no job or no date', () => {
    expect(() =>
      buildPack({ job: ' ', register, journal: finished, day: '2025-03-20' })
    ).toThrow(/belongs to a job/);
    expect(() =>
      buildPack({ job: 'C-1', register, journal: finished, day: '2025-02-30' })
    ).toThrow(/not a real calendar day/);
  });
});

describe('packContents', () => {
  it('lists the pages in the order they go in the folder', () => {
    const contents = packContents(pack);
    expect(contents).toHaveLength(6);
    expect(contents[0].title).toBe('Radiography summary');
    expect(contents.map((entry_) => entry_.page)).toEqual([1, 2, 3, 4, 5, 6]);
  });

  it('says how much is on each page, so an empty one shows in the contents', () => {
    const contents = packContents(pack);
    expect(contents.find((row) => row.title === 'Joint register').items).toBe(2);
    expect(contents.find((row) => row.title === 'Second reading').items).toBe(1);
    expect(contents.find((row) => row.title === 'Open nonconformances').items).toBe(0);
  });
});

describe('renderPack', () => {
  const text = renderPack(pack);

  it('prints every page', () => {
    expect(text).toContain('RADIOGRAPHY SUMMARY');
    expect(text).toContain('JOINT REGISTER');
    expect(text).toContain('RADIOGRAPHY COVERAGE');
    expect(text).toContain('SECOND READING');
    expect(text).toContain('OPEN NONCONFORMANCES');
    expect(text).toContain('AUDIT READINESS');
  });

  it('separates the pages', () => {
    expect(text.split(/_{20,}/)).toHaveLength(packContents(pack).length);
  });

  it('names the job throughout', () => {
    expect(text.match(/C-4471/g).length).toBeGreaterThanOrEqual(5);
  });

  it('keeps every line inside the widest page it prints', () => {
    for (const line of text.split('\n')) {
      expect(line.length).toBeLessThanOrEqual(96);
    }
  });
});

describe('packExport', () => {
  it('exports a register the client can check', () => {
    const csv = packExport(pack);
    expect(verifyChecksum(csv).intact).toBe(true);
    expect(csv).toContain('J-1');
  });
});

describe('issuable', () => {
  it('is issuable when the job is finished and clean', () => {
    const check = issuable(pack);
    expect(check.issuable).toBe(true);
    expect(check.reasons).toEqual([]);
  });

  it('is not issuable with a film shot and never read', () => {
    const partial = appendAll(makeJournal('C-4471'), [
      entry('shot', '2025-03-10', 'J-1'),
      entry('shot', '2025-03-10', 'J-2'),
      entry('verdict', '2025-03-11', 'J-1', { disposition: 'accept' }),
    ]);
    const other = buildPack({
      job: 'C-4471',
      register,
      journal: partial,
      day: '2025-03-20',
      level: 'ten',
    });
    const check = issuable(other);
    expect(check.issuable).toBe(false);
    expect(check.reasons.join(' ')).toMatch(/shot and not read/);
  });

  it('is not issuable when the record itself cannot be relied on', () => {
    const tampered = {
      name: finished.name,
      entries: finished.entries.map((e) => (e.seq === 2 ? { ...e, subject: 'J-9' } : e)),
    };
    const other = buildPack({
      job: 'C-4471',
      register,
      journal: tampered,
      day: '2025-03-20',
      level: 'ten',
    });
    const check = issuable(other);
    expect(check.issuable).toBe(false);
    expect(check.reasons.join(' ')).toMatch(/breaks at entry/);
  });

  it('is not issuable when the coverage is short', () => {
    const barely = appendAll(makeJournal('C-4471'), [
      entry('shot', '2025-03-10', 'J-1'),
      entry('verdict', '2025-03-11', 'J-1', { disposition: 'accept' }),
    ]);
    const other = buildPack({
      job: 'C-4471',
      register,
      journal: barely,
      day: '2025-03-20',
      level: 'full',
    });
    expect(issuable(other).reasons.join(' ')).toMatch(/coverage is short/);
  });

  it('gives every reason at once rather than the first', () => {
    const barely = appendAll(makeJournal('C-4471'), [entry('shot', '2025-03-10', 'J-1')]);
    const other = buildPack({
      job: 'C-4471',
      register,
      journal: barely,
      day: '2025-03-20',
      level: 'full',
    });
    expect(issuable(other).reasons.length).toBeGreaterThanOrEqual(2);
  });
});
