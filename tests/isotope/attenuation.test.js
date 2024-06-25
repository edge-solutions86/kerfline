import { describe, it, expect } from 'vitest';
import {
  shieldMaterials,
  halfValueLayerMm,
  halfValueLayerIn,
  tenthValueLayerMm,
  transmission,
  shieldForFactor,
  shieldForReading,
  wallInHalfValueLayers,
} from '../../src/isotope/attenuation.js';

describe('the half value layer table', () => {
  it('lists the materials it carries', () => {
    expect(shieldMaterials()).toEqual(['concrete', 'lead', 'steel']);
  });

  it('quotes the published millimetre values', () => {
    expect(halfValueLayerMm('IR192', 'lead')).toBeCloseTo(4.8, 2);
    expect(halfValueLayerMm('IR192', 'steel')).toBeCloseTo(12.7, 2);
    expect(halfValueLayerMm('CO60', 'lead')).toBeCloseTo(12.5, 2);
    expect(halfValueLayerMm('SE75', 'lead')).toBeCloseTo(2.0, 2);
  });

  it('gets harder as the source gets harder', () => {
    const lead = ['TM170', 'YB169', 'SE75', 'IR192', 'CO60'].map((id) =>
      halfValueLayerMm(id, 'lead')
    );
    const ascending = [...lead].sort((a, b) => a - b);
    expect(lead).toEqual(ascending);
  });

  it('converts to inches', () => {
    expect(halfValueLayerIn('IR192', 'steel')).toBeCloseTo(0.5, 2);
  });

  it('names the isotope and the material when it has no row', () => {
    expect(() => halfValueLayerMm('IR192', 'water')).toThrow(/no half value layer/);
    expect(() => halfValueLayerMm('IR192', 'water')).toThrow(/in water/);
  });

  it('refuses an isotope outside the catalog', () => {
    expect(() => halfValueLayerMm('CS137', 'lead')).toThrow(/no isotope CS137/);
  });
});

describe('tenthValueLayerMm', () => {
  it('is the half value layer times log two of ten', () => {
    const hvl = halfValueLayerMm('IR192', 'lead');
    expect(tenthValueLayerMm('IR192', 'lead')).toBeCloseTo(hvl * Math.log2(10), 9);
    expect(tenthValueLayerMm('IR192', 'lead')).toBeCloseTo(15.945, 3);
  });

  it('cuts the beam to a tenth', () => {
    const tvl = tenthValueLayerMm('CO60', 'lead');
    expect(transmission('CO60', 'lead', tvl)).toBeCloseTo(0.1, 9);
  });
});

describe('transmission', () => {
  it('passes everything through no shield', () => {
    expect(transmission('IR192', 'lead', 0)).toBe(1);
  });

  it('halves through one half value layer', () => {
    expect(transmission('IR192', 'lead', 4.8)).toBeCloseTo(0.5, 9);
    expect(transmission('IR192', 'lead', 9.6)).toBeCloseTo(0.25, 9);
  });

  it('falls off as the shield thickens', () => {
    const thin = transmission('IR192', 'steel', 10);
    const thick = transmission('IR192', 'steel', 40);
    expect(thick).toBeLessThan(thin);
  });

  it('refuses a negative thickness', () => {
    expect(() => transmission('IR192', 'lead', -1)).toThrow(/must not be negative/);
    expect(() => transmission('IR192', 'lead', 'thick')).toThrow(/finite number/);
  });
});

describe('shieldForFactor', () => {
  it('needs one half value layer to halve the beam', () => {
    expect(shieldForFactor('IR192', 'lead', 2)).toBeCloseTo(4.8, 9);
  });

  it('needs none to leave the beam alone', () => {
    expect(shieldForFactor('IR192', 'lead', 1)).toBeCloseTo(0, 12);
  });

  it('inverts transmission', () => {
    const mm = shieldForFactor('CO60', 'concrete', 100);
    expect(transmission('CO60', 'concrete', mm)).toBeCloseTo(0.01, 9);
  });

  it('refuses a factor below one', () => {
    expect(() => shieldForFactor('IR192', 'lead', 0.5)).toThrow(/below one is not a shield/);
  });
});

describe('shieldForReading', () => {
  it('takes a survey reading down to a target', () => {
    const mm = shieldForReading('IR192', 'lead', 400, 2);
    expect(transmission('IR192', 'lead', mm) * 400).toBeCloseTo(2, 9);
  });

  it('needs no shield when the reading is already at the target', () => {
    expect(shieldForReading('IR192', 'lead', 2, 2)).toBeCloseTo(0, 12);
  });

  it('refuses a target above the unshielded reading', () => {
    expect(() => shieldForReading('IR192', 'lead', 2, 400)).toThrow(/target reading is above/);
  });

  it('refuses a reading at or below zero', () => {
    expect(() => shieldForReading('IR192', 'lead', 0, 0)).toThrow(/above zero/);
    expect(() => shieldForReading('IR192', 'lead', 400, -1)).toThrow(/above zero/);
  });
});

describe('wallInHalfValueLayers', () => {
  it('counts the wall in half value layers of steel', () => {
    expect(wallInHalfValueLayers('IR192', 0.5)).toBeCloseTo(1, 1);
    expect(wallInHalfValueLayers('IR192', 1.0)).toBeCloseTo(2, 1);
  });

  it('makes a thick wall look worse on iridium than on cobalt', () => {
    expect(wallInHalfValueLayers('IR192', 2)).toBeGreaterThan(wallInHalfValueLayers('CO60', 2));
  });

  it('refuses a wall at or below zero', () => {
    expect(() => wallInHalfValueLayers('IR192', 0)).toThrow(/above zero/);
  });
});
