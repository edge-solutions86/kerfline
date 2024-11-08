import { describe, it, expect } from 'vitest';
import {
  codeName,
  elongatedLimitIn,
  limitBands,
  alignedGroups,
  judgePlanar,
  judgeElongated,
  judgeGroups,
  judgeJoint,
} from '../../src/accept/asme8.js';
import { makeIndication } from '../../src/indication/measure.js';

const ind = (type, measuredIn, extra = {}) => makeIndication({ type, measuredIn, ...extra });

describe('elongatedLimitIn', () => {
  it('is a quarter inch on thin work', () => {
    expect(elongatedLimitIn(0.25)).toBe(0.25);
    expect(elongatedLimitIn(0.75)).toBe(0.25);
  });

  it('climbs with the thickness through the middle band', () => {
    expect(elongatedLimitIn(1.5)).toBeCloseTo(0.5, 4);
    expect(elongatedLimitIn(2.25)).toBeCloseTo(0.75, 4);
  });

  it('stops climbing at three quarters of an inch', () => {
    expect(elongatedLimitIn(3)).toBe(0.75);
    expect(elongatedLimitIn(10)).toBe(0.75);
  });

  it('is continuous across both band edges', () => {
    expect(elongatedLimitIn(0.7501)).toBeCloseTo(0.25, 3);
    expect(elongatedLimitIn(2.2501)).toBeCloseTo(0.75, 3);
  });

  it('never goes down as the plate gets thicker', () => {
    let previous = 0;
    for (let t = 0.1; t <= 4; t += 0.05) {
      const limit = elongatedLimitIn(Number(t.toFixed(2)));
      expect(limit).toBeGreaterThanOrEqual(previous - 1e-9);
      previous = limit;
    }
  });

  it('names its three bands', () => {
    expect(limitBands().map((b) => b.rule)).toEqual(['quarter', 'third', 'threequarter']);
  });

  it('refuses a thickness at zero', () => {
    expect(() => elongatedLimitIn(0)).toThrow(/greater than 0/);
  });
});

describe('alignedGroups', () => {
  it('groups indications that sit close together', () => {
    const set = [ind('SLAG', 0.1, { at: 0 }), ind('SLAG', 0.1, { at: 0.3 })];
    expect(alignedGroups(set)).toHaveLength(1);
  });

  it('splits the run where the gap exceeds six times the longest', () => {
    const set = [ind('SLAG', 0.1, { at: 0 }), ind('SLAG', 0.1, { at: 5 })];
    expect(alignedGroups(set)).toHaveLength(2);
  });

  it('measures the gap from the end of one to the start of the next', () => {
    // 0.5 in long ending at 0.5, next starts at 3.4, gap 2.9, under 6 x 0.5.
    const together = [ind('SLAG', 0.5, { at: 0 }), ind('SLAG', 0.1, { at: 3.4 })];
    expect(alignedGroups(together)).toHaveLength(1);
    const apart = [ind('SLAG', 0.5, { at: 0 }), ind('SLAG', 0.1, { at: 3.6 })];
    expect(alignedGroups(apart)).toHaveLength(2);
  });

  it('lets a long indication hold a wider group together', () => {
    const withLong = [ind('SLAG', 1, { at: 0 }), ind('SLAG', 0.1, { at: 5 })];
    expect(alignedGroups(withLong)).toHaveLength(1);
  });

  it('sorts before grouping so the caller does not have to', () => {
    const set = [ind('SLAG', 0.1, { at: 0.3 }), ind('SLAG', 0.1, { at: 0 })];
    expect(alignedGroups(set)).toHaveLength(1);
  });

  it('ignores indications with no position', () => {
    expect(alignedGroups([ind('SLAG', 0.1)])).toEqual([]);
  });
});

describe('judgePlanar', () => {
  it('rejects a crack whatever its size', () => {
    expect(judgePlanar(ind('CRACK', 0.02)).disposition).toBe('reject');
  });

  it('rejects incomplete fusion and incomplete penetration', () => {
    expect(judgePlanar(ind('IF', 0.02)).disposition).toBe('reject');
    expect(judgePlanar(ind('IP', 0.02)).disposition).toBe('reject');
  });

  it('leaves a volumetric indication alone', () => {
    expect(judgePlanar(ind('SLAG', 3)).disposition).toBe('accept');
  });
});

describe('judgeElongated', () => {
  it('accepts an indication under the banded limit', () => {
    expect(judgeElongated(ind('SLAG', 0.2), 0.5).disposition).toBe('accept');
  });

  it('rejects one over it', () => {
    expect(judgeElongated(ind('SLAG', 0.3), 0.5).disposition).toBe('repair');
  });

  it('flips the verdict on the same indication when the plate thickens', () => {
    const slag = ind('SLAG', 0.4);
    expect(judgeElongated(slag, 0.5).disposition).toBe('repair');
    expect(judgeElongated(slag, 1.5).disposition).toBe('accept');
  });

  it('sends a planar indication to the planar rule', () => {
    expect(judgeElongated(ind('CRACK', 0.01), 1).disposition).toBe('reject');
  });

  it('leaves a rounded indication to the rounded rules', () => {
    expect(judgeElongated(ind('POROSITY', 5), 1).disposition).toBe('accept');
  });
});

describe('judgeGroups', () => {
  it('accepts a group totalling under the thickness', () => {
    const set = [ind('SLAG', 0.1, { at: 0 }), ind('SLAG', 0.1, { at: 0.3 })];
    expect(judgeGroups(set, 1)).toEqual([]);
  });

  it('catches a group totalling over the thickness inside the window', () => {
    const set = [
      ind('SLAG', 0.2, { at: 0 }),
      ind('SLAG', 0.2, { at: 0.5 }),
      ind('SLAG', 0.2, { at: 1 }),
    ];
    expect(judgeGroups(set, 0.5).length).toBeGreaterThan(0);
  });

  it('lets the same total through once the group is split by the gap rule', () => {
    const set = [
      ind('SLAG', 0.2, { at: 0 }),
      ind('SLAG', 0.2, { at: 30 }),
      ind('SLAG', 0.2, { at: 60 }),
    ];
    expect(judgeGroups(set, 0.5)).toEqual([]);
  });

  it('scales the window with the thickness', () => {
    const set = [ind('SLAG', 0.6, { at: 0 }), ind('SLAG', 0.6, { at: 2 })];
    // 12t is 6 in on a half inch weld, so the group is inside the window.
    expect(judgeGroups(set, 0.5).length).toBeGreaterThan(0);
  });
});

describe('judgeJoint', () => {
  it('accepts a clean joint', () => {
    expect(judgeJoint({ indications: [], thicknessIn: 1 }).disposition).toBe('accept');
  });

  it('takes a joint out for a planar indication', () => {
    const result = judgeJoint({ indications: [ind('IF', 0.05, { at: 1 })], thicknessIn: 1 });
    expect(result.disposition).toBe('reject');
  });

  it('calls for a repair on an oversize elongated indication', () => {
    const result = judgeJoint({ indications: [ind('SLAG', 0.5, { at: 1 })], thicknessIn: 0.5 });
    expect(result.disposition).toBe('repair');
  });

  it('accepts the same indication on a thicker weld', () => {
    const result = judgeJoint({ indications: [ind('SLAG', 0.5, { at: 1 })], thicknessIn: 2 });
    expect(result.disposition).toBe('accept');
  });

  it('refuses a thickness at zero', () => {
    expect(() => judgeJoint({ indications: [], thicknessIn: 0 })).toThrow(/weld thickness/);
  });

  it('names itself in the result', () => {
    expect(judgeJoint({ indications: [], thicknessIn: 1 }).code).toBe(codeName());
  });
});
