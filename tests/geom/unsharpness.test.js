import { describe, it, expect } from 'vitest';
import {
  unsharpnessLimit,
  unsharpnessBands,
  geometricUnsharpness,
  minimumSourceToObject,
  checkUnsharpness,
} from '../../src/geom/unsharpness.js';

describe('unsharpnessLimit', () => {
  it('reads the four bands', () => {
    expect(unsharpnessLimit(0.5)).toBe(0.02);
    expect(unsharpnessLimit(2.5)).toBe(0.03);
    expect(unsharpnessLimit(3.5)).toBe(0.04);
    expect(unsharpnessLimit(6)).toBe(0.07);
  });

  it('puts each boundary in the band below it', () => {
    expect(unsharpnessLimit(2)).toBe(0.02);
    expect(unsharpnessLimit(2.0001)).toBe(0.03);
    expect(unsharpnessLimit(3)).toBe(0.03);
    expect(unsharpnessLimit(4)).toBe(0.04);
    expect(unsharpnessLimit(4.0001)).toBe(0.07);
  });

  it('refuses a thickness at or below zero', () => {
    expect(() => unsharpnessLimit(0)).toThrow(/greater than 0/);
    expect(() => unsharpnessLimit(-1)).toThrow(/greater than 0/);
    expect(() => unsharpnessLimit('0.5')).toThrow(/finite number/);
  });

  it('publishes the bands for the technique sheet', () => {
    expect(unsharpnessBands()).toEqual([
      { over: 0, upTo: 2, limitIn: 0.02 },
      { over: 2, upTo: 3, limitIn: 0.03 },
      { over: 3, upTo: 4, limitIn: 0.04 },
      { over: 4, upTo: Infinity, limitIn: 0.07 },
    ]);
  });
});

describe('geometricUnsharpness', () => {
  it('is source size times standoff over distance', () => {
    const ug = geometricUnsharpness({
      sourceSizeIn: 0.12,
      objectToFilmIn: 0.5,
      sourceToObjectIn: 12,
    });
    expect(ug).toBeCloseTo(0.005, 6);
  });

  it('is zero when the object sits on the film', () => {
    const ug = geometricUnsharpness({
      sourceSizeIn: 0.12,
      objectToFilmIn: 0,
      sourceToObjectIn: 12,
    });
    expect(ug).toBe(0);
  });

  it('halves when the distance doubles', () => {
    const near = geometricUnsharpness({
      sourceSizeIn: 0.12,
      objectToFilmIn: 1,
      sourceToObjectIn: 12,
    });
    const far = geometricUnsharpness({
      sourceSizeIn: 0.12,
      objectToFilmIn: 1,
      sourceToObjectIn: 24,
    });
    expect(far).toBeCloseTo(near / 2, 9);
  });

  it('refuses a source size or distance at zero', () => {
    const base = { sourceSizeIn: 0.12, objectToFilmIn: 1, sourceToObjectIn: 12 };
    expect(() => geometricUnsharpness({ ...base, sourceSizeIn: 0 })).toThrow(/source size/);
    expect(() => geometricUnsharpness({ ...base, sourceToObjectIn: 0 })).toThrow(
      /source to object distance/
    );
  });

  it('refuses a negative standoff but allows zero', () => {
    const base = { sourceSizeIn: 0.12, objectToFilmIn: 1, sourceToObjectIn: 12 };
    expect(() => geometricUnsharpness({ ...base, objectToFilmIn: -1 })).toThrow(
      /not be below 0/
    );
  });
});

describe('minimumSourceToObject', () => {
  it('is the distance that lands exactly on the limit', () => {
    const minimum = minimumSourceToObject({
      sourceSizeIn: 0.12,
      objectToFilmIn: 0.5,
      limitIn: 0.02,
    });
    expect(minimum).toBeCloseTo(3, 9);
    const ug = geometricUnsharpness({
      sourceSizeIn: 0.12,
      objectToFilmIn: 0.5,
      sourceToObjectIn: minimum,
    });
    expect(ug).toBeCloseTo(0.02, 9);
  });

  it('is zero when the object sits on the film', () => {
    expect(
      minimumSourceToObject({ sourceSizeIn: 0.12, objectToFilmIn: 0, limitIn: 0.02 })
    ).toBe(0);
  });

  it('refuses a limit at zero', () => {
    expect(() =>
      minimumSourceToObject({ sourceSizeIn: 0.12, objectToFilmIn: 1, limitIn: 0 })
    ).toThrow(/unsharpness limit/);
  });
});

describe('checkUnsharpness', () => {
  const setup = {
    sourceSizeIn: 0.12,
    objectToFilmIn: 0.5,
    sourceToObjectIn: 12,
    materialThicknessIn: 0.5,
  };

  it('passes a setup with room to spare and reports the margin', () => {
    const result = checkUnsharpness(setup);
    expect(result.pass).toBe(true);
    expect(result.unsharpnessIn).toBe(0.005);
    expect(result.limitIn).toBe(0.02);
    expect(result.marginIn).toBe(0.015);
    expect(result.shortfallIn).toBe(0);
  });

  it('fails a setup that is cranked in too close and says by how much', () => {
    const result = checkUnsharpness({ ...setup, sourceToObjectIn: 2 });
    expect(result.pass).toBe(false);
    expect(result.unsharpnessIn).toBe(0.03);
    expect(result.marginIn).toBeLessThan(0);
    expect(result.minimumSourceToObjectIn).toBe(3);
    expect(result.shortfallIn).toBe(1);
  });

  it('passes exactly on the limit', () => {
    const result = checkUnsharpness({ ...setup, sourceToObjectIn: 3 });
    expect(result.unsharpnessIn).toBe(0.02);
    expect(result.pass).toBe(true);
    expect(result.marginIn).toBe(0);
  });

  it('moves the verdict when only the material thickness changes', () => {
    const thin = checkUnsharpness({ ...setup, sourceToObjectIn: 2.5 });
    const thick = checkUnsharpness({
      ...setup,
      sourceToObjectIn: 2.5,
      materialThicknessIn: 2.5,
    });
    expect(thin.pass).toBe(false);
    expect(thick.pass).toBe(true);
    expect(thin.unsharpnessIn).toBe(thick.unsharpnessIn);
  });
});
