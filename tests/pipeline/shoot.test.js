import { describe, it, expect } from 'vitest';
import { planShot, recordShot, shoot, filmLabels } from '../../src/pipeline/shoot.js';
import { makeJoint } from '../../src/joint/spec.js';
import { makeSource, makeLeakTest } from '../../src/safety/sourcelog.js';
import { makeJournal } from '../../src/store/journal.js';
import { verify } from '../../src/store/journal.js';

const joint = makeJoint({ id: 'J-118', nominalSize: 6, schedule: 40 });

const source = makeSource({
  serial: 'IR-7741',
  isotopeId: 'IR192',
  assayCi: 80,
  assayDay: '2025-03-01',
});

const leakTests = [makeLeakTest({ serial: 'IR-7741', day: '2025-03-01', microCuries: 0.0001 })];

const base = {
  job: 'C-4471',
  joint,
  day: '2025-03-14',
  source,
  leakTests,
  sourceToObjectIn: 48,
  objectToFilmIn: 6.625,
};

describe('planShot', () => {
  const plan = planShot(base);

  it('books the source out and decays it to the day', () => {
    expect(plan.source.serial).toBe('IR-7741');
    expect(plan.source.activityCi).toBeLessThan(80);
    expect(plan.source.activityCi).toBeGreaterThan(70);
  });

  it('builds the technique off the source it booked', () => {
    expect(plan.technique.exposure.isotope).toBe('IR192');
    expect(plan.technique.viewing.technique).toBe('DWSI');
  });

  it('plans a film for each shot round the joint', () => {
    expect(plan.shots.length).toBeGreaterThan(1);
    expect(plan.shots[0].clock).toBe('12:00');
  });

  it('puts the lead marks on every shot', () => {
    for (const shot of plan.shots) {
      expect(shot.marks).toContain('identification');
      expect(shot.marks.join(' ')).toMatch(/location \d+/);
    }
  });

  it('calls for the backscatter letter only when nothing is behind the film', () => {
    const leaded = planShot(base);
    expect(leaded.shots[0].marks).not.toContain('B');
    const bare = planShot({ ...base, screens: 'NONE' });
    expect(bare.shots[0].marks).toContain('B');
  });

  it('marks the film side indicator when the shot calls for it', () => {
    const filmSide = planShot({ ...base, side: 'film' });
    expect(filmSide.shots[0].marks).toContain('F');
  });

  it('is ready when everything resolved', () => {
    expect(plan.ready).toBe(true);
    expect(plan.problems).toEqual([]);
  });

  it('refuses a source whose leak test has run out', () => {
    expect(() => planShot({ ...base, day: '2025-10-01' })).toThrow(/run out/);
  });

  it('refuses a source that has never been leak tested', () => {
    expect(() => planShot({ ...base, leakTests: [] })).toThrow(/no leak test/);
  });

  it('refuses a shot with no source', () => {
    expect(() => planShot({ ...base, source: null })).toThrow(/needs a source/);
  });

  it('refuses a date that is not a real day', () => {
    expect(() => planShot({ ...base, day: '2025-02-30' })).toThrow(/not a real calendar day/);
  });

  it('is not ready when the technique could not be worked out', () => {
    const thick = makeJoint({ id: 'J-9', odIn: 24, wallIn: 3 });
    const plan_ = planShot({ ...base, joint: thick });
    expect(plan_.ready).toBe(false);
    expect(plan_.problems.length).toBeGreaterThan(0);
  });
});

describe('recordShot', () => {
  it('writes an entry against the joint', () => {
    const journal = recordShot(makeJournal('C-4471'), planShot(base));
    expect(journal.entries).toHaveLength(1);
    expect(journal.entries[0].kind).toBe('shot');
    expect(journal.entries[0].subject).toBe('J-118');
  });

  it('records what was actually used', () => {
    const journal = recordShot(makeJournal('C-4471'), planShot(base));
    const payload = journal.entries[0].payload;
    expect(payload.source).toBe('IR-7741');
    expect(payload.technique).toBe('DWSI');
    expect(payload.films).toBeGreaterThan(1);
    expect(payload.ready).toBe(true);
  });

  it('records a plan that was not ready, with its problems', () => {
    const thick = makeJoint({ id: 'J-9', odIn: 24, wallIn: 3 });
    const journal = recordShot(makeJournal('C-4471'), planShot({ ...base, joint: thick }));
    expect(journal.entries[0].payload.ready).toBe(false);
    expect(journal.entries[0].payload.problems.length).toBeGreaterThan(0);
  });

  it('keeps the journal chain intact', () => {
    const journal = recordShot(makeJournal('C-4471'), planShot(base));
    expect(verify(journal).intact).toBe(true);
  });

  it('refuses a plan with no joint on it', () => {
    expect(() => recordShot(makeJournal('C-4471'), {})).toThrow(/needs a plan with a joint/);
  });
});

describe('shoot', () => {
  it('plans and records in one step', () => {
    const { journal, plan } = shoot(makeJournal('C-4471'), base);
    expect(plan.ready).toBe(true);
    expect(journal.entries).toHaveLength(1);
  });

  it('chains onto an existing journal', () => {
    const first = shoot(makeJournal('C-4471'), base);
    const second = shoot(first.journal, { ...base, day: '2025-03-15' });
    expect(second.journal.entries).toHaveLength(2);
    expect(verify(second.journal).intact).toBe(true);
  });
});

describe('filmLabels', () => {
  it('labels a jacket per shot', () => {
    const plan = planShot(base);
    const labels = filmLabels(plan);
    expect(labels).toHaveLength(plan.shots.length);
    expect(labels[0]).toBe('J-118-1');
  });

  it('gives every film a different label', () => {
    const labels = filmLabels(planShot(base));
    expect(new Set(labels).size).toBe(labels.length);
  });
});
