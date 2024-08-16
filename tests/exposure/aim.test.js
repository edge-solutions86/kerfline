import { describe, it, expect } from 'vitest';
import {
  readableDensityRange,
  filmGradient,
  exposureRatioForDensity,
  densityForExposureRatio,
  aimAtDensity,
  densityIsReadable,
  reshootFactor,
} from '../../src/exposure/aim.js';

describe('filmGradient', () => {
  it('is steeper on a finer film', () => {
    expect(filmGradient('D4')).toBeGreaterThan(filmGradient('D7'));
    expect(filmGradient('D7')).toBeGreaterThan(filmGradient('D8'));
  });

  it('carries a gradient for every qualified film', () => {
    for (const id of ['D2', 'D3', 'D4', 'D5', 'D7', 'D8']) {
      expect(filmGradient(id)).toBeGreaterThan(1);
    }
  });

  it('refuses a film off the list', () => {
    expect(() => filmGradient('AA400')).toThrow(/no film AA400/);
  });
});

describe('exposureRatioForDensity', () => {
  it('is one when the density does not move', () => {
    expect(exposureRatioForDensity('D7', 2, 2)).toBeCloseTo(1, 12);
  });

  it('needs more exposure for more density', () => {
    expect(exposureRatioForDensity('D7', 2, 2.5)).toBeGreaterThan(1);
  });

  it('needs less exposure for less density', () => {
    expect(exposureRatioForDensity('D7', 2.5, 2)).toBeLessThan(1);
  });

  it('inverts', () => {
    const up = exposureRatioForDensity('D7', 2, 2.5);
    const down = exposureRatioForDensity('D7', 2.5, 2);
    expect(up * down).toBeCloseTo(1, 12);
  });

  it('costs a fine film less than a fast one for the same half point', () => {
    const fine = exposureRatioForDensity('D4', 2, 2.5);
    const fast = exposureRatioForDensity('D8', 2, 2.5);
    expect(fine).toBeLessThan(fast);
  });

  it('multiplies the exposure by ten across a whole gradient', () => {
    const gradient = filmGradient('D7');
    expect(exposureRatioForDensity('D7', 2, 2 + gradient)).toBeCloseTo(10, 9);
  });

  it('refuses a density at or below zero', () => {
    expect(() => exposureRatioForDensity('D7', 0, 2)).toThrow(/greater than zero/);
    expect(() => exposureRatioForDensity('D7', 2, -1)).toThrow(/greater than zero/);
  });
});

describe('densityForExposureRatio', () => {
  it('leaves the density alone at a ratio of one', () => {
    expect(densityForExposureRatio('D7', 2, 1)).toBe(2);
  });

  it('inverts exposureRatioForDensity', () => {
    const ratio = exposureRatioForDensity('D5', 2, 2.8);
    expect(densityForExposureRatio('D5', 2, ratio)).toBeCloseTo(2.8, 3);
  });

  it('drops the density when the exposure is cut', () => {
    expect(densityForExposureRatio('D7', 2.5, 0.5)).toBeLessThan(2.5);
  });

  it('refuses a ratio at or below zero', () => {
    expect(() => densityForExposureRatio('D7', 2, 0)).toThrow(/greater than zero/);
  });
});

describe('aimAtDensity', () => {
  it('lifts a chart exposure onto a higher wanted density', () => {
    const out = aimAtDensity({
      exposure: 100,
      filmId: 'D7',
      chartDensity: 2,
      wantedDensity: 2.5,
    });
    expect(out).toBeGreaterThan(100);
    expect(out).toBeCloseTo(100 * exposureRatioForDensity('D7', 2, 2.5), 9);
  });

  it('leaves it alone when the job asks for the chart density', () => {
    const out = aimAtDensity({
      exposure: 100,
      filmId: 'D7',
      chartDensity: 2,
      wantedDensity: 2,
    });
    expect(out).toBeCloseTo(100, 9);
  });

  it('refuses a negative exposure', () => {
    expect(() =>
      aimAtDensity({ exposure: -1, filmId: 'D7', chartDensity: 2, wantedDensity: 2 })
    ).toThrow(/not be negative/);
  });
});

describe('densityIsReadable', () => {
  it('accepts the span the shop reads film in', () => {
    expect(readableDensityRange()).toEqual({ minimum: 1.5, maximum: 4 });
    expect(densityIsReadable(1.5)).toBe(true);
    expect(densityIsReadable(2.7)).toBe(true);
    expect(densityIsReadable(4)).toBe(true);
  });

  it('rejects film that is too light or too dark to read', () => {
    expect(densityIsReadable(1.4)).toBe(false);
    expect(densityIsReadable(4.1)).toBe(false);
    expect(densityIsReadable('2.7')).toBe(false);
  });
});

describe('reshootFactor', () => {
  it('asks for more time after a light film', () => {
    expect(
      reshootFactor({ filmId: 'D7', gotDensity: 1.6, wantedDensity: 2.2 })
    ).toBeGreaterThan(1);
  });

  it('asks for less time after a dark film', () => {
    expect(reshootFactor({ filmId: 'D7', gotDensity: 3.4, wantedDensity: 2.5 })).toBeLessThan(
      1
    );
  });

  it('asks for the same time when the film landed on target', () => {
    expect(reshootFactor({ filmId: 'D7', gotDensity: 2.5, wantedDensity: 2.5 })).toBe(1);
  });
});
