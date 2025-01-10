import { describe, it, expect } from 'vitest';
import {
  highRadiationRate,
  restrictedAreaRate,
  boundaryDistancesFt,
  barricadeDistanceFt,
  isInsideBoundary,
  fitsInSpace,
  largestActivityFor,
  combinedBoundaryFt,
} from '../../src/safety/boundary.js';
import { doseRateAt } from '../../src/safety/doserate.js';

const camera = { isotopeId: 'IR192', activityCi: 40 };

describe('the two rates', () => {
  it('are the regulatory numbers', () => {
    expect(highRadiationRate()).toBe(100);
    expect(restrictedAreaRate()).toBe(2);
  });
});

describe('boundaryDistancesFt', () => {
  const bounds = boundaryDistancesFt(camera);

  it('puts the restricted boundary much further out than the high one', () => {
    expect(bounds.restrictedFt).toBeGreaterThan(bounds.highRadiationFt);
  });

  it('lands where the rate is what it says', () => {
    expect(doseRateAt({ ...camera, distanceFt: bounds.restrictedFt })).toBeCloseTo(2, 1);
    expect(doseRateAt({ ...camera, distanceFt: bounds.highRadiationFt })).toBeCloseTo(100, 0);
  });

  it('puts a forty curie iridium camera boundary a long way out', () => {
    expect(bounds.restrictedFt).toBeGreaterThan(200);
    expect(bounds.restrictedFt).toBeLessThan(400);
  });

  it('grows with the square root of the activity', () => {
    const small = boundaryDistancesFt({ isotopeId: 'IR192', activityCi: 10 });
    const large = boundaryDistancesFt({ isotopeId: 'IR192', activityCi: 40 });
    expect(large.restrictedFt).toBeCloseTo(small.restrictedFt * 2, 0);
  });
});

describe('barricadeDistanceFt', () => {
  it('is further out than the bare calculation', () => {
    const bare = boundaryDistancesFt(camera).restrictedFt;
    expect(barricadeDistanceFt(camera)).toBeGreaterThan(bare);
  });

  it('takes a margin', () => {
    const bare = boundaryDistancesFt(camera).restrictedFt;
    expect(barricadeDistanceFt({ ...camera, margin: 1 })).toBeCloseTo(bare, 0);
    expect(barricadeDistanceFt({ ...camera, margin: 2 })).toBeCloseTo(bare * 2, 0);
  });

  it('refuses a margin below one', () => {
    expect(() => barricadeDistanceFt({ ...camera, margin: 0.9 })).toThrow(/not be below 1/);
  });
});

describe('isInsideBoundary', () => {
  it('is inside close to the source and outside far from it', () => {
    expect(isInsideBoundary({ ...camera, distanceFt: 10 })).toBe(true);
    expect(isInsideBoundary({ ...camera, distanceFt: 5000 })).toBe(false);
  });

  it('reads the high radiation boundary separately', () => {
    const bounds = boundaryDistancesFt(camera);
    const between = (bounds.highRadiationFt + bounds.restrictedFt) / 2;
    expect(isInsideBoundary({ ...camera, distanceFt: between, boundary: 'restricted' })).toBe(
      true
    );
    expect(isInsideBoundary({ ...camera, distanceFt: between, boundary: 'high' })).toBe(false);
  });
});

describe('fitsInSpace', () => {
  it('fits when there is room', () => {
    const check = fitsInSpace({ ...camera, availableFt: 1000 });
    expect(check.fits).toBe(true);
    expect(check.shortFt).toBe(0);
  });

  it('does not fit in a tight bay and says by how much', () => {
    const check = fitsInSpace({ ...camera, availableFt: 100 });
    expect(check.fits).toBe(false);
    expect(check.shortFt).toBeGreaterThan(0);
    expect(check.neededFt).toBeCloseTo(check.availableFt + check.shortFt, 1);
  });

  it('fits a smaller source where a big one will not', () => {
    const big = fitsInSpace({ isotopeId: 'IR192', activityCi: 100, availableFt: 200 });
    const small = fitsInSpace({ isotopeId: 'IR192', activityCi: 2, availableFt: 200 });
    expect(big.fits).toBe(false);
    expect(small.fits).toBe(true);
  });

  it('refuses a space at zero', () => {
    expect(() => fitsInSpace({ ...camera, availableFt: 0 })).toThrow(/available space/);
  });
});

describe('largestActivityFor', () => {
  it('finds an activity that fits the space', () => {
    const activityCi = largestActivityFor({ isotopeId: 'IR192', availableFt: 200 });
    const check = fitsInSpace({ isotopeId: 'IR192', activityCi, availableFt: 200 });
    expect(check.fits).toBe(true);
    expect(check.neededFt).toBeCloseTo(200, 0);
  });

  it('allows more activity in more space, as the square', () => {
    const near = largestActivityFor({ isotopeId: 'IR192', availableFt: 100 });
    const far = largestActivityFor({ isotopeId: 'IR192', availableFt: 200 });
    expect(far).toBeCloseTo(near * 4, 0);
  });

  it('allows more of a soft source than a hard one', () => {
    const selenium = largestActivityFor({ isotopeId: 'SE75', availableFt: 200 });
    const cobalt = largestActivityFor({ isotopeId: 'CO60', availableFt: 200 });
    expect(selenium).toBeGreaterThan(cobalt);
  });
});

describe('combinedBoundaryFt', () => {
  it('is further out than either source on its own', () => {
    const one = barricadeDistanceFt({ isotopeId: 'IR192', activityCi: 40, margin: 1 });
    const both = combinedBoundaryFt(
      [
        { isotopeId: 'IR192', activityCi: 40 },
        { isotopeId: 'IR192', activityCi: 40 },
      ],
      { margin: 1 }
    );
    expect(both).toBeGreaterThan(one);
  });

  it('lands where the rates add to the restricted limit', () => {
    const sources = [
      { isotopeId: 'IR192', activityCi: 40 },
      { isotopeId: 'CO60', activityCi: 10 },
    ];
    const distanceFt = combinedBoundaryFt(sources, { margin: 1 });
    const total = sources.reduce(
      (sum, source) => sum + doseRateAt({ ...source, distanceFt }),
      0
    );
    expect(total).toBeCloseTo(2, 1);
  });

  it('matches the single source boundary when there is only one', () => {
    const single = barricadeDistanceFt({ isotopeId: 'IR192', activityCi: 40, margin: 1 });
    const combined = combinedBoundaryFt([{ isotopeId: 'IR192', activityCi: 40 }], {
      margin: 1,
    });
    expect(combined).toBeCloseTo(single, 0);
  });

  it('refuses an empty list', () => {
    expect(() => combinedBoundaryFt([])).toThrow(/at least one source/);
  });
});
