import { describe, it, expect } from 'vitest';
import {
  sourceKinds,
  sourceKindOf,
  densityLimits,
  ceilingDensity,
  withinLimits,
  judgeDensity,
  judgeComposite,
} from '../../src/density/limits.js';

describe('sourceKindOf', () => {
  it('names the two kinds', () => {
    expect(sourceKinds()).toEqual(['xray', 'gamma']);
  });

  it('passes a kind straight through', () => {
    expect(sourceKindOf('gamma')).toBe('gamma');
    expect(sourceKindOf('xray')).toBe('xray');
  });

  it('reads an isotope as a gamma source', () => {
    expect(sourceKindOf('IR192')).toBe('gamma');
    expect(sourceKindOf('co-60')).toBe('gamma');
  });

  it('refuses something that is neither', () => {
    expect(() => sourceKindOf('neutron')).toThrow(/no isotope neutron/);
  });
});

describe('densityLimits', () => {
  it('sets a higher floor for gamma than for X-ray', () => {
    expect(densityLimits('gamma').minimum).toBe(2);
    expect(densityLimits('xray').minimum).toBe(1.8);
  });

  it('sets the same ceiling for both', () => {
    expect(densityLimits('gamma').maximum).toBe(4);
    expect(densityLimits('xray').maximum).toBe(4);
    expect(ceilingDensity()).toBe(4);
  });

  it('drops the floor for each film of a composite set', () => {
    expect(densityLimits('gamma', { composite: true }).minimum).toBe(1.3);
    expect(densityLimits('xray', { composite: true }).minimum).toBe(1.3);
  });

  it('leaves the ceiling alone for a composite set', () => {
    expect(densityLimits('gamma', { composite: true }).maximum).toBe(4);
  });
});

describe('withinLimits', () => {
  it('accepts a reading inside the band', () => {
    expect(withinLimits(2.5, 'gamma')).toBe(true);
  });

  it('rejects one outside it', () => {
    expect(withinLimits(1.9, 'gamma')).toBe(false);
    expect(withinLimits(4.1, 'gamma')).toBe(false);
  });

  it('accepts at exactly the floor and the ceiling', () => {
    expect(withinLimits(2, 'gamma')).toBe(true);
    expect(withinLimits(4, 'gamma')).toBe(true);
  });

  it('turns a light gamma film into an acceptable X-ray one', () => {
    expect(withinLimits(1.9, 'gamma')).toBe(false);
    expect(withinLimits(1.9, 'xray')).toBe(true);
  });

  it('answers false rather than throwing on a bad reading', () => {
    expect(withinLimits('2.5', 'gamma')).toBe(false);
  });
});

describe('judgeDensity', () => {
  it('accepts a good film', () => {
    const verdict = judgeDensity(2.5, 'gamma');
    expect(verdict.pass).toBe(true);
    expect(verdict.verdict).toBe('accept');
    expect(verdict.kind).toBe('gamma');
  });

  it('calls a thin film light', () => {
    expect(judgeDensity(1.7, 'gamma').verdict).toBe('light');
  });

  it('calls an overexposed film dark', () => {
    expect(judgeDensity(4.2, 'gamma').verdict).toBe('dark');
  });

  it('reports the limits it judged against', () => {
    const verdict = judgeDensity(1.9, 'xray');
    expect(verdict.minimum).toBe(1.8);
    expect(verdict.maximum).toBe(4);
    expect(verdict.pass).toBe(true);
  });

  it('refuses a density that is not a positive number', () => {
    expect(() => judgeDensity(0, 'gamma')).toThrow(/greater than zero/);
    expect(() => judgeDensity('2.5', 'gamma')).toThrow(/finite number/);
  });
});

describe('judgeComposite', () => {
  it('passes a pair where each film clears the composite floor and the sum clears the single one', () => {
    const verdict = judgeComposite([1.4, 1.4], 'gamma');
    expect(verdict.stackedDensity).toBeCloseTo(2.8, 6);
    expect(verdict.everyFilmPasses).toBe(true);
    expect(verdict.stackedPasses).toBe(true);
    expect(verdict.pass).toBe(true);
  });

  it('fails when one film of the pair is under the composite floor', () => {
    const verdict = judgeComposite([1.2, 1.8], 'gamma');
    expect(verdict.everyFilmPasses).toBe(false);
    expect(verdict.films[0].verdict).toBe('light');
    expect(verdict.pass).toBe(false);
  });

  it('fails a pair that stacks up short even with both films legal on their own', () => {
    const verdict = judgeComposite([1.3, 1.3], 'xray');
    expect(verdict.everyFilmPasses).toBe(true);
    expect(verdict.stackedDensity).toBeCloseTo(2.6, 6);
    expect(verdict.stackedPasses).toBe(true);
  });

  it('fails a pair that stacks past the ceiling', () => {
    const verdict = judgeComposite([2.2, 2.2], 'gamma');
    expect(verdict.stackedDensity).toBeCloseTo(4.4, 6);
    expect(verdict.stackedPasses).toBe(false);
    expect(verdict.pass).toBe(false);
  });

  it('refuses anything that is not a set of two or more', () => {
    expect(() => judgeComposite([2.5], 'gamma')).toThrow(/two or more films/);
    expect(() => judgeComposite('2.5', 'gamma')).toThrow(/two or more films/);
  });

  it('names the film that is not a number', () => {
    expect(() => judgeComposite([1.4, 'x'], 'gamma')).toThrow(/film 2 density/);
  });
});
