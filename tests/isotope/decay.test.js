import { describe, it, expect } from 'vitest';
import {
  decayConstant,
  decayFactor,
  halfLivesElapsed,
  activityAfter,
  activityOf,
  daysToActivity,
  usableDays,
  decayTable,
  exposureMultiplier,
} from '../../src/isotope/decay.js';

describe('decayConstant', () => {
  it('is ln two over the half life', () => {
    expect(decayConstant(73.83)).toBeCloseTo(Math.LN2 / 73.83, 12);
  });

  it('refuses a half life that is not positive', () => {
    expect(() => decayConstant(0)).toThrow(/greater than zero/);
    expect(() => decayConstant(-1)).toThrow(/greater than zero/);
    expect(() => decayConstant('73.83')).toThrow(/finite number/);
  });
});

describe('decayFactor', () => {
  it('halves over one half life', () => {
    expect(decayFactor(73.83, 73.83)).toBeCloseTo(0.5, 12);
    expect(decayFactor(73.83, 147.66)).toBeCloseTo(0.25, 12);
  });

  it('is one at the assay', () => {
    expect(decayFactor(73.83, 0)).toBe(1);
  });

  it('goes above one when back decayed', () => {
    expect(decayFactor(73.83, -73.83)).toBeCloseTo(2, 12);
  });

  it('counts half lives', () => {
    expect(halfLivesElapsed(73.83, 147.66)).toBeCloseTo(2, 12);
  });
});

describe('activityAfter', () => {
  it('takes a hundred curie source down by half', () => {
    expect(activityAfter(100, 73.83, 73.83)).toBeCloseTo(50, 10);
  });

  it('matches a thirty day step on iridium', () => {
    expect(activityAfter(85, 73.83, 30)).toBeCloseTo(64.14, 2);
  });

  it('barely moves cobalt over a month', () => {
    expect(activityAfter(20, 1925.3, 30)).toBeCloseTo(19.79, 2);
  });

  it('refuses an assay that is not positive', () => {
    expect(() => activityAfter(0, 73.83, 10)).toThrow(/greater than zero/);
    expect(() => activityAfter(-5, 73.83, 10)).toThrow(/greater than zero/);
  });

  it('refuses an elapsed span that is not a number', () => {
    expect(() => activityAfter(100, 73.83, 'thirty')).toThrow(/finite number/);
  });
});

describe('activityOf', () => {
  it('takes the half life from the catalog', () => {
    expect(activityOf('IR192', 100, 73.83)).toBeCloseTo(50, 10);
    expect(activityOf('se-75', 40, 119.78)).toBeCloseTo(20, 10);
  });

  it('refuses an isotope the shop is not licensed for', () => {
    expect(() => activityOf('CS137', 100, 10)).toThrow(/no isotope CS137/);
  });
});

describe('daysToActivity', () => {
  it('reaches half in one half life', () => {
    expect(daysToActivity(100, 73.83, 50)).toBeCloseTo(73.83, 9);
  });

  it('reaches a quarter in two', () => {
    expect(daysToActivity(100, 73.83, 25)).toBeCloseTo(147.66, 9);
  });

  it('is zero when the target is the assay', () => {
    expect(daysToActivity(100, 73.83, 100)).toBeCloseTo(0, 12);
  });

  it('goes negative when the target is above the assay', () => {
    expect(daysToActivity(50, 73.83, 100)).toBeCloseTo(-73.83, 9);
  });

  it('refuses a target that is not positive', () => {
    expect(() => daysToActivity(100, 73.83, 0)).toThrow(/greater than zero/);
  });
});

describe('usableDays', () => {
  it('rounds down to a whole day the scheduler can book', () => {
    expect(usableDays('IR192', 100, 50)).toBe(73);
  });

  it('is zero when the source is already below the floor', () => {
    expect(usableDays('IR192', 20, 30)).toBe(0);
  });

  it('gives cobalt years rather than months', () => {
    expect(usableDays('CO60', 30, 15)).toBe(1925);
  });
});

describe('decayTable', () => {
  it('walks the offsets given', () => {
    const rows = decayTable('IR192', 100, [0, 73.83]);
    expect(rows).toEqual([
      { days: 0, curies: 100, fraction: 1 },
      { days: 73.83, curies: 50, fraction: 0.5 },
    ]);
  });

  it('rounds to a stated number of places', () => {
    const rows = decayTable('IR192', 100, [10], { decimals: 4 });
    expect(rows[0].curies).toBeCloseTo(91.0, 1);
    expect(String(rows[0].curies).split('.')[1].length).toBeLessThanOrEqual(4);
  });

  it('refuses an empty offset list', () => {
    expect(() => decayTable('IR192', 100, [])).toThrow(/at least one day offset/);
    expect(() => decayTable('IR192', 100, null)).toThrow(/at least one day offset/);
  });

  it('refuses an offset that is not a number', () => {
    expect(() => decayTable('IR192', 100, [0, 'ten'])).toThrow(/finite number/);
  });
});

describe('exposureMultiplier', () => {
  it('doubles the time after one half life', () => {
    expect(exposureMultiplier(73.83, 73.83)).toBeCloseTo(2, 10);
  });

  it('is one at the assay', () => {
    expect(exposureMultiplier(73.83, 0)).toBe(1);
  });

  it('is the reciprocal of the decay factor', () => {
    const days = 41;
    expect(exposureMultiplier(119.78, days) * decayFactor(119.78, days)).toBeCloseTo(1, 12);
  });
});
