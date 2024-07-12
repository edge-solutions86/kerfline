import { describe, it, expect } from 'vitest';
import {
  viewingTechniques,
  viewing,
  minimumExposures,
  exposureSeparationDegrees,
  penetratedThicknessIn,
  selectViewing,
  viewingPlan,
} from '../../src/geom/viewing.js';

describe('the technique table', () => {
  it('lists the five techniques the procedure allows', () => {
    expect(viewingTechniques()).toEqual([
      'SWSI_PANORAMIC',
      'SWSI_OFFSET',
      'DWSI',
      'DWDI_ELLIPTICAL',
      'DWDI_SUPERIMPOSED',
    ]);
  });

  it('carries a label and a wall count for each', () => {
    for (const name of viewingTechniques()) {
      const technique = viewing(name);
      expect(typeof technique.label).toBe('string');
      expect([1, 2]).toContain(technique.walls);
      expect(technique.minimumExposures).toBeGreaterThanOrEqual(1);
    }
  });

  it('refuses a technique nobody has heard of', () => {
    expect(() => viewing('TWSI')).toThrow(/no viewing technique TWSI/);
  });
});

describe('minimumExposures', () => {
  it('takes one shot on a panoramic', () => {
    expect(minimumExposures('SWSI_PANORAMIC')).toBe(1);
  });

  it('takes two on an elliptical and three when the walls superimpose', () => {
    expect(minimumExposures('DWDI_ELLIPTICAL')).toBe(2);
    expect(minimumExposures('DWDI_SUPERIMPOSED')).toBe(3);
  });

  it('takes three on a double wall single image', () => {
    expect(minimumExposures('DWSI')).toBe(3);
  });
});

describe('exposureSeparationDegrees', () => {
  it('puts the two elliptical shots at ninety degrees', () => {
    expect(exposureSeparationDegrees('DWDI_ELLIPTICAL')).toBe(90);
  });

  it('spreads three shots evenly at sixty degrees of weld each', () => {
    expect(exposureSeparationDegrees('DWDI_SUPERIMPOSED')).toBe(120);
    expect(exposureSeparationDegrees('DWSI')).toBe(120);
  });

  it('has nothing to separate on a single shot', () => {
    expect(exposureSeparationDegrees('SWSI_PANORAMIC')).toBe(0);
  });
});

describe('penetratedThicknessIn', () => {
  it('crosses one wall on a single wall shot', () => {
    expect(penetratedThicknessIn('SWSI_PANORAMIC', 0.28)).toBeCloseTo(0.28, 9);
  });

  it('crosses two walls on any double wall shot even when one is read', () => {
    expect(penetratedThicknessIn('DWSI', 0.28)).toBeCloseTo(0.56, 9);
    expect(penetratedThicknessIn('DWDI_ELLIPTICAL', 0.28)).toBeCloseTo(0.56, 9);
  });

  it('refuses a wall at or below zero', () => {
    expect(() => penetratedThicknessIn('DWSI', 0)).toThrow(/greater than 0/);
  });
});

describe('selectViewing', () => {
  it('shoots a panoramic when the source can get inside a big pipe', () => {
    expect(selectViewing({ odIn: 24, sourceCanEnter: true }).name).toBe('SWSI_PANORAMIC');
  });

  it('offsets the source inside a pipe too small to centre it', () => {
    expect(selectViewing({ odIn: 3, sourceCanEnter: true }).name).toBe('SWSI_OFFSET');
  });

  it('shoots double wall single image on anything over small bore', () => {
    expect(selectViewing({ odIn: 12.75 }).name).toBe('DWSI');
    expect(selectViewing({ odIn: 4.5 }).name).toBe('DWSI');
  });

  it('shoots an ellipse on small bore', () => {
    expect(selectViewing({ odIn: 2.375 }).name).toBe('DWDI_ELLIPTICAL');
    expect(selectViewing({ odIn: 3.5 }).name).toBe('DWDI_ELLIPTICAL');
  });

  it('falls back to superimposed when the images will not separate', () => {
    const chosen = selectViewing({ odIn: 2.375, canSeparateImages: false });
    expect(chosen.name).toBe('DWDI_SUPERIMPOSED');
  });

  it('ignores the separation question once the source is inside', () => {
    const chosen = selectViewing({ odIn: 24, sourceCanEnter: true, canSeparateImages: false });
    expect(chosen.name).toBe('SWSI_PANORAMIC');
  });

  it('puts the small bore boundary in the ellipse', () => {
    expect(selectViewing({ odIn: 3.5 }).name).toBe('DWDI_ELLIPTICAL');
    expect(selectViewing({ odIn: 3.5001 }).name).toBe('DWSI');
  });

  it('refuses a diameter at or below zero', () => {
    expect(() => selectViewing({ odIn: 0 })).toThrow(/greater than 0/);
  });
});

describe('viewingPlan', () => {
  it('carries the choice and everything it decided', () => {
    expect(viewingPlan({ odIn: 12.75, wallIn: 0.375 })).toEqual({
      technique: 'DWSI',
      label: 'double wall single image',
      minimumExposures: 3,
      separationDegrees: 120,
      penetratedThicknessIn: 0.75,
      readsBothWalls: false,
    });
  });

  it('reads both walls only on a double wall double image', () => {
    expect(viewingPlan({ odIn: 2.375, wallIn: 0.218 }).readsBothWalls).toBe(true);
    expect(viewingPlan({ odIn: 12.75, wallIn: 0.375 }).readsBothWalls).toBe(false);
  });

  it('halves the penetrated thickness once the source goes inside', () => {
    const outside = viewingPlan({ odIn: 12.75, wallIn: 0.375 });
    const inside = viewingPlan({ odIn: 12.75, wallIn: 0.375, sourceCanEnter: true });
    expect(inside.penetratedThicknessIn).toBeCloseTo(outside.penetratedThicknessIn / 2, 9);
  });
});
