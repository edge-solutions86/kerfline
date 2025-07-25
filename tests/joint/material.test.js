import { describe, it, expect } from 'vitest';
import {
  materialIds,
  material,
  iqiGroups,
  equivalentSteelIn,
  fromEquivalentSteelIn,
  indicatorAcceptable,
  indicatorsFor,
  governingMaterial,
} from '../../src/joint/material.js';

describe('the material list', () => {
  it('carries what this shop shoots', () => {
    expect(materialIds()).toContain('CARBON_STEEL');
    expect(materialIds()).toContain('ALUMINIUM');
    expect(materialIds()).toContain('STAINLESS');
  });

  it('takes carbon steel as the unit', () => {
    expect(material('CARBON_STEEL').equivalence).toBe(1);
  });

  it('finds a material however it was written', () => {
    expect(material('carbon steel').id).toBe('CARBON_STEEL');
    expect(material('low-alloy').id).toBe('LOW_ALLOY');
    expect(material(' STAINLESS ').id).toBe('STAINLESS');
  });

  it('carries a P number for each', () => {
    expect(material('CARBON_STEEL').pNumber).toBe('P1');
    expect(material('STAINLESS').pNumber).toBe('P8');
  });

  it('refuses a material it does not carry', () => {
    expect(() => material('LEAD')).toThrow(/no material LEAD/);
    expect(() => material(8)).toThrow(/named by a string/);
  });

  it('names the indicator groups', () => {
    expect(iqiGroups()).toEqual(['01', '02', '03']);
  });
});

describe('equivalentSteelIn', () => {
  it('leaves steel alone', () => {
    expect(equivalentSteelIn('CARBON_STEEL', 0.5)).toBe(0.5);
  });

  it('makes an inch of aluminium behave like a fifth of an inch of steel', () => {
    expect(equivalentSteelIn('ALUMINIUM', 1)).toBeCloseTo(0.18, 4);
  });

  it('makes the harder alloys behave like more steel', () => {
    expect(equivalentSteelIn('INCONEL', 1)).toBeGreaterThan(1);
    expect(equivalentSteelIn('COPPER', 1)).toBeGreaterThan(equivalentSteelIn('INCONEL', 1));
  });

  it('makes stainless slightly harder than carbon steel', () => {
    expect(equivalentSteelIn('STAINLESS', 1)).toBeGreaterThan(1);
    expect(equivalentSteelIn('STAINLESS', 1)).toBeLessThan(1.2);
  });

  it('inverts', () => {
    const steel = equivalentSteelIn('ALUMINIUM', 1.4);
    expect(fromEquivalentSteelIn('ALUMINIUM', steel)).toBeCloseTo(1.4, 3);
  });

  it('refuses a thickness at zero', () => {
    expect(() => equivalentSteelIn('CARBON_STEEL', 0)).toThrow(/thickness/);
    expect(() => fromEquivalentSteelIn('CARBON_STEEL', 0)).toThrow(/steel thickness/);
  });
});

describe('indicatorAcceptable', () => {
  it('accepts an indicator of the same group', () => {
    const check = indicatorAcceptable({
      weldMaterial: 'CARBON_STEEL',
      indicatorMaterial: 'STAINLESS',
    });
    expect(check.sameGroup).toBe(true);
    expect(check.acceptable).toBe(true);
  });

  it('refuses a steel indicator on an aluminium weld', () => {
    const check = indicatorAcceptable({
      weldMaterial: 'ALUMINIUM',
      indicatorMaterial: 'CARBON_STEEL',
    });
    expect(check.acceptable).toBe(false);
    expect(check.sameGroup).toBe(false);
  });

  it('refuses an aluminium indicator on a steel weld', () => {
    const check = indicatorAcceptable({
      weldMaterial: 'CARBON_STEEL',
      indicatorMaterial: 'ALUMINIUM',
    });
    expect(check.acceptable).toBe(false);
  });

  it('lets a steel indicator serve on a nickel weld but not the reverse', () => {
    expect(
      indicatorAcceptable({ weldMaterial: 'INCONEL', indicatorMaterial: 'CARBON_STEEL' })
        .acceptable
    ).toBe(true);
    expect(
      indicatorAcceptable({ weldMaterial: 'CARBON_STEEL', indicatorMaterial: 'INCONEL' })
        .acceptable
    ).toBe(false);
  });

  it('reports both groups so a rejection can be argued about', () => {
    const check = indicatorAcceptable({
      weldMaterial: 'ALUMINIUM',
      indicatorMaterial: 'CARBON_STEEL',
    });
    expect(check.weldGroup).toBe('01');
    expect(check.indicatorGroup).toBe('02');
  });
});

describe('indicatorsFor', () => {
  it('lists what will serve on a steel weld', () => {
    const serving = indicatorsFor('CARBON_STEEL');
    expect(serving).toContain('CARBON_STEEL');
    expect(serving).toContain('STAINLESS');
    expect(serving).not.toContain('ALUMINIUM');
  });

  it('lists more for a nickel weld, which accepts steel too', () => {
    expect(indicatorsFor('INCONEL').length).toBeGreaterThan(indicatorsFor('ALUMINIUM').length);
  });

  it('keeps the light materials to themselves', () => {
    expect(indicatorsFor('ALUMINIUM')).toEqual(['ALUMINIUM', 'TITANIUM']);
  });
});

describe('governingMaterial', () => {
  it('takes the harder of two welded together', () => {
    expect(governingMaterial('CARBON_STEEL', 'INCONEL').id).toBe('INCONEL');
    expect(governingMaterial('ALUMINIUM', 'CARBON_STEEL').id).toBe('CARBON_STEEL');
  });

  it('does not care which way round they are given', () => {
    expect(governingMaterial('INCONEL', 'CARBON_STEEL').id).toBe(
      governingMaterial('CARBON_STEEL', 'INCONEL').id
    );
  });

  it('takes either when they are the same', () => {
    expect(governingMaterial('CARBON_STEEL', 'LOW_ALLOY').equivalence).toBe(1);
  });
});
