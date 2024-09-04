import { describe, it, expect } from 'vitest';
import {
  placements,
  shimRequired,
  shimThicknessIn,
  placementPlan,
  sideFromMarking,
  markingAgrees,
} from '../../src/iqi/placement.js';

describe('placements', () => {
  it('names the two places an indicator goes', () => {
    expect(placements()).toEqual(['on_weld', 'beside_weld']);
  });
});

describe('shimRequired', () => {
  it('wants a shim under a plaque laid beside the weld', () => {
    expect(shimRequired({ iqiType: 'hole', placement: 'beside_weld' })).toBe(true);
  });

  it('wants none under a plaque laid on the weld', () => {
    expect(shimRequired({ iqiType: 'hole', placement: 'on_weld' })).toBe(false);
  });

  it('never wants one for a wire indicator', () => {
    expect(shimRequired({ iqiType: 'wire', placement: 'beside_weld' })).toBe(false);
    expect(shimRequired({ iqiType: 'wire', placement: 'on_weld' })).toBe(false);
  });

  it('refuses a placement it does not know', () => {
    expect(() => shimRequired({ iqiType: 'hole', placement: 'on_backing' })).toThrow(
      /on_weld or beside_weld/
    );
  });

  it('refuses an indicator type it has no rule for', () => {
    expect(() => shimRequired({ iqiType: 'step', placement: 'on_weld' })).toThrow(
      /no placement rule/
    );
  });
});

describe('shimThicknessIn', () => {
  it('is nothing when the weld is dressed flush', () => {
    expect(shimThicknessIn({})).toBe(0);
  });

  it('makes up one face of reinforcement', () => {
    expect(shimThicknessIn({ reinforcementIn: 0.0625 })).toBeCloseTo(0.0625, 6);
  });

  it('makes up both faces when the root is crowned too', () => {
    const shim = shimThicknessIn({ reinforcementIn: 0.0625, secondFaceIn: 0.0625 });
    expect(shim).toBeCloseTo(0.125, 6);
  });

  it('refuses a negative face', () => {
    expect(() => shimThicknessIn({ reinforcementIn: -0.01 })).toThrow(/not be below 0/);
  });
});

describe('placementPlan', () => {
  it('plans a plaque on the weld with no shim and no marking', () => {
    expect(placementPlan({ iqiType: 'hole' })).toEqual({
      iqiType: 'hole',
      placement: 'on_weld',
      side: 'source',
      shimRequired: false,
      shimThicknessIn: 0,
      leadMarking: null,
      marksRequired: [],
    });
  });

  it('plans a plaque beside the weld with a shim', () => {
    const plan = placementPlan({
      iqiType: 'hole',
      placement: 'beside_weld',
      reinforcementIn: 0.0625,
    });
    expect(plan.shimRequired).toBe(true);
    expect(plan.shimThicknessIn).toBeCloseTo(0.0625, 6);
  });

  it('leaves the shim at nothing when none is needed even with a crown', () => {
    const plan = placementPlan({ iqiType: 'wire', reinforcementIn: 0.0625 });
    expect(plan.shimRequired).toBe(false);
    expect(plan.shimThicknessIn).toBe(0);
  });

  it('calls for the lead F on the film side', () => {
    const plan = placementPlan({ iqiType: 'hole', side: 'film' });
    expect(plan.leadMarking).toBe('F');
    expect(plan.marksRequired).toEqual(['F']);
  });

  it('refuses a side that is not one of the two', () => {
    expect(() => placementPlan({ iqiType: 'hole', side: 'back' })).toThrow(/source or film/);
  });
});

describe('sideFromMarking', () => {
  it('reads an F as the film side', () => {
    expect(sideFromMarking('F')).toBe('film');
    expect(sideFromMarking(' f ')).toBe('film');
  });

  it('reads nothing at all as the source side', () => {
    expect(sideFromMarking(null)).toBe('source');
    expect(sideFromMarking(undefined)).toBe('source');
    expect(sideFromMarking('')).toBe('source');
  });

  it('refuses a marking it cannot read rather than guessing', () => {
    expect(() => sideFromMarking('S')).toThrow(/cannot read S/);
    expect(() => sideFromMarking('FF')).toThrow(/cannot read FF/);
  });
});

describe('markingAgrees', () => {
  it('agrees when the film is marked the way the sheet says', () => {
    expect(markingAgrees({ marking: 'F', side: 'film' })).toBe(true);
    expect(markingAgrees({ marking: null, side: 'source' })).toBe(true);
  });

  it('catches a film side shot with no F on it', () => {
    expect(markingAgrees({ marking: null, side: 'film' })).toBe(false);
  });

  it('catches an F on a shot the sheet calls source side', () => {
    expect(markingAgrees({ marking: 'F', side: 'source' })).toBe(false);
  });
});
