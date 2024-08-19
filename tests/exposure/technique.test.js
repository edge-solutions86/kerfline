import { describe, it, expect } from 'vitest';
import { exposureFactors, planExposure, timerSetting } from '../../src/exposure/technique.js';
import { chartExposure } from '../../src/exposure/chart.js';

const setup = {
  isotopeId: 'IR192',
  thicknessIn: 1,
  distanceIn: 24,
  filmId: 'D7',
  screens: 'LEAD_STANDARD',
  targetDensity: 2,
  assayCi: 40,
  daysSinceAssay: 0,
};

describe('exposureFactors', () => {
  it('is the bare chart reading at the reference conditions', () => {
    const factors = exposureFactors(setup);
    expect(factors.chartCurieMinutes).toBeCloseTo(chartExposure('IR192', 1), 3);
    expect(factors.distanceFactor).toBe(1);
    expect(factors.filmFactor).toBe(1);
    expect(factors.screenFactor).toBe(1);
    expect(factors.densityFactor).toBe(1);
    expect(factors.curieMinutes).toBeCloseTo(20, 2);
  });

  it('squares the distance factor', () => {
    const factors = exposureFactors({ ...setup, distanceIn: 48 });
    expect(factors.distanceFactor).toBe(4);
    expect(factors.curieMinutes).toBeCloseTo(80, 2);
  });

  it('doubles for a film half the speed', () => {
    const factors = exposureFactors({ ...setup, filmId: 'D5' });
    expect(factors.filmFactor).toBeCloseTo(2, 4);
  });

  it('lengthens the shot when the lead comes off', () => {
    const factors = exposureFactors({ ...setup, screens: 'NONE' });
    expect(factors.screenFactor).toBeGreaterThan(1);
  });

  it('shortens it slightly with heavier lead', () => {
    const factors = exposureFactors({ ...setup, screens: 'LEAD_HEAVY' });
    expect(factors.screenFactor).toBeLessThan(1);
  });

  it('lifts the exposure for a higher wanted density', () => {
    const factors = exposureFactors({ ...setup, targetDensity: 2.5 });
    expect(factors.densityFactor).toBeGreaterThan(1);
  });

  it('multiplies the factors out to the final exposure', () => {
    const factors = exposureFactors({
      ...setup,
      distanceIn: 36,
      filmId: 'D5',
      screens: 'NONE',
      targetDensity: 2.5,
    });
    const product =
      factors.chartCurieMinutes *
      factors.distanceFactor *
      factors.filmFactor *
      factors.screenFactor *
      factors.densityFactor;
    expect(factors.curieMinutes).toBeCloseTo(product, 1);
  });

  it('refuses a thickness the chart does not cover', () => {
    expect(() => exposureFactors({ ...setup, thicknessIn: 4 })).toThrow(/off the IR192 chart/);
  });
});

describe('planExposure', () => {
  it('turns the exposure into a time on today activity', () => {
    const plan = planExposure(setup);
    expect(plan.activityCi).toBe(40);
    expect(plan.minutes).toBeCloseTo(20 / 40, 2);
    expect(plan.seconds).toBe(Math.round(plan.minutes * 60));
  });

  it('asks for longer as the source decays', () => {
    const fresh = planExposure(setup);
    const aged = planExposure({ ...setup, daysSinceAssay: 73.83 });
    expect(aged.activityCi).toBeCloseTo(20, 1);
    expect(aged.minutes).toBeCloseTo(fresh.minutes * 2, 2);
  });

  it('carries the setup through to the sheet', () => {
    const plan = planExposure({ ...setup, filmId: 'D5', screens: 'LEAD_HEAVY' });
    expect(plan.isotope).toBe('IR192');
    expect(plan.film).toBe('D5');
    expect(plan.screens).toBe('LEAD_HEAVY');
    expect(plan.thicknessIn).toBe(1);
  });

  it('falls back to the shop defaults', () => {
    const plan = planExposure({
      isotopeId: 'IR192',
      thicknessIn: 1,
      distanceIn: 24,
      assayCi: 40,
    });
    expect(plan.film).toBe('D7');
    expect(plan.screens).toBe('LEAD_STANDARD');
    expect(plan.targetDensity).toBe(2);
  });

  it('takes a label rather than only an id for the source', () => {
    expect(planExposure({ ...setup, isotopeId: 'ir-192' }).isotope).toBe('IR192');
  });

  it('refuses an assay at or below zero', () => {
    expect(() => planExposure({ ...setup, assayCi: 0 })).toThrow(/greater than 0/);
  });

  it('refuses a day count that is not a number', () => {
    expect(() => planExposure({ ...setup, daysSinceAssay: 'thirty' })).toThrow(/finite number/);
  });
});

describe('timerSetting', () => {
  it('rounds up to a whole second with the working margin on', () => {
    expect(timerSetting(1)).toBe(66);
  });

  it('never rounds down', () => {
    expect(timerSetting(0.5, 0)).toBe(30);
    expect(timerSetting(0.5001, 0)).toBe(31);
  });

  it('takes a different margin', () => {
    expect(timerSetting(1, 0.25)).toBe(75);
  });

  it('refuses a time at zero or a negative margin', () => {
    expect(() => timerSetting(0)).toThrow(/greater than 0/);
    expect(() => timerSetting(1, -0.1)).toThrow(/not be below 0/);
  });
});
