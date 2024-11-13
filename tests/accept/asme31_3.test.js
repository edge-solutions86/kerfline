import { describe, it, expect } from 'vitest';
import {
  codeName,
  services,
  rulesFor,
  windowLengthIn,
  inclusionLimits,
  undercutLimitIn,
  judgeOne,
  judgeCumulative,
  judgeJoint,
} from '../../src/accept/asme31_3.js';
import { makeIndication } from '../../src/indication/measure.js';

const ind = (type, measuredIn, extra = {}) => makeIndication({ type, measuredIn, ...extra });

describe('the services', () => {
  it('names the three columns of the table', () => {
    expect(services()).toEqual(['normal', 'severe_cyclic', 'category_d']);
    expect(codeName()).toBe('B31.3');
  });

  it('refuses a service the table has no column for', () => {
    expect(() => rulesFor('lethal')).toThrow(/fluid service must be one of/);
  });
});

describe('windowLengthIn', () => {
  it('is twelve times the thinner component and not twelve inches', () => {
    expect(windowLengthIn(0.25)).toBe(3);
    expect(windowLengthIn(1)).toBe(12);
  });

  it('grows with the thickness', () => {
    expect(windowLengthIn(0.5)).toBeLessThan(windowLengthIn(0.75));
  });

  it('refuses a thickness at zero', () => {
    expect(() => windowLengthIn(0)).toThrow(/greater than 0/);
  });
});

describe('inclusionLimits', () => {
  it('caps the length at a third of the thickness', () => {
    expect(inclusionLimits(0.75).lengthIn).toBeCloseTo(0.25, 4);
  });

  it('caps the width at the lesser of the fixed limit and a third', () => {
    expect(inclusionLimits(0.75).widthIn).toBeCloseTo(0.098, 4);
    expect(inclusionLimits(0.15).widthIn).toBeCloseTo(0.05, 4);
  });

  it('caps the cumulative length at the thickness itself', () => {
    expect(inclusionLimits(0.5).cumulativeIn).toBe(0.5);
  });

  it('scales every limit together', () => {
    const thin = inclusionLimits(0.25);
    const thick = inclusionLimits(1);
    expect(thick.lengthIn).toBeGreaterThan(thin.lengthIn);
    expect(thick.cumulativeIn).toBeGreaterThan(thin.cumulativeIn);
    expect(thick.windowIn).toBeGreaterThan(thin.windowIn);
  });
});

describe('undercutLimitIn', () => {
  it('is tighter in severe cyclic service', () => {
    expect(undercutLimitIn('severe_cyclic', 1)).toBeLessThan(undercutLimitIn('normal', 1));
  });

  it('falls back to a quarter of the wall on thin work', () => {
    expect(undercutLimitIn('normal', 0.1)).toBeCloseTo(0.025, 4);
  });

  it('is the fixed limit on thick work', () => {
    expect(undercutLimitIn('normal', 2)).toBeCloseTo(0.0625, 4);
  });
});

describe('judgeOne', () => {
  const normal = { service: 'normal', thinnerWallIn: 0.5 };

  it('rejects a crack in any service', () => {
    for (const service of services()) {
      expect(judgeOne(ind('CRACK', 0.02), { service, thinnerWallIn: 0.5 }).disposition).toBe(
        'reject'
      );
    }
  });

  it('rejects incomplete fusion in any service', () => {
    for (const service of services()) {
      expect(judgeOne(ind('IF', 0.02), { service, thinnerWallIn: 0.5 }).disposition).toBe(
        'reject'
      );
    }
  });

  it('rejects incomplete penetration in normal service', () => {
    expect(judgeOne(ind('IP', 0.02), normal).disposition).toBe('reject');
  });

  it('permits incomplete penetration in category D service', () => {
    const verdict = judgeOne(ind('IP', 0.02), {
      service: 'category_d',
      thinnerWallIn: 0.5,
    });
    expect(verdict.disposition).toBe('accept');
  });

  it('caps an inclusion on length', () => {
    expect(judgeOne(ind('SLAG', 0.1), normal).disposition).toBe('accept');
    expect(judgeOne(ind('SLAG', 0.2), normal).disposition).toBe('repair');
  });

  it('caps an inclusion on width separately', () => {
    const wide = ind('SLAG', 0.1, { widthIn: 0.15 });
    expect(judgeOne(wide, normal).reason).toMatch(/wide/);
  });

  it('moves the inclusion verdict with the thickness', () => {
    const slag = ind('SLAG', 0.2);
    expect(judgeOne(slag, { service: 'normal', thinnerWallIn: 0.5 }).disposition).toBe(
      'repair'
    );
    expect(judgeOne(slag, { service: 'normal', thinnerWallIn: 1 }).disposition).toBe('accept');
  });

  it('moves the undercut verdict with the service', () => {
    const undercut = ind('UNDERCUT', 0.05);
    expect(judgeOne(undercut, { service: 'normal', thinnerWallIn: 1 }).disposition).toBe(
      'accept'
    );
    expect(judgeOne(undercut, { service: 'severe_cyclic', thinnerWallIn: 1 }).disposition).toBe(
      'repair'
    );
  });

  it('caps a pore against the wall', () => {
    expect(judgeOne(ind('POROSITY', 0.1), normal).disposition).toBe('accept');
    expect(judgeOne(ind('POROSITY', 0.2), normal).disposition).toBe('repair');
  });

  it('accepts a type the table has no row for', () => {
    expect(judgeOne(ind('ROOT_CONCAVITY', 0.5), normal).disposition).toBe('accept');
  });
});

describe('judgeCumulative', () => {
  it('finds nothing when there are no inclusions', () => {
    expect(judgeCumulative([ind('POROSITY', 0.05, { at: 0 })], 0.5)).toEqual([]);
  });

  it('catches inclusions totalling over the thickness inside the window', () => {
    const set = [
      ind('SLAG', 0.15, { at: 0 }),
      ind('SLAG', 0.15, { at: 1 }),
      ind('SLAG', 0.15, { at: 2 }),
      ind('SLAG', 0.15, { at: 3 }),
    ];
    expect(judgeCumulative(set, 0.5).length).toBeGreaterThan(0);
  });

  it('lets the same total through once the window is wider than the run', () => {
    const set = [
      ind('SLAG', 0.15, { at: 0 }),
      ind('SLAG', 0.15, { at: 1 }),
      ind('SLAG', 0.15, { at: 2 }),
      ind('SLAG', 0.15, { at: 3 }),
    ];
    // On a 1 in wall the cumulative cap is 1 in and 0.6 in is under it.
    expect(judgeCumulative(set, 1)).toEqual([]);
  });
});

describe('judgeJoint', () => {
  it('accepts a clean joint', () => {
    expect(
      judgeJoint({ indications: [], thinnerWallIn: 0.5, service: 'normal' }).disposition
    ).toBe('accept');
  });

  it('defaults to normal service', () => {
    const result = judgeJoint({
      indications: [ind('IP', 0.02, { at: 1 })],
      thinnerWallIn: 0.5,
    });
    expect(result.disposition).toBe('reject');
  });

  it('gives the same joint different answers in different services', () => {
    const indications = [ind('IP', 0.02, { at: 1 })];
    const normal = judgeJoint({ indications, thinnerWallIn: 0.5, service: 'normal' });
    const categoryD = judgeJoint({ indications, thinnerWallIn: 0.5, service: 'category_d' });
    expect(normal.disposition).toBe('reject');
    expect(categoryD.disposition).toBe('accept');
  });

  it('refuses a service it does not carry', () => {
    expect(() =>
      judgeJoint({ indications: [], thinnerWallIn: 0.5, service: 'lethal' })
    ).toThrow(/fluid service/);
  });
});
