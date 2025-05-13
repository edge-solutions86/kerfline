import { describe, it, expect } from 'vitest';
import {
  codeName,
  loadings,
  rulesFor,
  maxDiscontinuityIn,
  requiredClearanceIn,
  judgeOne,
  judgeSmallSum,
  judgeClearance,
  judgeJoint,
} from '../../src/accept/awsd1_1.js';
import { makeIndication } from '../../src/indication/measure.js';

const ind = (type, measuredIn, extra = {}) => makeIndication({ type, measuredIn, ...extra });

describe('the code', () => {
  it('names itself and its two loadings', () => {
    expect(codeName()).toBe('AWSD1.1');
    expect(loadings()).toEqual(['static', 'cyclic']);
  });

  it('is tighter throughout for cyclic loading', () => {
    const statically = rulesFor('static');
    const cyclically = rulesFor('cyclic');
    expect(cyclically.fraction).toBeLessThan(statically.fraction);
    expect(cyclically.ceilingIn).toBeLessThan(statically.ceilingIn);
    expect(cyclically.sumIn).toBeLessThan(statically.sumIn);
    expect(cyclically.clearanceMultiple).toBeGreaterThan(statically.clearanceMultiple);
  });

  it('refuses a loading it does not know', () => {
    expect(() => rulesFor('impact')).toThrow(/loading must be one of/);
  });
});

describe('maxDiscontinuityIn', () => {
  it('scales with the weld through the middle of the range', () => {
    expect(maxDiscontinuityIn(1.2)).toBeCloseTo(0.4, 4);
  });

  it('holds a floor on thin material', () => {
    expect(maxDiscontinuityIn(0.1)).toBeCloseTo(0.125, 4);
    expect(maxDiscontinuityIn(0.25)).toBeCloseTo(0.125, 4);
  });

  it('holds a ceiling on thick material', () => {
    expect(maxDiscontinuityIn(6)).toBeCloseTo(0.75, 4);
    expect(maxDiscontinuityIn(3)).toBeCloseTo(0.75, 4);
  });

  it('never goes down as the weld thickens', () => {
    let previous = 0;
    for (let t = 0.1; t <= 4; t += 0.1) {
      const cap = maxDiscontinuityIn(Number(t.toFixed(1)));
      expect(cap).toBeGreaterThanOrEqual(previous - 1e-9);
      previous = cap;
    }
  });

  it('is about half as generous for cyclic loading', () => {
    expect(maxDiscontinuityIn(1.2, 'cyclic')).toBeCloseTo(0.2, 4);
  });
});

describe('requiredClearanceIn', () => {
  it('is three times the larger for static loading', () => {
    expect(requiredClearanceIn(0.2)).toBeCloseTo(0.6, 4);
  });

  it('is six times for cyclic loading', () => {
    expect(requiredClearanceIn(0.2, 'cyclic')).toBeCloseTo(1.2, 4);
  });
});

describe('judgeOne', () => {
  it('rejects a crack at any size', () => {
    expect(judgeOne(ind('CRACK', 0.01), { thicknessIn: 1 }).disposition).toBe('reject');
  });

  it('rejects incomplete fusion at any size', () => {
    expect(judgeOne(ind('IF', 0.01), { thicknessIn: 1 }).disposition).toBe('reject');
  });

  it('accepts a discontinuity under the cap', () => {
    expect(judgeOne(ind('SLAG', 0.2), { thicknessIn: 1.2 }).disposition).toBe('accept');
  });

  it('calls a repair over the cap', () => {
    expect(judgeOne(ind('SLAG', 0.5), { thicknessIn: 1.2 }).disposition).toBe('repair');
  });

  it('flips the verdict on the same one under cyclic loading', () => {
    const slag = ind('SLAG', 0.3);
    expect(judgeOne(slag, { thicknessIn: 1.2 }).disposition).toBe('accept');
    expect(judgeOne(slag, { thicknessIn: 1.2, loading: 'cyclic' }).disposition).toBe('repair');
  });
});

describe('judgeSmallSum', () => {
  it('finds nothing when there is nothing small', () => {
    expect(judgeSmallSum([ind('SLAG', 0.3, { at: 0 })])).toEqual([]);
  });

  it('accepts a few small ones inside the sum', () => {
    const set = [ind('POROSITY', 0.1, { at: 0 }), ind('POROSITY', 0.1, { at: 0.5 })];
    expect(judgeSmallSum(set)).toEqual([]);
  });

  it('catches a scatter of small ones packed into one inch', () => {
    const set = [
      ind('POROSITY', 0.1, { at: 0 }),
      ind('POROSITY', 0.1, { at: 0.3 }),
      ind('POROSITY', 0.1, { at: 0.6 }),
      ind('POROSITY', 0.1, { at: 0.9 }),
    ];
    expect(judgeSmallSum(set).length).toBeGreaterThan(0);
  });

  it('lets the same scatter through when it is spread out', () => {
    const set = [
      ind('POROSITY', 0.1, { at: 0 }),
      ind('POROSITY', 0.1, { at: 3 }),
      ind('POROSITY', 0.1, { at: 6 }),
      ind('POROSITY', 0.1, { at: 9 }),
    ];
    expect(judgeSmallSum(set)).toEqual([]);
  });

  it('leaves the large ones out of the sum', () => {
    const set = [ind('SLAG', 0.5, { at: 0 }), ind('SLAG', 0.5, { at: 0.6 })];
    expect(judgeSmallSum(set)).toEqual([]);
  });

  it('is tighter under cyclic loading', () => {
    const set = [
      ind('POROSITY', 0.05, { at: 0 }),
      ind('POROSITY', 0.05, { at: 0.3 }),
      ind('POROSITY', 0.05, { at: 0.6 }),
      ind('POROSITY', 0.05, { at: 0.9 }),
    ];
    expect(judgeSmallSum(set)).toEqual([]);
    expect(judgeSmallSum(set, { loading: 'cyclic' }).length).toBeGreaterThan(0);
  });
});

describe('judgeClearance', () => {
  it('accepts two discontinuities well apart', () => {
    const set = [ind('SLAG', 0.1, { at: 0 }), ind('SLAG', 0.1, { at: 5 })];
    expect(judgeClearance(set)).toEqual([]);
  });

  it('catches two acceptable ones sitting too close', () => {
    const set = [ind('SLAG', 0.2, { at: 0 }), ind('SLAG', 0.2, { at: 0.3 })];
    expect(judgeClearance(set).length).toBeGreaterThan(0);
  });

  it('scales the clearance with the larger of the pair', () => {
    const small = [ind('SLAG', 0.05, { at: 0 }), ind('SLAG', 0.05, { at: 0.4 })];
    const large = [ind('SLAG', 0.3, { at: 0 }), ind('SLAG', 0.3, { at: 0.4 })];
    expect(judgeClearance(small)).toEqual([]);
    expect(judgeClearance(large).length).toBeGreaterThan(0);
  });

  it('wants more clearance under cyclic loading', () => {
    const set = [ind('SLAG', 0.1, { at: 0 }), ind('SLAG', 0.1, { at: 0.5 })];
    expect(judgeClearance(set)).toEqual([]);
    expect(judgeClearance(set, { loading: 'cyclic' }).length).toBeGreaterThan(0);
  });

  it('sorts before comparing so the caller does not have to', () => {
    const set = [ind('SLAG', 0.2, { at: 0.3 }), ind('SLAG', 0.2, { at: 0 })];
    expect(judgeClearance(set).length).toBeGreaterThan(0);
  });

  it('says nothing about a single discontinuity', () => {
    expect(judgeClearance([ind('SLAG', 0.2, { at: 0 })])).toEqual([]);
  });
});

describe('judgeJoint', () => {
  it('accepts a clean joint', () => {
    expect(judgeJoint({ indications: [], thicknessIn: 1 }).disposition).toBe('accept');
  });

  it('takes a joint out for a planar indication', () => {
    const result = judgeJoint({ indications: [ind('CRACK', 0.05, { at: 1 })], thicknessIn: 1 });
    expect(result.disposition).toBe('reject');
  });

  it('fails a joint on the sum rule alone', () => {
    const set = [
      ind('POROSITY', 0.1, { at: 0 }),
      ind('POROSITY', 0.1, { at: 0.3 }),
      ind('POROSITY', 0.1, { at: 0.6 }),
      ind('POROSITY', 0.1, { at: 0.9 }),
    ];
    const result = judgeJoint({ indications: set, thicknessIn: 2 });
    expect(result.disposition).toBe('repair');
    expect(result.reasons.some((reason) => reason.includes('one inch of weld'))).toBe(true);
  });

  it('gives the same joint different answers under the two loadings', () => {
    const set = [ind('SLAG', 0.3, { at: 0 })];
    expect(judgeJoint({ indications: set, thicknessIn: 1.2 }).disposition).toBe('accept');
    expect(
      judgeJoint({ indications: set, thicknessIn: 1.2, loading: 'cyclic' }).disposition
    ).toBe('repair');
  });

  it('refuses a thickness at zero or a loading it does not know', () => {
    expect(() => judgeJoint({ indications: [], thicknessIn: 0 })).toThrow(/weld thickness/);
    expect(() => judgeJoint({ indications: [], thicknessIn: 1, loading: 'impact' })).toThrow(
      /loading/
    );
  });
});
