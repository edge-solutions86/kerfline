import { describe, it, expect } from 'vitest';
import {
  chartReference,
  chartedIsotopes,
  chartPoints,
  chartRange,
  chartCovers,
  chartExposure,
  chartThickness,
  impliedHalfValueLayerIn,
} from '../../src/exposure/chart.js';

describe('the charts on file', () => {
  it('names the reference conditions they were shot at', () => {
    expect(chartReference()).toEqual({
      distanceIn: 24,
      film: 'D7',
      density: 2,
      screens: 'LEAD_STANDARD',
    });
  });

  it('holds a chart for the three working sources', () => {
    expect(chartedIsotopes()).toEqual(['IR192', 'SE75', 'CO60']);
  });

  it('climbs in both thickness and exposure', () => {
    for (const id of chartedIsotopes()) {
      const points = chartPoints(id);
      for (let i = 1; i < points.length; i += 1) {
        expect(points[i].thicknessIn).toBeGreaterThan(points[i - 1].thicknessIn);
        expect(points[i].curieMinutes).toBeGreaterThan(points[i - 1].curieMinutes);
      }
    }
  });

  it('stays inside the shop band for its source', () => {
    expect(chartRange('IR192')).toEqual({ minimumIn: 0.25, maximumIn: 2.5 });
    expect(chartRange('CO60')).toEqual({ minimumIn: 1.5, maximumIn: 9 });
  });

  it('has no chart for the soft sources', () => {
    expect(() => chartPoints('YB169')).toThrow(/no exposure chart/);
  });

  it('answers chartCovers without throwing', () => {
    expect(chartCovers('IR192', 1)).toBe(true);
    expect(chartCovers('IR192', 5)).toBe(false);
    expect(chartCovers('IR192', 'thick')).toBe(false);
  });
});

describe('chartExposure', () => {
  it('reads a charted point back exactly', () => {
    expect(chartExposure('IR192', 1)).toBeCloseTo(20, 6);
    expect(chartExposure('IR192', 2)).toBeCloseTo(125, 6);
    expect(chartExposure('CO60', 4)).toBeCloseTo(220, 6);
  });

  it('lands between the two points either side', () => {
    const middle = chartExposure('IR192', 1.125);
    expect(middle).toBeGreaterThan(20);
    expect(middle).toBeLessThan(32);
  });

  it('interpolates on the log scale rather than the linear one', () => {
    const log = chartExposure('IR192', 1.125);
    const linear = (20 + 32) / 2;
    expect(log).toBeLessThan(linear);
    expect(log).toBeCloseTo(Math.sqrt(20 * 32), 6);
  });

  it('climbs monotonically across the whole chart', () => {
    let previous = 0;
    for (let t = 0.25; t <= 2.5; t += 0.05) {
      const value = chartExposure('IR192', Number(t.toFixed(2)));
      expect(value).toBeGreaterThan(previous);
      previous = value;
    }
  });

  it('refuses a thickness off either end rather than extrapolating', () => {
    expect(() => chartExposure('IR192', 0.2)).toThrow(/off the IR192 chart/);
    expect(() => chartExposure('IR192', 3)).toThrow(/off the IR192 chart/);
  });

  it('refuses a thickness that is not a positive number', () => {
    expect(() => chartExposure('IR192', 0)).toThrow(/positive number/);
    expect(() => chartExposure('IR192', '1')).toThrow(/positive number/);
  });
});

describe('chartThickness', () => {
  it('inverts the chart at a charted point', () => {
    expect(chartThickness('IR192', 20)).toBeCloseTo(1, 3);
    expect(chartThickness('CO60', 220)).toBeCloseTo(4, 3);
  });

  it('round trips through chartExposure', () => {
    for (const thickness of [0.4, 1.1, 1.9, 2.4]) {
      const exposure = chartExposure('IR192', thickness);
      expect(chartThickness('IR192', exposure)).toBeCloseTo(thickness, 3);
    }
  });

  it('refuses an exposure off the chart', () => {
    expect(() => chartThickness('IR192', 1)).toThrow(/off the IR192 chart/);
    expect(() => chartThickness('IR192', 5000)).toThrow(/off the IR192 chart/);
  });

  it('refuses an exposure that is not positive', () => {
    expect(() => chartThickness('IR192', 0)).toThrow(/positive number/);
  });
});

describe('impliedHalfValueLayerIn', () => {
  it('lands near the published narrow beam figure for each source', () => {
    expect(impliedHalfValueLayerIn('IR192')).toBeGreaterThan(0.3);
    expect(impliedHalfValueLayerIn('IR192')).toBeLessThan(0.55);
    expect(impliedHalfValueLayerIn('CO60')).toBeGreaterThan(0.7);
    expect(impliedHalfValueLayerIn('CO60')).toBeLessThan(1.1);
  });

  it('is harder for cobalt than for iridium, which is the whole point', () => {
    expect(impliedHalfValueLayerIn('CO60')).toBeGreaterThan(impliedHalfValueLayerIn('IR192'));
    expect(impliedHalfValueLayerIn('IR192')).toBeGreaterThan(impliedHalfValueLayerIn('SE75'));
  });
});
