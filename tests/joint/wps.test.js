import { describe, it, expect } from 'vitest';
import {
  positions,
  positionsCoveredBy,
  thicknessRange,
  diameterRange,
  makeProcedure,
  coversJoint,
  proceduresFor,
  chooseProcedure,
} from '../../src/joint/wps.js';
import { makeJoint } from '../../src/joint/spec.js';

const wps = makeProcedure({
  id: 'WPS-14',
  process: 'SMAW',
  couponThicknessIn: 0.375,
  couponOdIn: 6.625,
  testPosition: '6G',
});

describe('positionsCoveredBy', () => {
  it('names the test positions', () => {
    expect(positions()).toEqual(['1G', '2G', '5G', '6G']);
  });

  it('lets a 6G test cover everything', () => {
    expect(positionsCoveredBy('6G')).toEqual(['1G', '2G', '5G', '6G']);
  });

  it('lets a 1G test cover only itself', () => {
    expect(positionsCoveredBy('1G')).toEqual(['1G']);
  });

  it('does not let a 5G test cover 2G', () => {
    expect(positionsCoveredBy('5G')).not.toContain('2G');
  });

  it('does not let a 2G test cover 5G', () => {
    expect(positionsCoveredBy('2G')).not.toContain('5G');
  });

  it('refuses a position nobody tests in', () => {
    expect(() => positionsCoveredBy('3G')).toThrow(/test position must be one of/);
  });
});

describe('thicknessRange', () => {
  it('runs from half the coupon to twice it', () => {
    expect(thicknessRange(0.5)).toEqual({ minimumIn: 0.25, maximumIn: 1 });
  });

  it('is not centred on the coupon', () => {
    const range = thicknessRange(0.5);
    const middle = (range.minimumIn + range.maximumIn) / 2;
    expect(middle).not.toBeCloseTo(0.5, 6);
  });

  it('holds the floor up on a very thin coupon', () => {
    expect(thicknessRange(0.125).minimumIn).toBeCloseTo(0.0625, 4);
  });

  it('opens the ceiling right up past three quarters', () => {
    expect(thicknessRange(0.75).maximumIn).toBe(8);
    expect(thicknessRange(1).maximumIn).toBe(8);
    expect(thicknessRange(0.7).maximumIn).toBeCloseTo(1.4, 4);
  });

  it('refuses a coupon at zero', () => {
    expect(() => thicknessRange(0)).toThrow(/coupon thickness/);
  });
});

describe('diameterRange', () => {
  it('runs upward only', () => {
    expect(diameterRange(6.625).maximumIn).toBe(Infinity);
  });

  it('lets a small bore coupon cover everything above one inch', () => {
    expect(diameterRange(2).minimumIn).toBe(1);
  });

  it('does not let a large coupon come back down to small bore', () => {
    expect(diameterRange(12.75).minimumIn).toBeCloseTo(2.875, 4);
  });

  it('pins a very small coupon to its own size', () => {
    expect(diameterRange(0.84).minimumIn).toBeCloseTo(0.84, 4);
  });
});

describe('makeProcedure', () => {
  it('works the three bands out from the one coupon', () => {
    expect(wps.thicknessRange).toEqual({ minimumIn: 0.1875, maximumIn: 0.75 });
    expect(wps.diameterRange.minimumIn).toBeCloseTo(2.875, 4);
    expect(wps.positionsCovered).toHaveLength(4);
  });

  it('refuses a procedure with no number', () => {
    expect(() =>
      makeProcedure({
        id: ' ',
        couponThicknessIn: 0.375,
        couponOdIn: 6.625,
        testPosition: '6G',
      })
    ).toThrow(/identified by its number/);
  });

  it('refuses a test position it does not know', () => {
    expect(() =>
      makeProcedure({
        id: 'W-1',
        couponThicknessIn: 0.375,
        couponOdIn: 6.625,
        testPosition: '4G',
      })
    ).toThrow(/test position/);
  });
});

describe('coversJoint', () => {
  it('covers a joint inside every band', () => {
    const joint = makeJoint({ id: 'J-1', nominalSize: 6, schedule: 40, process: 'SMAW' });
    const check = coversJoint(wps, joint);
    expect(check.covers).toBe(true);
    expect(check.reasons).toEqual([]);
  });

  it('refuses a joint thicker than the band', () => {
    const joint = makeJoint({ id: 'J-2', odIn: 6.625, wallIn: 0.9, process: 'SMAW' });
    const check = coversJoint(wps, joint);
    expect(check.covers).toBe(false);
    expect(check.reasons.join(' ')).toMatch(/over the qualified/);
  });

  it('refuses a joint thinner than the band', () => {
    const joint = makeJoint({ id: 'J-3', odIn: 6.625, wallIn: 0.1, process: 'SMAW' });
    expect(coversJoint(wps, joint).reasons.join(' ')).toMatch(/under the qualified 0.1875/);
  });

  it('refuses a small bore joint the coupon does not reach down to', () => {
    const joint = makeJoint({ id: 'J-4', nominalSize: 2, schedule: 40, process: 'SMAW' });
    expect(coversJoint(wps, joint).reasons.join(' ')).toMatch(/diameter/);
  });

  it('refuses a position the test does not cover', () => {
    const flat = makeProcedure({
      id: 'WPS-1',
      process: 'SMAW',
      couponThicknessIn: 0.375,
      couponOdIn: 6.625,
      testPosition: '1G',
    });
    const joint = makeJoint({ id: 'J-5', nominalSize: 6, schedule: 40, position: '5G' });
    expect(coversJoint(flat, joint).reasons.join(' ')).toMatch(/position 5G is not covered/);
  });

  it('refuses a different process', () => {
    const joint = makeJoint({ id: 'J-6', nominalSize: 6, schedule: 40, process: 'GTAW' });
    expect(coversJoint(wps, joint).reasons.join(' ')).toMatch(/process GTAW/);
  });

  it('reports every reason at once rather than the first', () => {
    const joint = makeJoint({ id: 'J-7', odIn: 2.375, wallIn: 0.9, process: 'GTAW' });
    expect(coversJoint(wps, joint).reasons.length).toBeGreaterThanOrEqual(3);
  });

  it('refuses a check with no joint', () => {
    expect(() => coversJoint(wps, null)).toThrow(/needs a joint/);
  });
});

describe('proceduresFor and chooseProcedure', () => {
  const thin = makeProcedure({
    id: 'WPS-9',
    process: 'SMAW',
    couponThicknessIn: 0.25,
    couponOdIn: 2.375,
    testPosition: '6G',
  });
  const procedures = [wps, thin];

  it('lists every procedure that covers the joint', () => {
    const joint = makeJoint({ id: 'J-1', nominalSize: 6, schedule: 40, process: 'SMAW' });
    expect(
      proceduresFor(procedures, joint)
        .map((p) => p.id)
        .sort()
    ).toEqual(['WPS-14', 'WPS-9']);
  });

  it('puts the closest coupon first', () => {
    const joint = makeJoint({ id: 'J-1', odIn: 6.625, wallIn: 0.28, process: 'SMAW' });
    expect(chooseProcedure(procedures, joint).id).toBe('WPS-9');
  });

  it('picks the other one when the wall moves', () => {
    const joint = makeJoint({ id: 'J-2', odIn: 6.625, wallIn: 0.432, process: 'SMAW' });
    expect(chooseProcedure(procedures, joint).id).toBe('WPS-14');
  });

  it('finds nothing for a joint outside every band', () => {
    const joint = makeJoint({ id: 'J-3', odIn: 24, wallIn: 2, process: 'SMAW' });
    expect(proceduresFor(procedures, joint)).toEqual([]);
  });

  it('refuses to choose when nothing fits, and says what was tried', () => {
    const joint = makeJoint({ id: 'J-3', odIn: 24, wallIn: 2, process: 'SMAW' });
    try {
      chooseProcedure(procedures, joint);
      throw new Error('should have refused');
    } catch (err) {
      expect(err.message).toMatch(/no qualified procedure/);
      expect(err.details.tried).toHaveLength(2);
    }
  });
});
