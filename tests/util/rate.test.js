import { describe, it, expect } from 'vitest';
import { rateOf, asRate, percentOf, itemsToReach } from '../../src/util/rate.js';

describe('rateOf', () => {
  it('divides', () => {
    expect(rateOf(1, 4)).toBeCloseTo(0.25, 6);
    expect(rateOf(3, 4)).toBeCloseTo(0.75, 6);
  });

  it('is nought over nothing by default', () => {
    expect(rateOf(0, 0)).toBe(0);
  });

  it('is one over nothing when that is what nothing means', () => {
    expect(rateOf(0, 0, { whenEmpty: 1 })).toBe(1);
  });

  it('is exactly one when everything counted', () => {
    expect(rateOf(4, 4)).toBe(1);
  });

  it('is exactly nought when nothing did', () => {
    expect(rateOf(0, 4)).toBe(0);
  });

  it('rounds to four places by default', () => {
    expect(String(rateOf(1, 3)).split('.')[1].length).toBeLessThanOrEqual(4);
  });

  it('takes a different number of places', () => {
    expect(rateOf(1, 3, { places: 2 })).toBe(0.33);
  });

  it('refuses a part bigger than the whole', () => {
    expect(() => rateOf(5, 4)).toThrow(/more than all of them/);
  });

  it('refuses counts that are not whole numbers', () => {
    expect(() => rateOf(1.5, 4)).toThrow(/whole number/);
    expect(() => rateOf(1, 4.5)).toThrow(/whole number/);
  });

  it('refuses a negative count', () => {
    expect(() => rateOf(-1, 4)).toThrow(/not be below 0/);
  });
});

describe('asRate', () => {
  it('carries the counts the rate came from', () => {
    expect(asRate(1, 4)).toEqual({ part: 1, whole: 4, rate: 0.25 });
  });

  it('keeps them apart so a report can print all three', () => {
    const small = asRate(1, 3);
    const large = asRate(30, 90);
    expect(small.rate).toBeCloseTo(large.rate, 3);
    expect(small.whole).not.toBe(large.whole);
  });

  it('passes the empty case through', () => {
    expect(asRate(0, 0, { whenEmpty: 1 }).rate).toBe(1);
  });
});

describe('percentOf', () => {
  it('is the rate times a hundred', () => {
    expect(percentOf(1, 4)).toBe(25);
    expect(percentOf(1, 3)).toBe(33.3);
  });

  it('rounds to one place by default', () => {
    expect(percentOf(2, 3)).toBe(66.7);
  });

  it('takes a different number of places', () => {
    expect(percentOf(2, 3, { places: 2 })).toBe(66.67);
  });

  it('carries the empty case through', () => {
    expect(percentOf(0, 0)).toBe(0);
    expect(percentOf(0, 0, { whenEmpty: 1 })).toBe(100);
  });
});

describe('itemsToReach', () => {
  it('is nothing when the rate is already under the target', () => {
    expect(itemsToReach(1, 100, 0.1)).toBe(0);
    expect(itemsToReach(0, 0, 0.1)).toBe(0);
  });

  it('counts what it takes to get under', () => {
    expect(itemsToReach(4, 20, 0.1)).toBe(20);
  });

  it('actually gets under when those items arrive', () => {
    const needed = itemsToReach(4, 20, 0.1);
    expect(rateOf(4, 20 + needed)).toBeLessThanOrEqual(0.1);
  });

  it('is nothing when the rate sits exactly on the target', () => {
    expect(itemsToReach(2, 20, 0.1)).toBe(0);
  });

  it('refuses a target of nought or above one', () => {
    expect(() => itemsToReach(1, 10, 0)).toThrow(/above zero and no more than one/);
    expect(() => itemsToReach(1, 10, 1.5)).toThrow(/above zero and no more than one/);
  });
});
