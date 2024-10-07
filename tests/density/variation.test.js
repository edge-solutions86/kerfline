import { describe, it, expect } from 'vitest';
import {
  variationFractions,
  variationLimits,
  withinVariation,
  judgeVariation,
  judgeArea,
  remedyFor,
} from '../../src/density/variation.js';

describe('the rule itself', () => {
  it('is tighter below the reference than above it', () => {
    const { below, above } = variationFractions();
    expect(below).toBe(0.15);
    expect(above).toBe(0.3);
    expect(below).toBeLessThan(above);
  });
});

describe('variationLimits', () => {
  it('brackets the reference', () => {
    const limits = variationLimits(2);
    expect(limits.minimum).toBeCloseTo(1.7, 6);
    expect(limits.maximum).toBeCloseTo(2.6, 6);
  });

  it('moves with the reference rather than sitting still', () => {
    const low = variationLimits(2);
    const high = variationLimits(3);
    expect(high.minimum).toBeGreaterThan(low.minimum);
    expect(high.maximum).toBeGreaterThan(low.maximum);
  });

  it('refuses a reference at or below zero', () => {
    expect(() => variationLimits(0)).toThrow(/greater than zero/);
  });
});

describe('withinVariation', () => {
  it('accepts a reading inside the band', () => {
    expect(withinVariation(2.4, 2)).toBe(true);
  });

  it('accepts both edges', () => {
    expect(withinVariation(1.7, 2)).toBe(true);
    expect(withinVariation(2.6, 2)).toBe(true);
  });

  it('rejects outside', () => {
    expect(withinVariation(1.69, 2)).toBe(false);
    expect(withinVariation(2.61, 2)).toBe(false);
  });

  it('flips the verdict on the same reading when the reference moves', () => {
    // 2.6 sits right on the dark edge of a film shot at 2.0 and is too light
    // for one shot at 3.5. Same reading, same weld, opposite verdicts.
    expect(withinVariation(2.6, 2)).toBe(true);
    expect(withinVariation(2.6, 3.5)).toBe(false);
  });
});

describe('judgeVariation', () => {
  it('signs the percentage against the reference', () => {
    expect(judgeVariation(2.6, 2).percentFromReference).toBeCloseTo(30, 2);
    expect(judgeVariation(1.7, 2).percentFromReference).toBeCloseTo(-15, 2);
  });

  it('names which way a failing reading went', () => {
    expect(judgeVariation(1.5, 2).verdict).toBe('too light');
    expect(judgeVariation(3, 2).verdict).toBe('too dark');
    expect(judgeVariation(2.1, 2).verdict).toBe('accept');
  });

  it('refuses a reading at or below zero', () => {
    expect(() => judgeVariation(0, 2)).toThrow(/greater than zero/);
  });
});

describe('judgeArea', () => {
  it('passes an area where every reading is inside the band', () => {
    const area = judgeArea([2.1, 2.3, 1.9], 2);
    expect(area.pass).toBe(true);
    expect(area.failures).toHaveLength(0);
    expect(area.readings).toHaveLength(3);
  });

  it('fails on a single reading outside the band', () => {
    const area = judgeArea([2.1, 2.3, 1.5], 2);
    expect(area.pass).toBe(false);
    expect(area.failures).toHaveLength(1);
  });

  it('reports the extremes of the area', () => {
    const area = judgeArea([2.1, 2.5, 1.8], 2);
    expect(area.lightest.density).toBe(1.8);
    expect(area.darkest.density).toBe(2.5);
  });

  it('takes readings with a location on them', () => {
    const area = judgeArea(
      [
        { at: 'A', density: 2.1 },
        { at: 'B', density: 1.5 },
      ],
      2
    );
    expect(area.failures[0].at).toBe('B');
    expect(area.readings[0].at).toBe('A');
  });

  it('refuses an empty area', () => {
    expect(() => judgeArea([], 2)).toThrow(/at least one reading/);
    expect(() => judgeArea('2.1', 2)).toThrow(/must be an array/);
  });

  it('names the reading that carries no density', () => {
    expect(() => judgeArea([2.1, { at: 'B' }], 2)).toThrow(/reading 2 has no density/);
  });
});

describe('remedyFor', () => {
  it('asks for nothing when the area passed', () => {
    const area = judgeArea([2.1, 2.2], 2);
    expect(remedyFor(area)).toEqual({ action: 'none', reshoot: false });
  });

  it('adds an exposure when one end is out', () => {
    const area = judgeArea(
      [
        { at: 'A', density: 2.1 },
        { at: 'B', density: 1.4 },
      ],
      2
    );
    const remedy = remedyFor(area);
    expect(remedy.action).toBe('add an exposure');
    expect(remedy.reshoot).toBe(false);
    expect(remedy.at).toEqual(['B']);
  });

  it('adds exposures at both ends when both are out', () => {
    const area = judgeArea(
      [
        { at: 'A', density: 1.4 },
        { at: 'B', density: 1.4 },
      ],
      2
    );
    expect(remedyFor(area).at).toEqual(['A', 'B']);
  });

  it('reshoots the joint when the failures are spread across more than two spots', () => {
    const area = judgeArea(
      [
        { at: 'A', density: 1.4 },
        { at: 'B', density: 1.4 },
        { at: 'C', density: 1.4 },
      ],
      2
    );
    expect(remedyFor(area).reshoot).toBe(true);
  });

  it('reshoots when the failing readings carry no location', () => {
    const area = judgeArea([1.4, 2.1], 2);
    expect(remedyFor(area).reshoot).toBe(true);
  });
});
