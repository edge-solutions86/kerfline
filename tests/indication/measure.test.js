import { describe, it, expect } from 'vitest';
import {
  makeIndication,
  governingSizeIn,
  cumulativeLengthIn,
  longestIn,
  overLimit,
  worstWindow,
} from '../../src/indication/measure.js';

describe('makeIndication', () => {
  it('takes the measurement as read on a contact shot', () => {
    const indication = makeIndication({ type: 'SLAG', measuredIn: 0.25 });
    expect(indication.sizeIn).toBe(0.25);
    expect(indication.magnification).toBe(1);
  });

  it('backs the magnification out of the measurement', () => {
    const indication = makeIndication({
      type: 'SLAG',
      measuredIn: 0.3125,
      magnification: 1.25,
    });
    expect(indication.sizeIn).toBeCloseTo(0.25, 4);
    expect(indication.measuredIn).toBe(0.3125);
  });

  it('keeps what was measured so the film can be checked again', () => {
    const indication = makeIndication({
      type: 'SLAG',
      measuredIn: 0.3125,
      magnification: 1.25,
    });
    expect(indication.measuredIn).toBe(0.3125);
    expect(indication.sizeIn).not.toBe(indication.measuredIn);
  });

  it('carries the type through', () => {
    const indication = makeIndication({ type: 'crack', measuredIn: 0.1 });
    expect(indication.type).toBe('CRACK');
    expect(indication.planar).toBe(true);
    expect(indication.measure).toBe('length');
  });

  it('scales a width the same way', () => {
    const indication = makeIndication({
      type: 'POROSITY',
      measuredIn: 0.05,
      widthIn: 0.0625,
      magnification: 1.25,
    });
    expect(indication.widthIn).toBeCloseTo(0.05, 4);
  });

  it('leaves the width null when none was taken', () => {
    expect(makeIndication({ type: 'SLAG', measuredIn: 0.25 }).widthIn).toBe(null);
  });

  it('refuses a magnification below one', () => {
    expect(() =>
      makeIndication({ type: 'SLAG', measuredIn: 0.25, magnification: 0.9 })
    ).toThrow(/not be below 1/);
  });

  it('refuses a negative measurement', () => {
    expect(() => makeIndication({ type: 'SLAG', measuredIn: -0.1 })).toThrow(/not be below 0/);
  });

  it('refuses a type it does not carry', () => {
    expect(() => makeIndication({ type: 'LAMINATION', measuredIn: 0.1 })).toThrow(
      /no indication type/
    );
  });

  it('refuses something that is not an object', () => {
    expect(() => makeIndication('slag')).toThrow(/described by an object/);
  });
});

describe('governingSizeIn', () => {
  it('is the length for something judged on length', () => {
    const slag = makeIndication({ type: 'SLAG', measuredIn: 0.25, widthIn: 0.4 });
    expect(governingSizeIn(slag)).toBe(0.25);
  });

  it('is the larger dimension for something judged on diameter', () => {
    const pore = makeIndication({ type: 'POROSITY', measuredIn: 0.05, widthIn: 0.08 });
    expect(governingSizeIn(pore)).toBeCloseTo(0.08, 4);
  });

  it('falls back to the length when no width was taken', () => {
    const pore = makeIndication({ type: 'POROSITY', measuredIn: 0.05 });
    expect(governingSizeIn(pore)).toBe(0.05);
  });
});

describe('cumulativeLengthIn', () => {
  it('adds the lengths', () => {
    const set = [
      makeIndication({ type: 'SLAG', measuredIn: 0.25 }),
      makeIndication({ type: 'SLAG', measuredIn: 0.125 }),
    ];
    expect(cumulativeLengthIn(set)).toBeCloseTo(0.375, 4);
  });

  it('leaves the round ones out of the total', () => {
    const set = [
      makeIndication({ type: 'SLAG', measuredIn: 0.25 }),
      makeIndication({ type: 'POROSITY', measuredIn: 0.06 }),
    ];
    expect(cumulativeLengthIn(set)).toBeCloseTo(0.25, 4);
  });

  it('is zero for an empty set', () => {
    expect(cumulativeLengthIn([])).toBe(0);
  });

  it('adds the true lengths and not the measured ones', () => {
    const set = [makeIndication({ type: 'SLAG', measuredIn: 0.5, magnification: 2 })];
    expect(cumulativeLengthIn(set)).toBeCloseTo(0.25, 4);
  });

  it('refuses anything that is not an array', () => {
    expect(() => cumulativeLengthIn('0.25')).toThrow(/must be an array/);
  });
});

describe('longestIn and overLimit', () => {
  const set = [
    makeIndication({ type: 'SLAG', measuredIn: 0.25 }),
    makeIndication({ type: 'SLAG', measuredIn: 0.5 }),
    makeIndication({ type: 'POROSITY', measuredIn: 0.06 }),
  ];

  it('finds the longest', () => {
    expect(longestIn(set)).toBe(0.5);
  });

  it('is zero for an empty set', () => {
    expect(longestIn([])).toBe(0);
  });

  it('lists what is over a limit', () => {
    expect(overLimit(set, 0.25)).toHaveLength(1);
    expect(overLimit(set, 0.5)).toHaveLength(0);
    expect(overLimit(set, 0.05)).toHaveLength(3);
  });

  it('treats a limit as inclusive', () => {
    expect(overLimit(set, 0.5)).toEqual([]);
  });
});

describe('worstWindow', () => {
  const set = [
    makeIndication({ type: 'SLAG', measuredIn: 0.2, at: 0 }),
    makeIndication({ type: 'SLAG', measuredIn: 0.3, at: 1 }),
    makeIndication({ type: 'SLAG', measuredIn: 0.4, at: 2 }),
    makeIndication({ type: 'SLAG', measuredIn: 0.5, at: 20 }),
  ];

  it('finds the run of weld carrying the most', () => {
    const worst = worstWindow(set, 6);
    expect(worst.startIn).toBe(0);
    expect(worst.totalIn).toBeCloseTo(0.9, 4);
    expect(worst.indications).toHaveLength(3);
  });

  it('shrinks the total as the window narrows', () => {
    expect(worstWindow(set, 1.5).totalIn).toBeCloseTo(0.7, 4);
    expect(worstWindow(set, 0.5).totalIn).toBeCloseTo(0.5, 4);
  });

  it('picks the isolated one up when the window is small enough', () => {
    const worst = worstWindow(set, 0.5);
    expect(worst.startIn).toBe(20);
  });

  it('returns nothing when no indication carries a position', () => {
    const unplaced = [makeIndication({ type: 'SLAG', measuredIn: 0.2 })];
    expect(worstWindow(unplaced, 6)).toEqual({ totalIn: 0, startIn: 0, indications: [] });
  });

  it('refuses a window at zero', () => {
    expect(() => worstWindow(set, 0)).toThrow(/greater than 0/);
  });
});
