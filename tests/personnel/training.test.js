import { describe, it, expect } from 'vitest';
import {
  methods,
  refreshYearsFor,
  makeTraining,
  hoursToward,
  hoursByMethod,
  readyFor,
  lapsed,
  currentToWork,
  takenBetween,
} from '../../src/personnel/training.js';

const course = (method, hours, day, name = 'course') =>
  makeTraining({ person: 'R. Ibarra', method, course: name, hours, day });

const records = [
  course('RT', 40, '2023-02-06', 'radiography level I'),
  course('RT', 40, '2023-09-11', 'radiography level II'),
  course('UT', 40, '2024-01-15', 'ultrasonics level I'),
  course('SAFETY', 8, '2025-01-06', 'radiation safety refresher'),
];

describe('the methods', () => {
  it('names the ones the shop trains in', () => {
    expect(methods()).toContain('RT');
    expect(methods()).toContain('SAFETY');
  });

  it('says which need refreshing', () => {
    expect(refreshYearsFor('SAFETY')).toBe(1);
    expect(refreshYearsFor('RT')).toBe(null);
  });

  it('refuses a method nobody trains in', () => {
    expect(() => refreshYearsFor('ET')).toThrow(/method must be one of/);
  });
});

describe('makeTraining', () => {
  it('records a course', () => {
    const record = course('RT', 40, '2023-02-06');
    expect(record.hours).toBe(40);
    expect(record.method).toBe('RT');
  });

  it('sets an expiry only on training that needs refreshing', () => {
    expect(course('SAFETY', 8, '2025-01-06').expiresOn).toBe('2026-01-06');
    expect(course('RT', 40, '2023-02-06').expiresOn).toBe(null);
  });

  it('refuses a record with nobody or no course on it', () => {
    expect(() =>
      makeTraining({ person: ' ', method: 'RT', course: 'x', hours: 1, day: '2025-01-06' })
    ).toThrow(/names who attended/);
    expect(() =>
      makeTraining({ person: 'x', method: 'RT', course: '', hours: 1, day: '2025-01-06' })
    ).toThrow(/names the course/);
  });

  it('refuses negative hours', () => {
    expect(() => course('RT', -1, '2025-01-06')).toThrow(/not be below 0/);
  });
});

describe('hoursToward', () => {
  it('adds up one method only', () => {
    expect(hoursToward(records, 'RT')).toBe(80);
    expect(hoursToward(records, 'UT')).toBe(40);
  });

  it('does not let another method count towards it', () => {
    expect(hoursToward(records, 'MT')).toBe(0);
  });

  it('splits the hours by method', () => {
    const split = hoursByMethod(records);
    expect(split.RT).toBe(80);
    expect(split.SAFETY).toBe(8);
    expect(split.PT).toBe(0);
  });
});

describe('readyFor', () => {
  it('is ready when both the training and the experience reach', () => {
    const check = readyFor(records, { level: 'II', experienceHours: 900 });
    expect(check.ready).toBe(true);
    expect(check.trainingShort).toBe(0);
  });

  it('is short on experience even with plenty of training', () => {
    const check = readyFor(records, { level: 'II', experienceHours: 100 });
    expect(check.ready).toBe(false);
    expect(check.experienceShort).toBe(530);
    expect(check.trainingShort).toBe(0);
  });

  it('is short on training when the hours are in another method', () => {
    const check = readyFor(records, { method: 'MT', level: 'I', experienceHours: 900 });
    expect(check.ready).toBe(false);
    expect(check.trainingShort).toBe(40);
  });

  it('refuses a level nobody has', () => {
    expect(() => readyFor(records, { level: 'IV', experienceHours: 0 })).toThrow(
      /certification level/
    );
  });
});

describe('lapsed and currentToWork', () => {
  it('is current inside the year', () => {
    const check = currentToWork(records, '2025-06-01');
    expect(check.current).toBe(true);
    expect(check.daysLeft).toBeGreaterThan(0);
  });

  it('has lapsed a year and a day later', () => {
    const check = currentToWork(records, '2026-01-07');
    expect(check.current).toBe(false);
    expect(check.reason).toMatch(/has lapsed/);
  });

  it('is not current with no safety training at all', () => {
    const check = currentToWork([course('RT', 40, '2023-02-06')], '2025-06-01');
    expect(check.current).toBe(false);
    expect(check.reason).toMatch(/no radiation safety training/);
  });

  it('takes the most recent safety course', () => {
    const refreshed = [...records, course('SAFETY', 8, '2025-11-04')];
    expect(currentToWork(refreshed, '2026-06-01').current).toBe(true);
  });

  it('ignores a safety course taken after the day asked about', () => {
    const later = [course('SAFETY', 8, '2026-01-06')];
    expect(currentToWork(later, '2025-06-01').current).toBe(false);
  });

  it('lists the lapsed records', () => {
    expect(lapsed(records, '2026-06-01')).toHaveLength(1);
    expect(lapsed(records, '2025-06-01')).toEqual([]);
  });

  it('never calls training with no expiry lapsed', () => {
    const rt = [course('RT', 40, '2015-01-01')];
    expect(lapsed(rt, '2030-01-01')).toEqual([]);
  });
});

describe('takenBetween', () => {
  it('finds the courses inside a window', () => {
    const found = takenBetween(records, { from: '2023-01-01', to: '2023-12-31' });
    expect(found).toHaveLength(2);
  });

  it('includes both ends of the window', () => {
    expect(takenBetween(records, { from: '2023-02-06', to: '2023-02-06' })).toHaveLength(1);
  });

  it('finds nothing in a quiet year', () => {
    expect(takenBetween(records, { from: '2022-01-01', to: '2022-12-31' })).toEqual([]);
  });
});
