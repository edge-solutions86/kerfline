import { describe, it, expect } from 'vitest';
import {
  sequence,
  parseSequence,
  isSequence,
  nextInSequence,
  compareIds,
  sortIds,
  requireStencil,
  isStencil,
  slug,
} from '../../src/util/ids.js';

describe('sequence', () => {
  it('pads to the stated width', () => {
    expect(sequence('J', 12)).toBe('J-012');
    expect(sequence('J', 12, 5)).toBe('J-00012');
  });

  it('upper cases the prefix', () => {
    expect(sequence('rt', 4, 2)).toBe('RT-04');
  });

  it('does not cut a number that is wider than the pad', () => {
    expect(sequence('J', 12345, 3)).toBe('J-12345');
  });

  it('refuses a prefix that is not one to four letters', () => {
    expect(() => sequence('', 1)).toThrow(/one to four letters/);
    expect(() => sequence('TOOLONG', 1)).toThrow(/one to four letters/);
    expect(() => sequence('J1', 1)).toThrow(/one to four letters/);
  });

  it('refuses a bad number or width', () => {
    expect(() => sequence('J', -1)).toThrow(/whole number/);
    expect(() => sequence('J', 1.5)).toThrow(/whole number/);
    expect(() => sequence('J', 1, 0)).toThrow(/between one and six/);
    expect(() => sequence('J', 1, 7)).toThrow(/between one and six/);
  });
});

describe('parseSequence', () => {
  it('reads the form the formatter writes', () => {
    expect(parseSequence('J-012')).toEqual({ prefix: 'J', number: 12 });
  });

  it('accepts a space or nothing where the dash should be', () => {
    expect(parseSequence('J 12')).toEqual({ prefix: 'J', number: 12 });
    expect(parseSequence('J12')).toEqual({ prefix: 'J', number: 12 });
  });

  it('accepts lower case and surrounding space off a scanned form', () => {
    expect(parseSequence('  rt-4 ')).toEqual({ prefix: 'RT', number: 4 });
  });

  it('refuses what it cannot read', () => {
    expect(() => parseSequence('joint twelve')).toThrow(/cannot read/);
    expect(() => parseSequence('12')).toThrow(/cannot read/);
    expect(() => parseSequence('')).toThrow(/cannot read/);
    expect(() => parseSequence(12)).toThrow(/must be a string/);
  });

  it('answers isSequence without throwing', () => {
    expect(isSequence('J-012')).toBe(true);
    expect(isSequence('nope')).toBe(false);
  });
});

describe('nextInSequence', () => {
  it('keeps the written width', () => {
    expect(nextInSequence('J-012')).toBe('J-013');
    expect(nextInSequence('J-099')).toBe('J-100');
  });

  it('grows past the width rather than wrapping', () => {
    expect(nextInSequence('J-999')).toBe('J-1000');
  });

  it('keeps an unpadded series unpadded', () => {
    expect(nextInSequence('J-7')).toBe('J-8');
  });
});

describe('compareIds and sortIds', () => {
  it('puts J-2 before J-10', () => {
    expect(compareIds('J-2', 'J-10')).toBeLessThan(0);
    expect(compareIds('J-10', 'J-2')).toBeGreaterThan(0);
  });

  it('is zero for the same id', () => {
    expect(compareIds('J-10', 'J-10')).toBe(0);
  });

  it('ignores case', () => {
    expect(compareIds('j-10', 'J-10')).toBe(0);
  });

  it('sorts a register the way it reads', () => {
    const ids = ['J-10', 'J-2', 'J-1', 'J-20', 'J-3'];
    expect(sortIds(ids)).toEqual(['J-1', 'J-2', 'J-3', 'J-10', 'J-20']);
  });

  it('leaves the caller array alone', () => {
    const ids = ['J-10', 'J-2'];
    sortIds(ids);
    expect(ids).toEqual(['J-10', 'J-2']);
  });

  it('sorts across differing prefixes', () => {
    expect(sortIds(['RT-2', 'J-10'])).toEqual(['J-10', 'RT-2']);
  });

  it('puts a shorter id first when one is a prefix of the other', () => {
    expect(sortIds(['J-1A', 'J-1'])).toEqual(['J-1', 'J-1A']);
  });

  it('puts a number ahead of a letter at the same position', () => {
    expect(compareIds('1A', 'A1')).toBeLessThan(0);
  });

  it('refuses anything that is not an array', () => {
    expect(() => sortIds('J-1')).toThrow(/must be an array/);
  });
});

describe('requireStencil', () => {
  it('accepts the stamps the shop issues', () => {
    expect(requireStencil('A17')).toBe('A17');
    expect(requireStencil('42')).toBe('42');
    expect(requireStencil(' bw9 ')).toBe('BW9');
  });

  it('refuses a stamp that cannot be struck', () => {
    expect(() => requireStencil('BW-9')).toThrow(/one to six letters or digits/);
    expect(() => requireStencil('')).toThrow(/one to six letters or digits/);
    expect(() => requireStencil('TOOLONGX')).toThrow(/one to six letters or digits/);
    expect(() => requireStencil(42)).toThrow(/one to six letters or digits/);
  });

  it('answers isStencil without throwing', () => {
    expect(isStencil('A17')).toBe(true);
    expect(isStencil('BW-9')).toBe(false);
  });
});

describe('slug', () => {
  it('reduces a name to a key', () => {
    expect(slug('Line 12 Spool 4')).toBe('line-12-spool-4');
  });

  it('collapses runs and trims the ends', () => {
    expect(slug('  --Line   12--  ')).toBe('line-12');
  });

  it('refuses a name with nothing usable in it', () => {
    expect(() => slug('---')).toThrow(/empty name/);
    expect(() => slug('')).toThrow(/empty name/);
    expect(() => slug(null)).toThrow(/empty name/);
  });
});
