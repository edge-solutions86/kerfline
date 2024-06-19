import { describe, it, expect } from 'vitest';
import {
  isotopes,
  isotopeIds,
  isotope,
  normaliseId,
  hasIsotope,
  withinSteelRange,
  sourcesForThickness,
} from '../../src/isotope/catalog.js';

describe('the catalog', () => {
  it('carries the five licensed gamma sources', () => {
    expect(isotopeIds()).toEqual(['IR192', 'SE75', 'CO60', 'YB169', 'TM170']);
  });

  it('quotes the half lives off the certificates', () => {
    expect(isotope('IR192').halfLifeDays).toBeCloseTo(73.83, 2);
    expect(isotope('SE75').halfLifeDays).toBeCloseTo(119.78, 2);
    expect(isotope('CO60').halfLifeDays).toBeCloseTo(1925.3, 1);
    expect(isotope('YB169').halfLifeDays).toBeCloseTo(32.02, 2);
    expect(isotope('TM170').halfLifeDays).toBeCloseTo(128.6, 1);
  });

  it('quotes the gamma ray constants in R per hour at a metre per curie', () => {
    expect(isotope('IR192').gammaFactor).toBeCloseTo(0.48, 3);
    expect(isotope('CO60').gammaFactor).toBeCloseTo(1.32, 3);
    expect(isotope('SE75').gammaFactor).toBeCloseTo(0.203, 3);
  });

  it('carries the two cobalt lines and nothing else', () => {
    expect(isotope('CO60').energiesMeV).toEqual([1.173, 1.332]);
  });

  it('orders every energy list ascending', () => {
    for (const source of isotopes()) {
      const sorted = [...source.energiesMeV].sort((a, b) => a - b);
      expect(source.energiesMeV).toEqual(sorted);
    }
  });

  it('keeps the mean energy inside the quoted lines', () => {
    for (const source of isotopes()) {
      const lines = source.energiesMeV;
      expect(source.meanEnergyMeV).toBeGreaterThanOrEqual(lines[0] * 0.9);
      expect(source.meanEnergyMeV).toBeLessThanOrEqual(lines[lines.length - 1] * 1.1);
    }
  });

  it('gives every source a steel band that goes upwards', () => {
    for (const source of isotopes()) {
      expect(source.steelRangeIn.max).toBeGreaterThan(source.steelRangeIn.min);
      expect(source.steelRangeIn.min).toBeGreaterThan(0);
    }
  });

  it('freezes a record so a technique cannot edit the catalog', () => {
    const source = isotope('IR192');
    expect(() => {
      source.halfLifeDays = 1;
    }).toThrow();
    expect(() => {
      source.energiesMeV.push(9);
    }).toThrow();
  });
});

describe('lookup', () => {
  it('finds a source by its id', () => {
    expect(isotope('IR192').label).toBe('Ir-192');
  });

  it('finds a source by the label written on the form', () => {
    expect(isotope('Ir-192').id).toBe('IR192');
    expect(isotope('ir 192').id).toBe('IR192');
    expect(isotope('ir_192').id).toBe('IR192');
  });

  it('names what it knows when it cannot find one', () => {
    expect(() => isotope('CS137')).toThrow(/no isotope CS137/);
    try {
      isotope('CS137');
    } catch (err) {
      expect(err.details.known).toContain('IR192');
    }
  });

  it('refuses a non string', () => {
    expect(() => isotope(192)).toThrow(/named by a string/);
  });

  it('answers hasIsotope without throwing', () => {
    expect(hasIsotope('co-60')).toBe(true);
    expect(hasIsotope('CS137')).toBe(false);
    expect(hasIsotope(null)).toBe(false);
  });

  it('normalises the way the paperwork is written', () => {
    expect(normaliseId('ir-192')).toBe('IR192');
    expect(normaliseId('Se 75')).toBe('SE75');
  });
});

describe('the shop steel bands', () => {
  it('puts a quarter inch wall inside iridium and outside cobalt', () => {
    expect(withinSteelRange('IR192', 0.25)).toBe(true);
    expect(withinSteelRange('CO60', 0.25)).toBe(false);
  });

  it('includes both ends of the band', () => {
    expect(withinSteelRange('SE75', 0.2)).toBe(true);
    expect(withinSteelRange('SE75', 1.1)).toBe(true);
    expect(withinSteelRange('SE75', 1.1001)).toBe(false);
  });

  it('refuses a thickness that is not a number', () => {
    expect(() => withinSteelRange('IR192', '0.25')).toThrow(/finite number/);
  });

  it('lists the sources that cover a wall, hardest first', () => {
    const ids = sourcesForThickness(0.28).map((s) => s.id);
    expect(ids).toEqual(['IR192', 'SE75', 'YB169']);
  });

  it('puts cobalt alone on a thick wall', () => {
    expect(sourcesForThickness(4).map((s) => s.id)).toEqual(['CO60']);
  });

  it('hands back nothing when no licensed source covers the wall', () => {
    expect(sourcesForThickness(12)).toEqual([]);
  });
});
