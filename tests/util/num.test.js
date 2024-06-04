import { describe, it, expect } from 'vitest';
import {
  isFiniteNumber,
  round,
  roundTo,
  ceilTo,
  floorTo,
  clamp,
  approxEqual,
  lerp,
  interpolate,
  logInterpolate,
  sum,
  mean,
  median,
  maxOf,
  minOf,
  fixed,
  percentChange,
} from '../../src/util/num.js';

describe('isFiniteNumber', () => {
  it('accepts ordinary numbers including zero and negatives', () => {
    expect(isFiniteNumber(0)).toBe(true);
    expect(isFiniteNumber(-3.25)).toBe(true);
  });

  it('rejects the non numbers that turn up in parsed field data', () => {
    expect(isFiniteNumber(NaN)).toBe(false);
    expect(isFiniteNumber(Infinity)).toBe(false);
    expect(isFiniteNumber('0.280')).toBe(false);
    expect(isFiniteNumber(null)).toBe(false);
    expect(isFiniteNumber(undefined)).toBe(false);
  });
});

describe('round', () => {
  it('rounds half away from zero', () => {
    expect(round(0.5)).toBe(1);
    expect(round(-0.5)).toBe(-1);
    expect(round(1.5)).toBe(2);
    expect(round(2.5)).toBe(3);
  });

  it('gets the binary float cases right', () => {
    expect(round(1.005, 2)).toBe(1.01);
    expect(round(1.045, 2)).toBe(1.05);
    expect(round(8.575, 2)).toBe(8.58);
  });

  it('keeps wall thicknesses at three places', () => {
    expect(round(0.2795, 3)).toBe(0.28);
    expect(round(0.4375, 3)).toBe(0.438);
  });

  it('never hands back negative zero', () => {
    expect(Object.is(round(-0.0004, 2), 0)).toBe(true);
    expect(Object.is(round(0), 0)).toBe(true);
  });

  it('refuses a silly decimal place count', () => {
    expect(() => round(1, -1)).toThrow(/decimal places/);
    expect(() => round(1, 13)).toThrow(/decimal places/);
    expect(() => round(1, 1.5)).toThrow(/decimal places/);
  });

  it('refuses a value that is not a number', () => {
    expect(() => round('1.5', 1)).toThrow(/finite number/);
    expect(() => round(NaN)).toThrow(/finite number/);
  });
});

describe('roundTo, ceilTo and floorTo', () => {
  it('snaps to a caliper step', () => {
    expect(roundTo(0.2812, 0.005)).toBe(0.28);
    expect(roundTo(0.2837, 0.005)).toBe(0.285);
  });

  it('rounds a step away from zero on both sides', () => {
    expect(roundTo(0.0025, 0.005)).toBe(0.005);
    expect(roundTo(-0.0025, 0.005)).toBe(-0.005);
  });

  it('ceils and floors without tripping on an exact multiple', () => {
    expect(ceilTo(0.28, 0.005)).toBe(0.28);
    expect(floorTo(0.28, 0.005)).toBe(0.28);
    expect(ceilTo(0.2801, 0.005)).toBe(0.285);
    expect(floorTo(0.2799, 0.005)).toBe(0.275);
  });

  it('refuses a step that is zero or negative', () => {
    expect(() => roundTo(1, 0)).toThrow(/step must be positive/);
    expect(() => ceilTo(1, -0.1)).toThrow(/step must be positive/);
    expect(() => floorTo(1, 0)).toThrow(/step must be positive/);
  });
});

describe('clamp', () => {
  it('holds a value inside the band', () => {
    expect(clamp(5, 1, 10)).toBe(5);
    expect(clamp(0, 1, 10)).toBe(1);
    expect(clamp(99, 1, 10)).toBe(10);
  });

  it('refuses an inverted band rather than quietly swapping it', () => {
    expect(() => clamp(5, 10, 1)).toThrow(/lower bound is above/);
  });
});

describe('approxEqual', () => {
  it('compares within a tolerance', () => {
    expect(approxEqual(0.1 + 0.2, 0.3)).toBe(true);
    expect(approxEqual(1.0, 1.004, 0.005)).toBe(true);
    expect(approxEqual(1.0, 1.006, 0.005)).toBe(false);
  });

  it('treats a zero tolerance as exact', () => {
    expect(approxEqual(1, 1, 0)).toBe(true);
    expect(approxEqual(1, 1.0000001, 0)).toBe(false);
  });

  it('refuses a negative tolerance', () => {
    expect(() => approxEqual(1, 1, -1)).toThrow(/must not be negative/);
  });
});

describe('interpolation', () => {
  it('lerps between endpoints', () => {
    expect(lerp(10, 20, 0)).toBe(10);
    expect(lerp(10, 20, 1)).toBe(20);
    expect(lerp(10, 20, 0.25)).toBe(12.5);
  });

  it('reads a straight chart line', () => {
    expect(interpolate(0.5, 0, 0, 1, 100)).toBe(50);
    expect(interpolate(0.375, 0.25, 40, 0.5, 80)).toBeCloseTo(60, 9);
  });

  it('extrapolates rather than clamping, which is the caller decision', () => {
    expect(interpolate(2, 0, 0, 1, 100)).toBe(200);
  });

  it('refuses two points at the same x', () => {
    expect(() => interpolate(1, 2, 3, 2, 9)).toThrow(/same x/);
  });

  it('interpolates exposure on a log scale', () => {
    expect(logInterpolate(0.5, 0, 10, 1, 1000)).toBeCloseTo(100, 9);
    expect(logInterpolate(0.25, 0, 10, 1, 10000)).toBeCloseTo(56.234132, 5);
  });

  it('refuses a non positive endpoint on the log scale', () => {
    expect(() => logInterpolate(0.5, 0, 0, 1, 100)).toThrow(/positive endpoints/);
    expect(() => logInterpolate(0.5, 0, 10, 1, -5)).toThrow(/positive endpoints/);
  });
});

describe('the list reducers', () => {
  it('sums, averages and takes a median', () => {
    expect(sum([1, 2, 3, 4])).toBe(10);
    expect(mean([1, 2, 3, 4])).toBe(2.5);
    expect(median([3, 1, 2])).toBe(2);
    expect(median([4, 1, 3, 2])).toBe(2.5);
  });

  it('leaves the caller array alone when taking a median', () => {
    const values = [3, 1, 2];
    median(values);
    expect(values).toEqual([3, 1, 2]);
  });

  it('sums an empty list to zero but refuses an empty average', () => {
    expect(sum([])).toBe(0);
    expect(() => mean([])).toThrow(/empty list/);
    expect(() => median([])).toThrow(/empty list/);
    expect(() => maxOf([])).toThrow(/empty list/);
    expect(() => minOf([])).toThrow(/empty list/);
  });

  it('takes extremes', () => {
    expect(maxOf([0.28, 0.432, 0.154])).toBe(0.432);
    expect(minOf([0.28, 0.432, 0.154])).toBe(0.154);
  });

  it('names the offending index when a list holds a non number', () => {
    expect(() => sum([1, '2', 3])).toThrow(/values\[1\]/);
    expect(() => sum('123')).toThrow(/must be an array/);
  });
});

describe('fixed', () => {
  it('keeps trailing zeros for the report', () => {
    expect(fixed(0.28, 3)).toBe('0.280');
    expect(fixed(2, 2)).toBe('2.00');
  });

  it('rounds before formatting', () => {
    expect(fixed(1.005, 2)).toBe('1.01');
  });

  it('refuses a silly place count', () => {
    expect(() => fixed(1, -1)).toThrow(/decimal places/);
  });
});

describe('percentChange', () => {
  it('signs the change against the reference', () => {
    expect(percentChange(2.0, 2.6)).toBeCloseTo(30, 9);
    expect(percentChange(2.0, 1.7)).toBeCloseTo(-15, 9);
  });

  it('refuses a zero reference', () => {
    expect(() => percentChange(0, 1)).toThrow(/from zero/);
  });
});
