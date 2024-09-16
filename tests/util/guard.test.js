import { describe, it, expect } from 'vitest';
import {
  requireNumber,
  requireAbove,
  requireAtLeast,
  requirePositive,
  requireWhole,
  requireOneOf,
  requireArray,
} from '../../src/util/guard.js';

describe('requireNumber', () => {
  it('hands the value straight back', () => {
    expect(requireNumber(0.28, 'wall')).toBe(0.28);
    expect(requireNumber(-3, 'offset')).toBe(-3);
  });

  it('refuses anything that is not a finite number, naming the field', () => {
    expect(() => requireNumber('0.28', 'wall')).toThrow(/wall must be a finite number/);
    expect(() => requireNumber(NaN, 'wall')).toThrow(/finite number/);
    expect(() => requireNumber(Infinity, 'wall')).toThrow(/finite number/);
    expect(() => requireNumber(null, 'wall')).toThrow(/finite number/);
  });

  it('carries the offending value in the details', () => {
    try {
      requireNumber('x', 'wall');
    } catch (err) {
      expect(err.details.got).toBe('x');
      expect(err.code).toBe('BAD_INPUT');
    }
  });
});

describe('requireAbove', () => {
  it('accepts a value past the bound', () => {
    expect(requireAbove(1, 0, 'distance')).toBe(1);
  });

  it('refuses the bound itself and names it', () => {
    expect(() => requireAbove(0, 0, 'distance')).toThrow(/distance must be greater than 0/);
    expect(() => requireAbove(2, 2, 'ratio')).toThrow(/greater than 2/);
  });

  it('checks the value is a number first', () => {
    expect(() => requireAbove('1', 0, 'distance')).toThrow(/finite number/);
  });
});

describe('requireAtLeast', () => {
  it('accepts the bound itself', () => {
    expect(requireAtLeast(0, 0, 'standoff')).toBe(0);
  });

  it('refuses anything under it', () => {
    expect(() => requireAtLeast(-0.1, 0, 'standoff')).toThrow(/standoff must not be below 0/);
  });
});

describe('requirePositive', () => {
  it('says zero in words rather than as a bound', () => {
    expect(() => requirePositive(0, 'activity')).toThrow(/activity must be greater than zero/);
    expect(() => requirePositive(-1, 'activity')).toThrow(/greater than zero/);
  });

  it('accepts anything above zero', () => {
    expect(requirePositive(0.001, 'activity')).toBe(0.001);
  });

  it('says something different from requireAbove at the same bound', () => {
    let fromPositive = '';
    let fromAbove = '';
    try {
      requirePositive(0, 'x');
    } catch (err) {
      fromPositive = err.message;
    }
    try {
      requireAbove(0, 0, 'x');
    } catch (err) {
      fromAbove = err.message;
    }
    expect(fromPositive).not.toBe(fromAbove);
  });
});

describe('requireWhole', () => {
  it('accepts an integer including a negative one', () => {
    expect(requireWhole(3, 'count')).toBe(3);
    expect(requireWhole(-3, 'count')).toBe(-3);
  });

  it('refuses a fraction or a non number', () => {
    expect(() => requireWhole(1.5, 'count')).toThrow(/count must be a whole number/);
    expect(() => requireWhole('3', 'count')).toThrow(/whole number/);
  });
});

describe('requireOneOf', () => {
  it('accepts a member', () => {
    expect(requireOneOf('film', ['source', 'film'], 'side')).toBe('film');
  });

  it('lists the options when it refuses', () => {
    expect(() => requireOneOf('back', ['source', 'film'], 'side')).toThrow(
      /side must be one of source or film/
    );
  });

  it('carries the options in the details', () => {
    try {
      requireOneOf('back', ['source', 'film'], 'side');
    } catch (err) {
      expect(err.details.known).toEqual(['source', 'film']);
    }
  });
});

describe('requireArray', () => {
  it('accepts an array including an empty one', () => {
    expect(requireArray([], 'readings')).toEqual([]);
  });

  it('refuses anything else', () => {
    expect(() => requireArray('abc', 'readings')).toThrow(/readings must be an array/);
    expect(() => requireArray(null, 'readings')).toThrow(/must be an array/);
  });
});
