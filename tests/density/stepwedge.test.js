import { describe, it, expect } from 'vitest';
import {
  calibrationTolerance,
  calibrationValidDays,
  compareSteps,
  calibrationRecord,
  calibrationCurrent,
  daysUntilExpiry,
  interpolateStep,
} from '../../src/density/stepwedge.js';

const certified = [1, 1.5, 2, 2.5, 3, 3.5, 4];

describe('the calibration rules', () => {
  it('holds each step to five hundredths', () => {
    expect(calibrationTolerance()).toBe(0.05);
  });

  it('lapses after ninety days', () => {
    expect(calibrationValidDays()).toBe(90);
  });
});

describe('compareSteps', () => {
  it('passes an instrument reading true', () => {
    const result = compareSteps([1, 1.5, 2, 2.5, 3, 3.5, 4], certified);
    expect(result.pass).toBe(true);
    expect(result.largestError).toBe(0);
  });

  it('passes an instrument inside the tolerance on every step', () => {
    const readings = certified.map((value) => value + 0.04);
    expect(compareSteps(readings, certified).pass).toBe(true);
  });

  it('accepts a step sitting exactly on the tolerance', () => {
    const readings = certified.map((value) => value + 0.05);
    expect(compareSteps(readings, certified).pass).toBe(true);
  });

  it('fails on one step outside the tolerance', () => {
    const readings = [...certified];
    readings[3] += 0.09;
    const result = compareSteps(readings, certified);
    expect(result.pass).toBe(false);
    expect(result.worstStep).toBe(4);
    expect(result.worstError).toBeCloseTo(0.09, 4);
  });

  it('fails an instrument that averages to nothing but is out at both ends', () => {
    const readings = [...certified];
    readings[0] += 0.09;
    readings[6] -= 0.09;
    const result = compareSteps(readings, certified);
    expect(result.pass).toBe(false);
    const average = result.steps.reduce((sum, step) => sum + step.error, 0);
    expect(average).toBeCloseTo(0, 6);
  });

  it('signs the error the way the instrument reads', () => {
    const readings = [...certified];
    readings[2] -= 0.08;
    expect(compareSteps(readings, certified).worstError).toBeCloseTo(-0.08, 4);
  });

  it('takes a tighter tolerance', () => {
    const readings = certified.map((value) => value + 0.04);
    expect(compareSteps(readings, certified, 0.02).pass).toBe(false);
  });

  it('refuses lists of different lengths', () => {
    expect(() => compareSteps([1, 1.5], certified)).toThrow(/without a certified step/);
  });

  it('refuses an empty calibration', () => {
    expect(() => compareSteps([], [])).toThrow(/at least one step/);
  });

  it('names the step that is not a pair of numbers', () => {
    expect(() => compareSteps([1, '1.5'], [1, 1.5])).toThrow(/step 2 is not a pair/);
  });
});

describe('calibrationRecord', () => {
  const record = calibrationRecord({
    instrument: 'X-Rite 301',
    day: '2025-03-14',
    readings: certified,
    certified,
  });

  it('stores the expiry rather than working it out on read', () => {
    expect(record.day).toBe('2025-03-14');
    expect(record.expires).toBe('2025-06-12');
  });

  it('carries the comparison through', () => {
    expect(record.pass).toBe(true);
    expect(record.steps).toHaveLength(7);
    expect(record.instrument).toBe('X-Rite 301');
  });

  it('refuses a record with no instrument on it', () => {
    expect(() =>
      calibrationRecord({ instrument: '  ', day: '2025-03-14', readings: certified, certified })
    ).toThrow(/names the instrument/);
  });

  it('refuses a date that is not a real day', () => {
    expect(() =>
      calibrationRecord({
        instrument: 'X-Rite 301',
        day: '2025-02-30',
        readings: certified,
        certified,
      })
    ).toThrow(/not a real calendar day/);
  });
});

describe('calibrationCurrent', () => {
  const good = calibrationRecord({
    instrument: 'X-Rite 301',
    day: '2025-03-14',
    readings: certified,
    certified,
  });

  it('is current on the day it was done', () => {
    expect(calibrationCurrent(good, '2025-03-14')).toBe(true);
  });

  it('is current on the day it expires', () => {
    expect(calibrationCurrent(good, '2025-06-12')).toBe(true);
  });

  it('has lapsed the day after', () => {
    expect(calibrationCurrent(good, '2025-06-13')).toBe(false);
  });

  it('is not current before it was done', () => {
    expect(calibrationCurrent(good, '2025-03-13')).toBe(false);
  });

  it('is never current when the calibration itself failed', () => {
    const readings = [...certified];
    readings[0] += 0.5;
    const bad = calibrationRecord({
      instrument: 'X-Rite 301',
      day: '2025-03-14',
      readings,
      certified,
    });
    expect(bad.pass).toBe(false);
    expect(calibrationCurrent(bad, '2025-03-14')).toBe(false);
  });

  it('counts the days left', () => {
    expect(daysUntilExpiry(good, '2025-06-01')).toBe(11);
    expect(daysUntilExpiry(good, '2025-06-13')).toBe(-1);
  });
});

describe('interpolateStep', () => {
  it('lands on a whole step at a certified density', () => {
    expect(interpolateStep(certified, 1)).toBe(1);
    expect(interpolateStep(certified, 2)).toBe(3);
    expect(interpolateStep(certified, 4)).toBe(7);
  });

  it('lands between two steps in the middle', () => {
    expect(interpolateStep(certified, 1.75)).toBeCloseTo(2.5, 3);
  });

  it('refuses a density off the end of the wedge', () => {
    expect(() => interpolateStep(certified, 0.5)).toThrow(/off the end of the wedge/);
    expect(() => interpolateStep(certified, 4.5)).toThrow(/off the end of the wedge/);
  });

  it('refuses a wedge with fewer than two steps', () => {
    expect(() => interpolateStep([2], 2)).toThrow(/at least two steps/);
  });
});
