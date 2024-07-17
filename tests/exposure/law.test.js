import { describe, it, expect } from 'vitest';
import {
  intensityAtDistance,
  timeAtDistance,
  distanceForTime,
  curieMinutes,
  minutesForCurieMinutes,
  milliampereMinutes,
  minutesForMilliampereMinutes,
  scaleExposure,
  equivalentExposure,
} from '../../src/exposure/law.js';

describe('intensityAtDistance', () => {
  it('quarters when the distance doubles', () => {
    expect(intensityAtDistance(400, 12, 24)).toBeCloseTo(100, 9);
  });

  it('quadruples when the distance halves', () => {
    expect(intensityAtDistance(100, 24, 12)).toBeCloseTo(400, 9);
  });

  it('leaves the reading alone at the same distance', () => {
    expect(intensityAtDistance(400, 12, 12)).toBeCloseTo(400, 9);
  });

  it('refuses a distance at zero', () => {
    expect(() => intensityAtDistance(400, 0, 12)).toThrow(/greater than 0/);
    expect(() => intensityAtDistance(400, 12, 0)).toThrow(/greater than 0/);
  });
});

describe('timeAtDistance', () => {
  it('goes the other way from intensity', () => {
    expect(timeAtDistance(3, 12, 24)).toBeCloseTo(12, 9);
    expect(timeAtDistance(12, 24, 12)).toBeCloseTo(3, 9);
  });

  it('inverts back through distanceForTime', () => {
    const longer = timeAtDistance(3, 12, 30);
    expect(distanceForTime(3, 12, longer)).toBeCloseTo(30, 9);
  });

  it('refuses a negative time', () => {
    expect(() => timeAtDistance(-1, 12, 24)).toThrow(/not be below 0/);
  });
});

describe('the exposure products', () => {
  it('multiplies activity by time', () => {
    expect(curieMinutes(38, 4.5)).toBeCloseTo(171, 9);
  });

  it('divides back to a time', () => {
    expect(minutesForCurieMinutes(171, 38)).toBeCloseTo(4.5, 9);
  });

  it('asks for longer as the source decays', () => {
    const fresh = minutesForCurieMinutes(171, 38);
    const aged = minutesForCurieMinutes(171, 19);
    expect(aged).toBeCloseTo(fresh * 2, 9);
  });

  it('does the same for an X-ray head', () => {
    expect(milliampereMinutes(5, 2)).toBeCloseTo(10, 9);
    expect(minutesForMilliampereMinutes(10, 5)).toBeCloseTo(2, 9);
  });

  it('refuses an activity or current at zero', () => {
    expect(() => curieMinutes(0, 4.5)).toThrow(/greater than 0/);
    expect(() => minutesForCurieMinutes(171, 0)).toThrow(/greater than 0/);
    expect(() => milliampereMinutes(0, 2)).toThrow(/greater than 0/);
  });
});

describe('scaleExposure', () => {
  it('moves a chart exposure onto the real setup', () => {
    const out = scaleExposure({
      curieMinutesAt: 100,
      chartDistanceIn: 24,
      actualDistanceIn: 48,
    });
    expect(out).toBeCloseTo(400, 9);
  });

  it('shrinks the exposure when the crew works closer than the chart', () => {
    const out = scaleExposure({
      curieMinutesAt: 400,
      chartDistanceIn: 48,
      actualDistanceIn: 24,
    });
    expect(out).toBeCloseTo(100, 9);
  });

  it('leaves it alone at the chart distance', () => {
    const out = scaleExposure({
      curieMinutesAt: 171,
      chartDistanceIn: 24,
      actualDistanceIn: 24,
    });
    expect(out).toBeCloseTo(171, 12);
  });
});

describe('equivalentExposure', () => {
  const base = { activityCi: 38, minutes: 4.5, distanceIn: 24 };

  it('accepts a setup that lays down the same exposure', () => {
    const twiceTheDistance = { activityCi: 38, minutes: 18, distanceIn: 48 };
    expect(equivalentExposure(base, twiceTheDistance)).toBe(true);
  });

  it('accepts halving the source and doubling the time', () => {
    expect(equivalentExposure(base, { activityCi: 19, minutes: 9, distanceIn: 24 })).toBe(true);
  });

  it('rejects a setup that is plainly short', () => {
    expect(equivalentExposure(base, { activityCi: 38, minutes: 2, distanceIn: 24 })).toBe(
      false
    );
  });

  it('takes a tolerance', () => {
    const close = { activityCi: 38, minutes: 4.6, distanceIn: 24 };
    expect(equivalentExposure(base, close, 0.05)).toBe(true);
    expect(equivalentExposure(base, close, 0.001)).toBe(false);
  });

  it('calls two dead sources equivalent rather than dividing by nothing', () => {
    const dead = { activityCi: 38, minutes: 0, distanceIn: 24 };
    expect(equivalentExposure(dead, dead)).toBe(true);
  });
});
