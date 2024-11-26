import { describe, it, expect } from 'vitest';
import {
  defaultThreshold,
  minimumSample,
  repairRate,
  welderRates,
  rollingRate,
  exceedsThreshold,
  weldersOverThreshold,
  cleanJointsToRecover,
} from '../../src/repair/rate.js';

const joint = (welder, disposition) => ({ welder, disposition });

function run(welder, total, repaired) {
  return Array.from({ length: total }, (_unused, i) =>
    joint(welder, i < repaired ? 'repair' : 'accept')
  );
}

describe('the thresholds', () => {
  it('sits at ten percent over twenty joints', () => {
    expect(defaultThreshold()).toBe(0.1);
    expect(minimumSample()).toBe(20);
  });
});

describe('repairRate', () => {
  it('counts repairs over joints shot', () => {
    const rate = repairRate(run('A17', 20, 2));
    expect(rate.shot).toBe(20);
    expect(rate.repaired).toBe(2);
    expect(rate.rate).toBeCloseTo(0.1, 4);
  });

  it('counts a rejection as a repair for rate purposes', () => {
    const rate = repairRate([joint('A17', 'reject'), joint('A17', 'accept')]);
    expect(rate.repaired).toBe(1);
  });

  it('is zero on an empty list rather than dividing by nothing', () => {
    expect(repairRate([])).toEqual({ shot: 0, repaired: 0, rate: 0 });
  });

  it('ignores joints that have not been shot yet', () => {
    const rate = repairRate([joint('A17', 'accept'), { welder: 'A17', disposition: null }]);
    expect(rate.shot).toBe(1);
  });

  it('refuses an entry that is not a record', () => {
    expect(() => repairRate(['J-1'])).toThrow(/must be a joint record/);
    expect(() => repairRate('J-1')).toThrow(/must be an array/);
  });
});

describe('welderRates', () => {
  it('splits the rate by stencil', () => {
    const joints = [...run('A17', 10, 1), ...run('B04', 10, 3)];
    const rates = welderRates(joints);
    expect(rates).toHaveLength(2);
    expect(rates[0].welder).toBe('B04');
    expect(rates[0].rate).toBeCloseTo(0.3, 4);
  });

  it('puts the worst welder first', () => {
    const joints = [...run('A17', 10, 5), ...run('B04', 10, 1)];
    expect(welderRates(joints)[0].welder).toBe('A17');
  });

  it('gathers joints with no welder under one heading', () => {
    const rates = welderRates([joint(null, 'accept'), joint(undefined, 'repair')]);
    expect(rates).toHaveLength(1);
    expect(rates[0].welder).toBe('unknown');
  });
});

describe('rollingRate', () => {
  it('looks only at the most recent joints', () => {
    const joints = [...run('A17', 10, 10), ...run('A17', 20, 0)];
    expect(repairRate(joints).rate).toBeCloseTo(0.3333, 3);
    expect(rollingRate(joints, 20).rate).toBe(0);
  });

  it('takes everything when the window is longer than the history', () => {
    const joints = run('A17', 5, 1);
    expect(rollingRate(joints, 20).shot).toBe(5);
  });

  it('refuses a window that is not a whole number above zero', () => {
    expect(() => rollingRate([], 0)).toThrow(/greater than 0/);
    expect(() => rollingRate([], 1.5)).toThrow(/whole number/);
  });
});

describe('exceedsThreshold', () => {
  it('holds back while the sample is too small to say', () => {
    const result = exceedsThreshold(run('A17', 3, 3));
    expect(result.rate).toBe(1);
    expect(result.exceeds).toBe(false);
    expect(result.enoughToSay).toBe(false);
    expect(result.shortBy).toBe(17);
  });

  it('reports an exceedance once the sample is big enough', () => {
    const result = exceedsThreshold(run('A17', 20, 3));
    expect(result.enoughToSay).toBe(true);
    expect(result.exceeds).toBe(true);
  });

  it('does not call the threshold itself an exceedance', () => {
    const result = exceedsThreshold(run('A17', 20, 2));
    expect(result.rate).toBeCloseTo(0.1, 4);
    expect(result.exceeds).toBe(false);
  });

  it('takes a job specific threshold and sample', () => {
    const result = exceedsThreshold(run('A17', 10, 1), { threshold: 0.05, minimumSample: 10 });
    expect(result.exceeds).toBe(true);
  });

  it('can be asked over a rolling window instead', () => {
    const joints = [...run('A17', 20, 20), ...run('A17', 20, 0)];
    expect(exceedsThreshold(joints).exceeds).toBe(true);
    expect(exceedsThreshold(joints, { windowSize: 20 }).exceeds).toBe(false);
  });
});

describe('weldersOverThreshold', () => {
  it('names only the welders past the limit with enough joints to say so', () => {
    const joints = [...run('A17', 20, 5), ...run('B04', 20, 1), ...run('C09', 3, 3)];
    const over = weldersOverThreshold(joints);
    expect(over.map((entry) => entry.welder)).toEqual(['A17']);
  });

  it('is empty when everybody is inside the limit', () => {
    expect(weldersOverThreshold(run('A17', 20, 1))).toEqual([]);
  });

  it('sorts the worst first', () => {
    const joints = [...run('A17', 20, 4), ...run('B04', 20, 8)];
    expect(weldersOverThreshold(joints)[0].welder).toBe('B04');
  });
});

describe('cleanJointsToRecover', () => {
  it('is nothing when the welder is already under', () => {
    expect(cleanJointsToRecover(run('A17', 20, 1))).toBe(0);
    expect(cleanJointsToRecover([])).toBe(0);
  });

  it('counts the clean joints needed to get back under', () => {
    // 4 repairs in 20 is 20 percent; 40 joints total brings it to 10 percent.
    expect(cleanJointsToRecover(run('A17', 20, 4))).toBe(20);
  });

  it('actually brings the rate back under when those joints are shot', () => {
    const history = run('A17', 20, 4);
    const needed = cleanJointsToRecover(history);
    const after = repairRate([...history, ...run('A17', needed, 0)]);
    expect(after.rate).toBeLessThanOrEqual(0.1);
  });

  it('takes a job specific threshold', () => {
    expect(cleanJointsToRecover(run('A17', 20, 2), { threshold: 0.05 })).toBe(20);
  });
});
