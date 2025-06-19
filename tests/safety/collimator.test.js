import { describe, it, expect } from 'vitest';
import {
  collimatorTypes,
  collimator,
  inBeam,
  factorAt,
  collimatedRateAt,
  collimatedBoundaryFt,
  smallestFitting,
  quietFraction,
} from '../../src/safety/collimator.js';
import { doseRateAt } from '../../src/safety/doserate.js';
import { barricadeDistanceFt } from '../../src/safety/boundary.js';

const camera = { isotopeId: 'IR192', activityCi: 40 };

describe('the sleeves', () => {
  it('lists what the shop owns', () => {
    expect(collimatorTypes()).toEqual(['NONE', 'PARTIAL', 'DIRECTIONAL']);
  });

  it('gets narrower and heavier as it gets better', () => {
    const sleeves = collimatorTypes().map((name) => collimator(name));
    for (let i = 1; i < sleeves.length; i += 1) {
      expect(sleeves[i].beamDegrees).toBeLessThan(sleeves[i - 1].beamDegrees);
      expect(sleeves[i].outsideFactor).toBeLessThan(sleeves[i - 1].outsideFactor);
      expect(sleeves[i].weightLb).toBeGreaterThan(sleeves[i - 1].weightLb);
    }
  });

  it('refuses a sleeve nobody owns', () => {
    expect(() => collimator('PINHOLE')).toThrow(/no collimator called PINHOLE/);
  });
});

describe('inBeam', () => {
  it('is in the beam straight ahead', () => {
    expect(inBeam('DIRECTIONAL', 0)).toBe(true);
  });

  it('is in the beam at the edge of the cone', () => {
    expect(inBeam('DIRECTIONAL', 30)).toBe(true);
    expect(inBeam('DIRECTIONAL', 31)).toBe(false);
  });

  it('does not care which side of the axis', () => {
    expect(inBeam('DIRECTIONAL', -30)).toBe(true);
    expect(inBeam('DIRECTIONAL', -31)).toBe(false);
  });

  it('is behind the camera at a hundred and eighty', () => {
    expect(inBeam('DIRECTIONAL', 180)).toBe(false);
    expect(inBeam('PARTIAL', 180)).toBe(false);
  });

  it('wraps a bearing past a full turn', () => {
    expect(inBeam('DIRECTIONAL', 360)).toBe(true);
    // 390 is 30 off the axis, which is still the edge of the cone.
    expect(inBeam('DIRECTIONAL', 390)).toBe(true);
    expect(inBeam('DIRECTIONAL', 400)).toBe(false);
  });

  it('is always in the beam with no sleeve on', () => {
    for (const bearing of [0, 90, 180, 270]) {
      expect(inBeam('NONE', bearing)).toBe(true);
    }
  });
});

describe('factorAt', () => {
  it('does nothing in the beam', () => {
    expect(factorAt('DIRECTIONAL', 0)).toBe(1);
  });

  it('cuts the rate hard outside it', () => {
    expect(factorAt('DIRECTIONAL', 180)).toBeCloseTo(0.03, 4);
    expect(factorAt('PARTIAL', 180)).toBeCloseTo(0.1, 4);
  });

  it('does nothing anywhere with a bare source', () => {
    expect(factorAt('NONE', 180)).toBe(1);
  });
});

describe('collimatedRateAt', () => {
  it('matches the bare rate in the beam', () => {
    const bare = doseRateAt({ ...camera, distanceFt: 50 });
    const inside = collimatedRateAt({
      ...camera,
      distanceFt: 50,
      collimator: 'DIRECTIONAL',
      offsetDegrees: 0,
    });
    expect(inside).toBeCloseTo(bare, 2);
  });

  it('is far lower behind the camera', () => {
    const behind = collimatedRateAt({
      ...camera,
      distanceFt: 50,
      collimator: 'DIRECTIONAL',
      offsetDegrees: 180,
    });
    const bare = doseRateAt({ ...camera, distanceFt: 50 });
    expect(behind).toBeLessThan(bare / 20);
  });
});

describe('collimatedBoundaryFt', () => {
  it('leaves the beam side boundary exactly where it was', () => {
    const bare = barricadeDistanceFt(camera);
    const bounds = collimatedBoundaryFt({ ...camera, collimator: 'DIRECTIONAL' });
    expect(bounds.inBeamFt).toBeCloseTo(bare, 1);
  });

  it('pulls the boundary in behind the sleeve', () => {
    const bounds = collimatedBoundaryFt({ ...camera, collimator: 'DIRECTIONAL' });
    expect(bounds.outsideBeamFt).toBeLessThan(bounds.inBeamFt);
  });

  it('pulls it in by the square root of the attenuation', () => {
    const bounds = collimatedBoundaryFt({ ...camera, collimator: 'PARTIAL' });
    expect(bounds.outsideBeamFt).toBeCloseTo(bounds.inBeamFt * Math.sqrt(0.1), 0);
  });

  it('changes nothing at all with no sleeve on', () => {
    const bounds = collimatedBoundaryFt({ ...camera, collimator: 'NONE' });
    expect(bounds.outsideBeamFt).toBeCloseTo(bounds.inBeamFt, 1);
  });
});

describe('smallestFitting', () => {
  it('takes the lightest sleeve that will do', () => {
    const fitted = smallestFitting({ ...camera, availableFt: 130 });
    expect(fitted).not.toBe(null);
    expect(fitted.collimator).toBe('PARTIAL');
    expect(fitted.outsideBeamFt).toBeLessThanOrEqual(130);
  });

  it('reaches for the heavy one when the lighter sleeve is ten feet short', () => {
    // The partial sleeve lands just past 120 ft on this source, so a bay of
    // 120 is the case where a crew has to carry the heavy one.
    expect(smallestFitting({ ...camera, availableFt: 120 }).collimator).toBe('DIRECTIONAL');
    expect(smallestFitting({ ...camera, availableFt: 70 }).collimator).toBe('DIRECTIONAL');
  });

  it('needs nothing at all in the open', () => {
    expect(smallestFitting({ ...camera, availableFt: 1000 }).collimator).toBe('NONE');
  });

  it('gives up when no sleeve is enough', () => {
    expect(smallestFitting({ ...camera, availableFt: 10 })).toBe(null);
  });

  it('reports the weight the crew has to carry', () => {
    expect(smallestFitting({ ...camera, availableFt: 70 }).weightLb).toBeGreaterThan(0);
  });

  it('refuses a space at zero', () => {
    expect(() => smallestFitting({ ...camera, availableFt: 0 })).toThrow(/available space/);
  });
});

describe('quietFraction', () => {
  it('says how much of the sphere a sleeve quietens', () => {
    expect(quietFraction('DIRECTIONAL')).toBeCloseTo(1 - 60 / 360, 4);
    expect(quietFraction('PARTIAL')).toBeCloseTo(1 - 120 / 360, 4);
  });

  it('is nothing with no sleeve on', () => {
    expect(quietFraction('NONE')).toBe(0);
  });
});
