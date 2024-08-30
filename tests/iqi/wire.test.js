import { describe, it, expect } from 'vitest';
import {
  wireNumbers,
  wireSetLetters,
  wireDiameterIn,
  wireSet,
  setsForWire,
  preferredSetForWire,
  hasWire,
  wireForDiameterIn,
  wireSetRecord,
} from '../../src/iqi/wire.js';

describe('the wire ladder', () => {
  it('runs from one to twenty one', () => {
    expect(wireNumbers()).toHaveLength(21);
    expect(wireNumbers()[0]).toBe(1);
    expect(wireNumbers()[20]).toBe(21);
  });

  it('gets thicker with every step', () => {
    const diameters = wireNumbers().map(wireDiameterIn);
    expect(diameters).toEqual([...diameters].sort((a, b) => a - b));
  });

  it('quotes the published diameters', () => {
    expect(wireDiameterIn(1)).toBeCloseTo(0.0032, 6);
    expect(wireDiameterIn(6)).toBeCloseTo(0.01, 6);
    expect(wireDiameterIn(11)).toBeCloseTo(0.032, 6);
    expect(wireDiameterIn(21)).toBeCloseTo(0.32, 6);
  });

  it('refuses a wire that does not exist', () => {
    expect(() => wireDiameterIn(22)).toThrow(/no wire numbered 22/);
    expect(() => wireDiameterIn('6')).toThrow(/named by its number/);
  });

  it('answers hasWire without throwing', () => {
    expect(hasWire(6)).toBe(true);
    expect(hasWire(22)).toBe(false);
    expect(hasWire('6')).toBe(false);
  });
});

describe('the four sets', () => {
  it('names them A to D', () => {
    expect(wireSetLetters()).toEqual(['A', 'B', 'C', 'D']);
  });

  it('holds six wires each', () => {
    for (const letter of wireSetLetters()) {
      expect(wireSet(letter)).toHaveLength(6);
    }
  });

  it('overlaps by exactly one wire at each join', () => {
    expect(wireSet('A')[5]).toBe(wireSet('B')[0]);
    expect(wireSet('B')[5]).toBe(wireSet('C')[0]);
    expect(wireSet('C')[5]).toBe(wireSet('D')[0]);
  });

  it('covers the whole ladder between them', () => {
    const covered = new Set(wireSetLetters().flatMap((letter) => wireSet(letter)));
    expect([...covered].sort((a, b) => a - b)).toEqual(wireNumbers());
  });

  it('takes a lower case letter', () => {
    expect(wireSet('b')).toEqual(wireSet('B'));
  });

  it('refuses a set that does not exist', () => {
    expect(() => wireSet('E')).toThrow(/no wire set E/);
  });

  it('hands back a copy so a caller cannot edit the set', () => {
    const set = wireSet('A');
    set.push(99);
    expect(wireSet('A')).toHaveLength(6);
  });
});

describe('setsForWire', () => {
  it('names one set for a wire in the middle of a set', () => {
    expect(setsForWire(3)).toEqual(['A']);
    expect(setsForWire(9)).toEqual(['B']);
  });

  it('names both sets for a join wire', () => {
    expect(setsForWire(6)).toEqual(['A', 'B']);
    expect(setsForWire(11)).toEqual(['B', 'C']);
    expect(setsForWire(16)).toEqual(['C', 'D']);
  });

  it('draws the finer set at a join', () => {
    expect(preferredSetForWire(6)).toBe('A');
    expect(preferredSetForWire(16)).toBe('C');
    expect(preferredSetForWire(9)).toBe('B');
  });
});

describe('wireForDiameterIn', () => {
  it('finds the wire at an exact diameter', () => {
    expect(wireForDiameterIn(0.01)).toBe(6);
    expect(wireForDiameterIn(0.032)).toBe(11);
  });

  it('takes the next wire up when the call falls between two', () => {
    expect(wireForDiameterIn(0.011)).toBe(7);
    expect(wireForDiameterIn(0.0001)).toBe(1);
  });

  it('refuses a call past the coarsest wire', () => {
    expect(() => wireForDiameterIn(0.5)).toThrow(/no wire reaches/);
  });

  it('refuses a diameter that is not positive', () => {
    expect(() => wireForDiameterIn(0)).toThrow(/positive number/);
  });
});

describe('wireSetRecord', () => {
  it('describes a set for the technique sheet', () => {
    const record = wireSetRecord('B');
    expect(record.letter).toBe('B');
    expect(record.wires).toEqual([6, 7, 8, 9, 10, 11]);
    expect(record.finestIn).toBeCloseTo(0.01, 6);
    expect(record.coarsestIn).toBeCloseTo(0.032, 6);
    expect(record.diametersIn).toHaveLength(6);
  });

  it('keeps the diameters in step with the wires', () => {
    for (const letter of ['A', 'B', 'C', 'D']) {
      const record = wireSetRecord(letter);
      record.wires.forEach((wire, index) => {
        expect(record.diametersIn[index]).toBe(wireDiameterIn(wire));
      });
    }
  });
});
