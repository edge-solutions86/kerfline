import { describe, it, expect } from 'vitest';
import {
  doseRateAt,
  doseRateAtMetres,
  distanceForRateFt,
  shieldedRateAt,
  doseForExposure,
  doseForExposureMicroSievert,
  minutesToDose,
  compareSources,
} from '../../src/safety/doserate.js';

const camera = { isotopeId: 'IR192', activityCi: 40 };

describe('doseRateAt', () => {
  it('follows the inverse square', () => {
    const near = doseRateAt({ ...camera, distanceFt: 10 });
    const far = doseRateAt({ ...camera, distanceFt: 20 });
    expect(far).toBeCloseTo(near / 4, 2);
  });

  it('reads about eleven times hotter at a foot than at a metre', () => {
    const atFoot = doseRateAt({ ...camera, distanceFt: 1 });
    const atMetre = doseRateAtMetres({ ...camera, distanceM: 1 });
    expect(atFoot / atMetre).toBeCloseTo(10.764, 2);
  });

  it('gives the catalog constant back at one metre', () => {
    // 0.48 R/h per curie at a metre, forty curies, in milliroentgen.
    expect(doseRateAtMetres({ ...camera, distanceM: 1 })).toBeCloseTo(19200, 0);
  });

  it('scales with the activity', () => {
    const half = doseRateAt({ isotopeId: 'IR192', activityCi: 20, distanceFt: 10 });
    const full = doseRateAt({ ...camera, distanceFt: 10 });
    expect(full).toBeCloseTo(half * 2, 2);
  });

  it('reads cobalt hotter than iridium at the same activity', () => {
    const iridium = doseRateAt({ isotopeId: 'IR192', activityCi: 10, distanceFt: 10 });
    const cobalt = doseRateAt({ isotopeId: 'CO60', activityCi: 10, distanceFt: 10 });
    expect(cobalt).toBeGreaterThan(iridium);
  });

  it('refuses a distance or activity at zero', () => {
    expect(() => doseRateAt({ ...camera, distanceFt: 0 })).toThrow(/greater than 0/);
    expect(() => doseRateAt({ isotopeId: 'IR192', activityCi: 0, distanceFt: 10 })).toThrow(
      /source activity/
    );
  });
});

describe('distanceForRateFt', () => {
  it('inverts doseRateAt', () => {
    const rate = doseRateAt({ ...camera, distanceFt: 25 });
    expect(distanceForRateFt({ ...camera, rateMrPerHour: rate })).toBeCloseTo(25, 1);
  });

  it('wants more distance for a lower rate', () => {
    const close = distanceForRateFt({ ...camera, rateMrPerHour: 100 });
    const far = distanceForRateFt({ ...camera, rateMrPerHour: 2 });
    expect(far).toBeGreaterThan(close);
  });

  it('refuses a rate at zero', () => {
    expect(() => distanceForRateFt({ ...camera, rateMrPerHour: 0 })).toThrow(/dose rate/);
  });
});

describe('shieldedRateAt', () => {
  it('is the bare rate with no shield', () => {
    const bare = doseRateAt({ ...camera, distanceFt: 20 });
    const shielded = shieldedRateAt({
      ...camera,
      distanceFt: 20,
      material: 'lead',
      thicknessMm: 0,
    });
    expect(shielded).toBeCloseTo(bare, 2);
  });

  it('halves through one half value layer', () => {
    const bare = doseRateAt({ ...camera, distanceFt: 20 });
    const shielded = shieldedRateAt({
      ...camera,
      distanceFt: 20,
      material: 'lead',
      thicknessMm: 4.8,
    });
    expect(shielded).toBeCloseTo(bare / 2, 1);
  });

  it('falls away fast through real shielding', () => {
    const shielded = shieldedRateAt({
      ...camera,
      distanceFt: 20,
      material: 'concrete',
      thicknessMm: 300,
    });
    expect(shielded).toBeLessThan(doseRateAt({ ...camera, distanceFt: 20 }) / 100);
  });

  it('refuses a negative shield', () => {
    expect(() =>
      shieldedRateAt({ ...camera, distanceFt: 20, material: 'lead', thicknessMm: -1 })
    ).toThrow(/not be negative|not be below 0/);
  });
});

describe('doseForExposure', () => {
  it('is the rate over the fraction of an hour worked', () => {
    const rate = doseRateAt({ ...camera, distanceFt: 50 });
    expect(doseForExposure({ ...camera, distanceFt: 50, minutes: 60 })).toBeCloseTo(rate, 2);
    expect(doseForExposure({ ...camera, distanceFt: 50, minutes: 30 })).toBeCloseTo(
      rate / 2,
      2
    );
  });

  it('is nothing for no time at all', () => {
    expect(doseForExposure({ ...camera, distanceFt: 50, minutes: 0 })).toBe(0);
  });

  it('converts to the unit a dosimeter reads', () => {
    const mr = doseForExposure({ ...camera, distanceFt: 50, minutes: 10 });
    const usv = doseForExposureMicroSievert({ ...camera, distanceFt: 50, minutes: 10 });
    expect(usv).toBeCloseTo(mr * 10, 1);
  });
});

describe('minutesToDose', () => {
  it('allows longer further away', () => {
    const close = minutesToDose({ ...camera, distanceFt: 20, limitMr: 100 });
    const far = minutesToDose({ ...camera, distanceFt: 200, limitMr: 100 });
    expect(far).toBeGreaterThan(close);
  });

  it('scales with the limit', () => {
    const small = minutesToDose({ ...camera, distanceFt: 100, limitMr: 50 });
    const large = minutesToDose({ ...camera, distanceFt: 100, limitMr: 100 });
    expect(large).toBeCloseTo(small * 2, 1);
  });

  it('refuses a limit at zero', () => {
    expect(() => minutesToDose({ ...camera, distanceFt: 100, limitMr: 0 })).toThrow(
      /dose limit/
    );
  });
});

describe('compareSources', () => {
  it('ranks the sources coolest first at the same activity', () => {
    const ranked = compareSources({
      isotopeIds: ['CO60', 'IR192', 'SE75'],
      activityCi: 20,
      distanceFt: 30,
    });
    expect(ranked.map((row) => row.isotope)).toEqual(['SE75', 'IR192', 'CO60']);
  });

  it('reports a rate for each', () => {
    const ranked = compareSources({ isotopeIds: ['IR192'], activityCi: 20, distanceFt: 30 });
    expect(ranked[0].rateMrPerHour).toBeGreaterThan(0);
  });
});
