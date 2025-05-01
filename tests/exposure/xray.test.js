import { describe, it, expect } from 'vitest';
import {
  xrayReference,
  maximumKilovolts,
  thicknessRange,
  kilovoltsFor,
  thicknessAtKilovolts,
  milliampereMinutesFor,
  planXray,
  headCanReach,
} from '../../src/exposure/xray.js';

describe('the head', () => {
  it('names its reference conditions', () => {
    expect(xrayReference()).toEqual({ distanceIn: 40, film: 'D7', density: 2 });
  });

  it('tops out at four hundred kilovolts', () => {
    expect(maximumKilovolts()).toBe(400);
  });

  it('covers an eighth of an inch to three inches', () => {
    expect(thicknessRange()).toEqual({ minimumIn: 0.125, maximumIn: 3 });
  });
});

describe('kilovoltsFor', () => {
  it('reads the curve at a charted point', () => {
    expect(kilovoltsFor(1)).toBe(200);
    expect(kilovoltsFor(0.5)).toBe(150);
  });

  it('climbs with the wall', () => {
    expect(kilovoltsFor(2)).toBeGreaterThan(kilovoltsFor(1));
    expect(kilovoltsFor(1)).toBeGreaterThan(kilovoltsFor(0.25));
  });

  it('rounds up to a settable step rather than down', () => {
    const between = kilovoltsFor(0.6);
    expect(between % 5).toBe(0);
    expect(between).toBeGreaterThan(150);
    expect(between).toBeLessThan(180);
  });

  it('takes a different step', () => {
    expect(kilovoltsFor(0.6, { step: 10 }) % 10).toBe(0);
  });

  it('never goes past the rating of the head', () => {
    expect(kilovoltsFor(3)).toBe(400);
  });

  it('refuses a wall outside the range rather than guessing', () => {
    expect(() => kilovoltsFor(4)).toThrow(/outside the range this head covers/);
    expect(() => kilovoltsFor(0.05)).toThrow(/outside the range/);
  });
});

describe('thicknessAtKilovolts', () => {
  it('inverts the curve', () => {
    expect(thicknessAtKilovolts(200)).toBeCloseTo(1, 3);
    expect(thicknessAtKilovolts(150)).toBeCloseTo(0.5, 3);
  });

  it('reaches further at a higher setting', () => {
    expect(thicknessAtKilovolts(300)).toBeGreaterThan(thicknessAtKilovolts(200));
  });

  it('refuses a setting off the head', () => {
    expect(() => thicknessAtKilovolts(500)).toThrow(/outside the range of this head/);
    expect(() => thicknessAtKilovolts(50)).toThrow(/outside the range/);
  });
});

describe('milliampereMinutesFor', () => {
  it('reads a charted point', () => {
    expect(milliampereMinutesFor(1)).toBeCloseTo(15, 2);
  });

  it('climbs with the wall', () => {
    expect(milliampereMinutesFor(2)).toBeGreaterThan(milliampereMinutesFor(1));
  });

  it('interpolates on the log scale between points', () => {
    const between = milliampereMinutesFor(0.625);
    expect(between).toBeCloseTo(Math.sqrt(9 * 12), 1);
  });
});

describe('planXray', () => {
  const base = { thicknessIn: 1, distanceIn: 40, milliamps: 5 };

  it('takes the shop minimum kilovoltage by default', () => {
    const plan = planXray(base);
    expect(plan.kilovolts).toBe(200);
    expect(plan.minimumKilovolts).toBe(200);
    expect(plan.aboveMinimum).toBe(false);
  });

  it('works the time out from the current', () => {
    const plan = planXray(base);
    expect(plan.milliampereMinutes).toBeCloseTo(15, 2);
    expect(plan.minutes).toBeCloseTo(3, 2);
    expect(plan.seconds).toBe(180);
  });

  it('squares the exposure with the distance', () => {
    const near = planXray(base);
    const far = planXray({ ...base, distanceIn: 80 });
    expect(far.milliampereMinutes).toBeCloseTo(near.milliampereMinutes * 4, 2);
  });

  it('shortens the time when the current goes up', () => {
    const plan = planXray({ ...base, milliamps: 10 });
    expect(plan.minutes).toBeCloseTo(1.5, 2);
  });

  it('records a decision to run above the minimum', () => {
    const plan = planXray({ ...base, kilovolts: 250 });
    expect(plan.kilovolts).toBe(250);
    expect(plan.aboveMinimum).toBe(true);
  });

  it('refuses a setting that will not penetrate', () => {
    expect(() => planXray({ ...base, kilovolts: 150 })).toThrow(
      /kilovoltage must not be below/
    );
  });

  it('refuses a distance or a current at zero', () => {
    expect(() => planXray({ ...base, distanceIn: 0 })).toThrow(/distance/);
    expect(() => planXray({ ...base, milliamps: 0 })).toThrow(/tube current/);
  });
});

describe('headCanReach', () => {
  it('says a head is up to the job', () => {
    const check = headCanReach({ ratedKilovolts: 300, thicknessIn: 1 });
    expect(check.canReach).toBe(true);
    expect(check.shortBy).toBe(0);
  });

  it('says a head is not, and by how much', () => {
    const check = headCanReach({ ratedKilovolts: 160, thicknessIn: 1 });
    expect(check.canReach).toBe(false);
    expect(check.shortBy).toBe(40);
  });

  it('accepts a head rated exactly at what is needed', () => {
    expect(headCanReach({ ratedKilovolts: 200, thicknessIn: 1 }).canReach).toBe(true);
  });

  it('refuses a rating at zero', () => {
    expect(() => headCanReach({ ratedKilovolts: 0, thicknessIn: 1 })).toThrow(
      /rated kilovoltage/
    );
  });
});
