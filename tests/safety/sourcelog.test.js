import { describe, it, expect } from 'vitest';
import {
  leakLimitMicroCuries,
  leakIntervalMonths,
  makeSource,
  activityOn,
  makeUse,
  utilisation,
  whoHadIt,
  makeLeakTest,
  leakTestCurrent,
  checkOut,
  leakTestsDueBy,
} from '../../src/safety/sourcelog.js';

const source = makeSource({
  serial: 'IR-7741',
  isotopeId: 'IR192',
  assayCi: 80,
  assayDay: '2025-01-06',
});

describe('the leak test rules', () => {
  it('sets the limit at five nanocuries and the interval at six months', () => {
    expect(leakLimitMicroCuries()).toBe(0.005);
    expect(leakIntervalMonths()).toBe(6);
  });
});

describe('makeSource and activityOn', () => {
  it('records the source off its certificate', () => {
    expect(source.serial).toBe('IR-7741');
    expect(source.isotope).toBe('IR192');
    expect(source.assayCi).toBe(80);
  });

  it('reads the assay activity on the assay day', () => {
    expect(activityOn(source, '2025-01-06')).toBe(80);
  });

  it('decays it forward', () => {
    expect(activityOn(source, '2025-03-21')).toBeCloseTo(40, 0);
  });

  it('back decays to before the assay', () => {
    expect(activityOn(source, '2024-10-24')).toBeGreaterThan(80);
  });

  it('refuses a source with no serial', () => {
    expect(() =>
      makeSource({ serial: '  ', isotopeId: 'IR192', assayCi: 80, assayDay: '2025-01-06' })
    ).toThrow(/identified by its serial/);
  });

  it('refuses an isotope the shop is not licensed for', () => {
    expect(() =>
      makeSource({ serial: 'X-1', isotopeId: 'CS137', assayCi: 80, assayDay: '2025-01-06' })
    ).toThrow(/no isotope CS137/);
  });
});

describe('the utilisation log', () => {
  const uses = [
    makeUse({
      serial: 'IR-7741',
      day: '2025-02-03',
      user: 'R. Ibarra',
      location: 'Unit 4',
      minutes: 45,
      exposures: 12,
    }),
    makeUse({
      serial: 'IR-7741',
      day: '2025-02-04',
      user: 'D. Whyte',
      location: 'Unit 4',
      minutes: 30,
      exposures: 8,
    }),
    makeUse({
      serial: 'CO-2210',
      day: '2025-02-04',
      user: 'R. Ibarra',
      location: 'Shop',
      minutes: 15,
      exposures: 2,
    }),
  ];

  it('totals one source over a period', () => {
    const summary = utilisation(uses, {
      serial: 'IR-7741',
      from: '2025-02-01',
      to: '2025-02-28',
    });
    expect(summary.entries).toBe(2);
    expect(summary.minutes).toBeCloseTo(75, 1);
    expect(summary.exposures).toBe(20);
  });

  it('leaves other sources out of it', () => {
    const summary = utilisation(uses, {
      serial: 'CO-2210',
      from: '2025-02-01',
      to: '2025-02-28',
    });
    expect(summary.exposures).toBe(2);
  });

  it('lists who had it and on what days', () => {
    const summary = utilisation(uses, {
      serial: 'IR-7741',
      from: '2025-02-01',
      to: '2025-02-28',
    });
    expect(summary.users).toEqual(['D. Whyte', 'R. Ibarra']);
    expect(summary.days).toEqual(['2025-02-03', '2025-02-04']);
  });

  it('is empty over a period with nothing in it', () => {
    const summary = utilisation(uses, {
      serial: 'IR-7741',
      from: '2025-06-01',
      to: '2025-06-30',
    });
    expect(summary.entries).toBe(0);
    expect(summary.minutes).toBe(0);
  });

  it('answers who had a source on a day', () => {
    const holders = whoHadIt(uses, { serial: 'IR-7741', day: '2025-02-03' });
    expect(holders).toHaveLength(1);
    expect(holders[0].user).toBe('R. Ibarra');
    expect(holders[0].location).toBe('Unit 4');
  });

  it('answers with nothing when the source was in the store', () => {
    expect(whoHadIt(uses, { serial: 'IR-7741', day: '2025-02-05' })).toEqual([]);
  });

  it('refuses a log entry with nobody on it', () => {
    expect(() =>
      makeUse({ serial: 'IR-7741', day: '2025-02-03', user: '', minutes: 1, exposures: 1 })
    ).toThrow(/names who had it/);
  });
});

describe('makeLeakTest', () => {
  it('passes a clean source and sets the next due date', () => {
    const test = makeLeakTest({ serial: 'IR-7741', day: '2025-01-10', microCuries: 0.0001 });
    expect(test.pass).toBe(true);
    expect(test.dueBy).toBe('2025-07-10');
    expect(test.action).toBe(null);
  });

  it('fails a source over the limit and says what happens', () => {
    const test = makeLeakTest({ serial: 'IR-7741', day: '2025-01-10', microCuries: 0.006 });
    expect(test.pass).toBe(false);
    expect(test.action).toMatch(/remove the source from service/);
  });

  it('fails at exactly the limit rather than passing on the boundary', () => {
    const test = makeLeakTest({ serial: 'IR-7741', day: '2025-01-10', microCuries: 0.005 });
    expect(test.pass).toBe(false);
  });

  it('refuses a negative reading', () => {
    expect(() =>
      makeLeakTest({ serial: 'IR-7741', day: '2025-01-10', microCuries: -1 })
    ).toThrow(/not be below 0/);
  });
});

describe('leakTestCurrent', () => {
  const good = makeLeakTest({ serial: 'IR-7741', day: '2025-01-10', microCuries: 0.0001 });
  const bad = makeLeakTest({ serial: 'IR-7741', day: '2025-01-10', microCuries: 0.02 });

  it('is current inside the six months', () => {
    const check = leakTestCurrent([good], { serial: 'IR-7741', day: '2025-04-01' });
    expect(check.current).toBe(true);
    expect(check.daysLeft).toBeGreaterThan(0);
  });

  it('has run out after the six months', () => {
    const check = leakTestCurrent([good], { serial: 'IR-7741', day: '2025-07-11' });
    expect(check.current).toBe(false);
    expect(check.reason).toMatch(/run out/);
  });

  it('is never current after a failed test', () => {
    const check = leakTestCurrent([bad], { serial: 'IR-7741', day: '2025-01-11' });
    expect(check.current).toBe(false);
    expect(check.reason).toMatch(/failed/);
  });

  it('says so when there is no test at all', () => {
    const check = leakTestCurrent([], { serial: 'IR-7741', day: '2025-01-11' });
    expect(check.current).toBe(false);
    expect(check.reason).toMatch(/no leak test on file/);
  });

  it('ignores a test dated after the day being asked about', () => {
    const check = leakTestCurrent([good], { serial: 'IR-7741', day: '2025-01-05' });
    expect(check.current).toBe(false);
  });

  it('takes the most recent test when there are several', () => {
    const later = makeLeakTest({ serial: 'IR-7741', day: '2025-06-01', microCuries: 0.0001 });
    const check = leakTestCurrent([good, later], { serial: 'IR-7741', day: '2025-08-01' });
    expect(check.current).toBe(true);
    expect(check.latest.day).toBe('2025-06-01');
  });
});

describe('checkOut', () => {
  const good = makeLeakTest({ serial: 'IR-7741', day: '2025-01-10', microCuries: 0.0001 });

  it('books a source out with its activity for the day', () => {
    const out = checkOut({ source, tests: [good], day: '2025-02-03' });
    expect(out.serial).toBe('IR-7741');
    expect(out.activityCi).toBeLessThan(80);
  });

  it('refuses a source whose leak test has run out', () => {
    expect(() => checkOut({ source, tests: [good], day: '2025-08-01' })).toThrow(/run out/);
  });

  it('refuses a source that has never been tested', () => {
    expect(() => checkOut({ source, tests: [], day: '2025-02-03' })).toThrow(/no leak test/);
  });
});

describe('leakTestsDueBy', () => {
  const good = makeLeakTest({ serial: 'IR-7741', day: '2025-01-10', microCuries: 0.0001 });

  it('finds nothing when the test has a long time left', () => {
    const due = leakTestsDueBy([source], [good], { day: '2025-02-01', withinDays: 30 });
    expect(due).toEqual([]);
  });

  it('finds a source coming up for renewal', () => {
    const due = leakTestsDueBy([source], [good], { day: '2025-06-20', withinDays: 30 });
    expect(due).toHaveLength(1);
    expect(due[0].dueBy).toBe('2025-07-10');
  });

  it('finds a source with no test at all', () => {
    const due = leakTestsDueBy([source], [], { day: '2025-02-01', withinDays: 30 });
    expect(due[0].reason).toMatch(/no leak test/);
  });
});
