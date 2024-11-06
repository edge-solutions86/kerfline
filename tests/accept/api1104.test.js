import { describe, it, expect } from 'vitest';
import {
  codeName,
  windowLengthIn,
  ruleFor,
  judgeCrack,
  judgePorosity,
  judgeCluster,
  judgeSlag,
  judgeByLength,
  judgeOne,
  judgeAggregate,
  judgeJoint,
} from '../../src/accept/api1104.js';
import { makeIndication } from '../../src/indication/measure.js';

const ind = (type, measuredIn, extra = {}) => makeIndication({ type, measuredIn, ...extra });

describe('the code itself', () => {
  it('names itself and its window', () => {
    expect(codeName()).toBe('API1104');
    expect(windowLengthIn()).toBe(12);
  });

  it('carries a clause for every type it rules on', () => {
    for (const type of ['IP', 'IF', 'SLAG', 'HOLLOW_BEAD', 'BURN_THROUGH', 'UNDERCUT']) {
      expect(ruleFor(type).clause).toMatch(/^9\.3/);
    }
  });

  it('has no rule for a type it does not cover', () => {
    expect(ruleFor('TUNGSTEN')).toBe(null);
  });
});

describe('judgeCrack', () => {
  it('rejects a crack of any size', () => {
    expect(judgeCrack(ind('CRACK', 0.05)).disposition).toBe('reject');
    expect(judgeCrack(ind('CRACK', 2)).disposition).toBe('reject');
  });

  it('lets a shallow crater crack under five thirty seconds through', () => {
    const crater = ind('CRACK', 0.1, { note: 'crater' });
    expect(judgeCrack(crater).disposition).toBe('accept');
  });

  it('rejects a crater crack that is too long', () => {
    const crater = ind('CRACK', 0.2, { note: 'crater' });
    expect(judgeCrack(crater).disposition).toBe('reject');
  });

  it('rejects an ordinary crack of the same length as a tolerated crater one', () => {
    expect(judgeCrack(ind('CRACK', 0.1)).disposition).toBe('reject');
  });
});

describe('judgePorosity', () => {
  it('accepts a pore inside both caps', () => {
    expect(judgePorosity(ind('POROSITY', 0.05), 0.5).disposition).toBe('accept');
  });

  it('caps a pore at an eighth of an inch on a thick wall', () => {
    expect(judgePorosity(ind('POROSITY', 0.13), 1).disposition).toBe('repair');
    expect(judgePorosity(ind('POROSITY', 0.125), 1).disposition).toBe('accept');
  });

  it('caps a pore at a quarter of the wall on a thin one', () => {
    // A quarter of a 0.25 in wall is 0.0625 in, tighter than the 0.125 cap.
    expect(judgePorosity(ind('POROSITY', 0.1), 0.25).disposition).toBe('repair');
    expect(judgePorosity(ind('POROSITY', 0.06), 0.25).disposition).toBe('accept');
  });

  it('flips the verdict on the same pore when the wall changes', () => {
    const pore = ind('POROSITY', 0.1);
    expect(judgePorosity(pore, 0.25).disposition).toBe('repair');
    expect(judgePorosity(pore, 0.5).disposition).toBe('accept');
  });

  it('refuses a wall at zero', () => {
    expect(() => judgePorosity(ind('POROSITY', 0.05), 0)).toThrow(/greater than 0/);
  });
});

describe('judgeCluster and judgeSlag', () => {
  it('caps a cluster at half an inch', () => {
    expect(judgeCluster(ind('CLUSTER', 0.4)).disposition).toBe('accept');
    expect(judgeCluster(ind('CLUSTER', 0.6)).disposition).toBe('repair');
  });

  it('caps slag at two inches long', () => {
    expect(judgeSlag(ind('SLAG', 1.5)).disposition).toBe('accept');
    expect(judgeSlag(ind('SLAG', 2.5)).disposition).toBe('repair');
  });

  it('caps slag separately on width', () => {
    const wide = ind('SLAG', 0.5, { widthIn: 0.1 });
    const verdict = judgeSlag(wide);
    expect(verdict.disposition).toBe('repair');
    expect(verdict.reason).toMatch(/wide/);
  });

  it('takes the width cap before the length one', () => {
    const wideAndLong = ind('SLAG', 3, { widthIn: 0.1 });
    expect(judgeSlag(wideAndLong).reason).toMatch(/wide/);
  });

  it('accepts slag inside both caps', () => {
    expect(judgeSlag(ind('SLAG', 1, { widthIn: 0.05 })).disposition).toBe('accept');
  });
});

describe('judgeByLength', () => {
  it('caps incomplete penetration at one inch', () => {
    expect(judgeByLength(ind('IP', 0.9)).disposition).toBe('accept');
    expect(judgeByLength(ind('IP', 1.1)).disposition).toBe('repair');
  });

  it('caps hollow bead at half an inch', () => {
    expect(judgeByLength(ind('HOLLOW_BEAD', 0.4)).disposition).toBe('accept');
    expect(judgeByLength(ind('HOLLOW_BEAD', 0.6)).disposition).toBe('repair');
  });

  it('caps burn through at a quarter inch', () => {
    expect(judgeByLength(ind('BURN_THROUGH', 0.3)).disposition).toBe('repair');
  });

  it('accepts a type the code has no rule for', () => {
    expect(judgeByLength(ind('TUNGSTEN', 0.9)).disposition).toBe('accept');
  });
});

describe('judgeOne', () => {
  it('routes each type to its own rule', () => {
    expect(judgeOne(ind('CRACK', 0.05), 0.5).clause).toBe('9.3.9');
    expect(judgeOne(ind('POROSITY', 0.05), 0.5).clause).toBe('9.3.3');
    expect(judgeOne(ind('SLAG', 0.5), 0.5).clause).toBe('9.3.6');
    expect(judgeOne(ind('IP', 0.5), 0.5).clause).toBe('9.3.2');
  });
});

describe('judgeAggregate', () => {
  it('finds nothing when there is nothing of that type', () => {
    expect(judgeAggregate([ind('SLAG', 0.5)], 'IP', 20)).toEqual([]);
  });

  it('catches a total over the window cap even when each one passes', () => {
    const set = [
      ind('IP', 0.4, { at: 0 }),
      ind('IP', 0.4, { at: 1 }),
      ind('IP', 0.4, { at: 2 }),
    ];
    const verdicts = judgeAggregate(set, 'IP', 40);
    expect(verdicts.length).toBeGreaterThan(0);
    expect(verdicts[0].reason).toMatch(/totals/);
  });

  it('accepts the same total spread past the window', () => {
    const spread = [
      ind('IP', 0.4, { at: 0 }),
      ind('IP', 0.4, { at: 20 }),
      ind('IP', 0.4, { at: 40 }),
    ];
    const verdicts = judgeAggregate(spread, 'IP', 80);
    const windowFindings = verdicts.filter((v) => v.reason.includes('in 12 in'));
    expect(windowFindings).toEqual([]);
  });

  it('catches the percentage cap on a short weld', () => {
    // 0.9 in of incomplete penetration on a 7.5 in weld is 12 percent.
    const set = [ind('IP', 0.9, { at: 0 })];
    const verdicts = judgeAggregate(set, 'IP', 7.5);
    expect(verdicts.some((v) => v.reason.includes('percent'))).toBe(true);
  });

  it('lets the same total through on a long weld', () => {
    const set = [ind('IP', 0.9, { at: 0 })];
    const verdicts = judgeAggregate(set, 'IP', 60);
    expect(verdicts.some((v) => v.reason.includes('percent'))).toBe(false);
  });

  it('has no percentage cap on slag', () => {
    expect(ruleFor('SLAG').percent).toBe(null);
    const set = [ind('SLAG', 1.5, { at: 0 })];
    const verdicts = judgeAggregate(set, 'SLAG', 7.5);
    expect(verdicts.some((v) => v.reason.includes('percent'))).toBe(false);
  });
});

describe('judgeJoint', () => {
  const joint = { wallIn: 0.28, weldLengthIn: 20.8 };

  it('accepts a clean joint', () => {
    const result = judgeJoint({ ...joint, indications: [] });
    expect(result.disposition).toBe('accept');
    expect(result.findings).toEqual([]);
  });

  it('accepts a joint whose indications are all inside the caps', () => {
    const indications = [ind('POROSITY', 0.05, { at: 2 }), ind('SLAG', 0.5, { at: 8 })];
    expect(judgeJoint({ ...joint, indications }).disposition).toBe('accept');
  });

  it('takes a joint out for a crack whatever else is on it', () => {
    const indications = [ind('POROSITY', 0.05, { at: 2 }), ind('CRACK', 0.1, { at: 8 })];
    const result = judgeJoint({ ...joint, indications });
    expect(result.disposition).toBe('reject');
    expect(result.clause).toBe('9.3.9');
  });

  it('carries every finding and not only the governing one', () => {
    const indications = [ind('SLAG', 3, { at: 2 }), ind('CRACK', 0.1, { at: 8 })];
    const result = judgeJoint({ ...joint, indications });
    expect(result.findings.length).toBeGreaterThanOrEqual(2);
  });

  it('fails a joint on the aggregate alone', () => {
    const indications = [
      ind('IP', 0.4, { at: 0 }),
      ind('IP', 0.4, { at: 1 }),
      ind('IP', 0.4, { at: 2 }),
    ];
    const result = judgeJoint({ ...joint, indications });
    expect(result.disposition).toBe('repair');
    expect(result.reasons.some((r) => r.includes('totals'))).toBe(true);
  });

  it('refuses a joint with no wall or weld length', () => {
    expect(() => judgeJoint({ indications: [], wallIn: 0, weldLengthIn: 20 })).toThrow(
      /wall thickness/
    );
    expect(() => judgeJoint({ indications: [], wallIn: 0.28, weldLengthIn: 0 })).toThrow(
      /weld length/
    );
  });
});
