import { describe, it, expect } from 'vitest';
import {
  correctionFor,
  planReshoot,
  recordReshoot,
  reshootRate,
  reshootReasons,
  repeatedlyShot,
} from '../../src/pipeline/reshoot.js';
import { makeJournal, appendAll, verify } from '../../src/store/journal.js';
import { gradeFilm } from '../../src/pipeline/interpret.js';

const goodFilm = () => ({
  film: '1',
  identification: { job: 'C-4471', day: '2025-03-14', jointId: 'J-118', position: '1' },
  sourceKind: 'gamma',
  indicatorDensity: 2.4,
  areaDensities: [
    { at: 'A', density: 2.3 },
    { at: 'B', density: 2.5 },
  ],
  indicator: { identity: 20, hole: '2T' },
  penetratedThicknessIn: 0.56,
});

describe('correctionFor', () => {
  it('knows what to do about each check', () => {
    for (const check of [
      'identification',
      'density',
      'variation',
      'sensitivity',
      'backscatter',
    ]) {
      expect(() => correctionFor(check)).not.toThrow();
    }
  });

  it('says to lay the identification rather than change the exposure', () => {
    const correction = correctionFor('identification');
    expect(correction.exposureFactor).toBe(1);
    expect(correction.changes[0]).toMatch(/lay the missing identification/);
  });

  it('refuses a check it has no correction for', () => {
    expect(() => correctionFor('vibration')).toThrow(/no correction is known/);
  });

  it('hands back a copy of the changes', () => {
    const correction = correctionFor('backscatter');
    correction.changes.push('nonsense');
    expect(correctionFor('backscatter').changes).toHaveLength(1);
  });
});

describe('planReshoot', () => {
  it('needs nothing when the film was good', () => {
    const plan = planReshoot({ grade: gradeFilm(goodFilm()), minutes: 4 });
    expect(plan.needed).toBe(false);
    expect(plan.changes).toEqual([]);
  });

  it('lengthens the exposure after a light film', () => {
    const grade = gradeFilm({ ...goodFilm(), indicatorDensity: 1.6, areaDensities: [] });
    const plan = planReshoot({ grade, minutes: 4 });
    expect(plan.needed).toBe(true);
    expect(plan.exposureFactor).toBeGreaterThan(1);
    expect(plan.minutes).toBeGreaterThan(4);
    expect(plan.changes.join(' ')).toMatch(/lengthen the exposure/);
  });

  it('shortens it after a dark film', () => {
    const grade = gradeFilm({ ...goodFilm(), indicatorDensity: 4.4, areaDensities: [] });
    const plan = planReshoot({ grade, minutes: 4 });
    expect(plan.exposureFactor).toBeLessThan(1);
    expect(plan.changes.join(' ')).toMatch(/shorten the exposure/);
  });

  it('leaves the exposure alone when only the identification was missing', () => {
    const grade = gradeFilm({ ...goodFilm(), identification: { job: 'C-4471' } });
    const plan = planReshoot({ grade, minutes: 4 });
    expect(plan.exposureFactor).toBe(1);
    expect(plan.minutes).toBe(4);
  });

  it('asks for lead rather than time after a backscatter failure', () => {
    const grade = gradeFilm({
      ...goodFilm(),
      backscatter: { letterDensity: 1.8, backgroundDensity: 2.4 },
    });
    const plan = planReshoot({ grade, minutes: 4 });
    expect(plan.exposureFactor).toBe(1);
    expect(plan.changes.join(' ')).toMatch(/lead behind the cassette/);
  });

  it('asks for the right indicator after a sensitivity failure', () => {
    const grade = gradeFilm({ ...goodFilm(), indicator: { identity: 60, hole: '2T' } });
    const plan = planReshoot({ grade, minutes: 4 });
    expect(plan.changes.join(' ')).toMatch(/fit the indicator the table calls for/);
  });

  it('adds an exposure when one end of the film ran out of the band', () => {
    const grade = gradeFilm({
      ...goodFilm(),
      areaDensities: [
        { at: 'A', density: 2.4 },
        { at: 'B', density: 1.5 },
      ],
    });
    const plan = planReshoot({ grade, minutes: 4 });
    expect(plan.techniqueChange).toBe(false);
    expect(plan.changes.join(' ')).toMatch(/add an exposure/);
  });

  it('calls for a different technique when the section is simply too wide', () => {
    const grade = gradeFilm({
      ...goodFilm(),
      areaDensities: [
        { at: 'A', density: 1.4 },
        { at: 'B', density: 1.4 },
        { at: 'C', density: 1.4 },
      ],
    });
    const plan = planReshoot({ grade, minutes: 4 });
    expect(plan.techniqueChange).toBe(true);
    expect(plan.changes.join(' ')).toMatch(/change film or source/);
  });

  it('does not repeat a change that two failures both asked for', () => {
    const grade = gradeFilm({
      ...goodFilm(),
      identification: {},
      indicator: null,
    });
    const plan = planReshoot({ grade, minutes: 4 });
    expect(new Set(plan.changes).size).toBe(plan.changes.length);
  });

  it('works without being told the original time', () => {
    const grade = gradeFilm({ ...goodFilm(), indicator: null });
    expect(planReshoot({ grade }).minutes).toBeUndefined();
  });

  it('refuses something that is not a graded film', () => {
    expect(() => planReshoot({ grade: {}, minutes: 4 })).toThrow(/from a graded film/);
  });
});

describe('recordReshoot', () => {
  const grade = gradeFilm({ ...goodFilm(), indicatorDensity: 1.6, areaDensities: [] });
  const plan = planReshoot({ grade, minutes: 4 });

  it('writes a shot entry marked as a reshoot', () => {
    const journal = recordReshoot(makeJournal('C-4471'), {
      jointId: 'J-118',
      day: '2025-03-15',
      plan,
    });
    const last = journal.entries[0];
    expect(last.kind).toBe('shot');
    expect(last.payload.reshoot).toBe(true);
    expect(verify(journal).intact).toBe(true);
  });

  it('carries the changes into the record', () => {
    const journal = recordReshoot(makeJournal('C-4471'), {
      jointId: 'J-118',
      day: '2025-03-15',
      plan,
    });
    expect(journal.entries[0].payload.changes.length).toBeGreaterThan(0);
  });

  it('refuses a reshoot with no joint named', () => {
    expect(() =>
      recordReshoot(makeJournal('C-1'), { jointId: '', day: '2025-03-15', plan })
    ).toThrow(/names the joint/);
  });
});

describe('reshootRate and reasons', () => {
  const entry = (day, subject, payload = {}) => ({ kind: 'shot', day, subject, payload });
  const journal = appendAll(makeJournal('C-4471'), [
    entry('2025-03-10', 'J-1', { films: 3 }),
    entry('2025-03-11', 'J-1', { reshoot: true, changes: ['lengthen the exposure'] }),
    entry('2025-03-12', 'J-2', { films: 3 }),
    entry('2025-03-13', 'J-2', { reshoot: true, changes: ['lengthen the exposure'] }),
    entry('2025-03-14', 'J-3', { films: 3 }),
  ]);

  it('counts the reshoots against the shots', () => {
    const rate = reshootRate(journal);
    expect(rate.shots).toBe(5);
    expect(rate.reshoots).toBe(2);
    expect(rate.rate).toBeCloseTo(0.4, 4);
  });

  it('is zero on a journal with no shooting in it', () => {
    expect(reshootRate(makeJournal('C-0')).rate).toBe(0);
  });

  it('ranks the reasons, commonest first', () => {
    const reasons = reshootReasons(journal);
    expect(reasons[0]).toEqual({ change: 'lengthen the exposure', count: 2 });
  });

  it('finds nothing to rank when nothing was reshot', () => {
    expect(reshootReasons(makeJournal('C-0'))).toEqual([]);
  });

  it('names joints shot more times than they should be', () => {
    const thrice = appendAll(journal, [
      entry('2025-03-15', 'J-1', { reshoot: true, changes: [] }),
    ]);
    expect(repeatedlyShot(thrice, 2)).toEqual([{ jointId: 'J-1', shots: 3 }]);
  });

  it('names nobody when everything went twice or less', () => {
    expect(repeatedlyShot(journal, 2)).toEqual([]);
  });
});
