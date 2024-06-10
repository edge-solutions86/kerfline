import { describe, it, expect } from 'vitest';
import {
  dimensionOf,
  unitsFor,
  dimensions,
  convert,
  inches,
  curies,
  minutes,
  parseMeasure,
  normaliseUnit,
  formatMeasure,
} from '../../src/util/units.js';

describe('the unit registry', () => {
  it('places every unit in exactly one dimension', () => {
    const seen = new Map();
    for (const dim of dimensions()) {
      for (const unit of unitsFor(dim)) {
        expect(seen.has(unit)).toBe(false);
        seen.set(unit, dim);
      }
    }
    expect(seen.size).toBeGreaterThan(15);
  });

  it('names the dimension a unit belongs to', () => {
    expect(dimensionOf('in')).toBe('length');
    expect(dimensionOf('Ci')).toBe('activity');
    expect(dimensionOf('mR/h')).toBe('doseRate');
    expect(dimensionOf('min')).toBe('time');
  });

  it('answers null for a unit it does not carry', () => {
    expect(dimensionOf('furlong')).toBe(null);
    expect(dimensionOf('')).toBe(null);
  });

  it('refuses a dimension it does not know', () => {
    expect(() => unitsFor('luminance')).toThrow(/no such dimension/);
  });

  it('lists the dimensions', () => {
    expect(dimensions()).toEqual(['activity', 'doseRate', 'length', 'time']);
  });
});

describe('convert', () => {
  it('converts length both ways', () => {
    expect(convert(1, 'in', 'mm')).toBeCloseTo(25.4, 9);
    expect(convert(25.4, 'mm', 'in')).toBeCloseTo(1, 9);
    expect(convert(1, 'ft', 'in')).toBeCloseTo(12, 9);
    expect(convert(280, 'mil', 'in')).toBeCloseTo(0.28, 9);
  });

  it('converts activity across the metric prefixes', () => {
    expect(convert(1, 'Ci', 'Bq')).toBeCloseTo(3.7e10, 0);
    expect(convert(1, 'Ci', 'GBq')).toBeCloseTo(37, 9);
    expect(convert(100, 'Ci', 'TBq')).toBeCloseTo(3.7, 9);
    expect(convert(1000, 'mCi', 'Ci')).toBeCloseTo(1, 9);
  });

  it('converts dose rate', () => {
    expect(convert(1, 'R/h', 'mR/h')).toBeCloseTo(1000, 9);
    expect(convert(2, 'mR/h', 'uSv/h')).toBeCloseTo(20, 9);
    expect(convert(100, 'mR/h', 'mSv/h')).toBeCloseTo(1, 9);
  });

  it('converts time', () => {
    expect(convert(90, 's', 'min')).toBeCloseTo(1.5, 9);
    expect(convert(2, 'h', 'min')).toBeCloseTo(120, 9);
    expect(convert(1, 'd', 'h')).toBeCloseTo(24, 9);
  });

  it('is exact when the units match', () => {
    expect(convert(0.28, 'in', 'in')).toBe(0.28);
  });

  it('round trips without drifting', () => {
    const there = convert(0.432, 'in', 'mm');
    expect(convert(there, 'mm', 'in')).toBeCloseTo(0.432, 12);
  });

  it('refuses to cross dimensions', () => {
    expect(() => convert(1, 'in', 'Ci')).toThrow(/cannot convert in to Ci/);
  });

  it('refuses a unit it does not know, naming which side', () => {
    expect(() => convert(1, 'furlong', 'in')).toThrow(/unknown unit furlong/);
    expect(() => convert(1, 'in', 'furlong')).toThrow(/unknown unit furlong/);
  });

  it('refuses a value that is not a number', () => {
    expect(() => convert('1', 'in', 'mm')).toThrow(/finite number/);
  });
});

describe('the shorthand converters', () => {
  it('lands on the base unit of the dimension', () => {
    expect(inches(25.4, 'mm')).toBeCloseTo(1, 9);
    expect(curies(37, 'GBq')).toBeCloseTo(1, 9);
    expect(minutes(120, 's')).toBeCloseTo(2, 9);
  });
});

describe('parseMeasure', () => {
  it('reads a value with a space', () => {
    expect(parseMeasure('0.280 in')).toEqual({ value: 0.28, unit: 'in' });
  });

  it('reads a value without a space', () => {
    expect(parseMeasure('12.7mm')).toEqual({ value: 12.7, unit: 'mm' });
  });

  it('tolerates surrounding whitespace and a negative', () => {
    expect(parseMeasure('  -3 mm ')).toEqual({ value: -3, unit: 'mm' });
  });

  it('folds case when that is unambiguous', () => {
    expect(parseMeasure('0.5 IN').unit).toBe('in');
    expect(parseMeasure('40 CI').unit).toBe('Ci');
  });

  it('reads a slashed dose rate unit', () => {
    expect(parseMeasure('2 mR/h')).toEqual({ value: 2, unit: 'mR/h' });
  });

  it('refuses text it cannot read', () => {
    expect(() => parseMeasure('about a quarter inch')).toThrow(/cannot read measurement/);
    expect(() => parseMeasure('0.280')).toThrow(/cannot read measurement/);
    expect(() => parseMeasure('')).toThrow(/cannot read measurement/);
  });

  it('refuses a unit it does not carry', () => {
    expect(() => parseMeasure('3 furlong')).toThrow(/unknown unit furlong/);
  });

  it('refuses a non string', () => {
    expect(() => parseMeasure(0.28)).toThrow(/must be a string/);
  });
});

describe('normaliseUnit', () => {
  it('leaves an exact unit alone', () => {
    expect(normaliseUnit('mCi')).toBe('mCi');
    expect(normaliseUnit('MBq')).toBe('MBq');
  });

  it('folds a single case insensitive match', () => {
    expect(normaliseUnit('MM')).toBe('mm');
  });

  it('folds a prefixed unit whose case is the only thing carrying meaning', () => {
    expect(normaliseUnit('mbq')).toBe('MBq');
    expect(normaliseUnit('gbq')).toBe('GBq');
  });

  it('hands back an unknown unit unchanged', () => {
    expect(normaliseUnit('furlong')).toBe('furlong');
  });
});

describe('formatMeasure', () => {
  it('rounds and appends the unit', () => {
    expect(formatMeasure(0.2795, 'in')).toBe('0.28 in');
    expect(formatMeasure(25.4001, 'mm', 1)).toBe('25.4 mm');
  });

  it('refuses an unknown unit', () => {
    expect(() => formatMeasure(1, 'furlong')).toThrow(/unknown unit/);
  });
});
