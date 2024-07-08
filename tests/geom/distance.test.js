import { describe, it, expect } from 'vitest';
import {
  sourceToFilm,
  sourceToObject,
  objectToFilm,
  magnification,
  projectedLength,
  actualLength,
  resolveDistances,
} from '../../src/geom/distance.js';

describe('the three distances', () => {
  it('adds up', () => {
    expect(sourceToFilm({ sourceToObjectIn: 24, objectToFilmIn: 6 })).toBe(30);
    expect(sourceToObject({ sourceToFilmIn: 30, objectToFilmIn: 6 })).toBe(24);
    expect(objectToFilm({ sourceToFilmIn: 30, sourceToObjectIn: 24 })).toBe(6);
  });

  it('allows the object to lie on the film', () => {
    expect(sourceToFilm({ sourceToObjectIn: 24, objectToFilmIn: 0 })).toBe(24);
    expect(objectToFilm({ sourceToFilmIn: 24, sourceToObjectIn: 24 })).toBe(0);
  });

  it('refuses an object at or behind the film', () => {
    expect(() => sourceToObject({ sourceToFilmIn: 24, objectToFilmIn: 24 })).toThrow(
      /at or behind the film/
    );
    expect(() => objectToFilm({ sourceToFilmIn: 24, sourceToObjectIn: 30 })).toThrow(
      /behind the film/
    );
  });

  it('refuses a distance at or below zero', () => {
    expect(() => sourceToFilm({ sourceToObjectIn: 0, objectToFilmIn: 6 })).toThrow(
      /greater than 0/
    );
    expect(() => sourceToFilm({ sourceToObjectIn: 24, objectToFilmIn: -1 })).toThrow(
      /not be below 0/
    );
  });

  it('refuses a distance that is not a number', () => {
    expect(() => sourceToFilm({ sourceToObjectIn: '24', objectToFilmIn: 6 })).toThrow(
      /finite number/
    );
  });
});

describe('magnification', () => {
  it('is one when the object lies on the film', () => {
    expect(magnification({ sourceToObjectIn: 24, objectToFilmIn: 0 })).toBe(1);
  });

  it('grows with the standoff', () => {
    expect(magnification({ sourceToObjectIn: 24, objectToFilmIn: 6 })).toBeCloseTo(1.25, 9);
    expect(magnification({ sourceToObjectIn: 24, objectToFilmIn: 12 })).toBeCloseTo(1.5, 9);
  });

  it('falls back towards one as the source is cranked out', () => {
    const near = magnification({ sourceToObjectIn: 12, objectToFilmIn: 6 });
    const far = magnification({ sourceToObjectIn: 120, objectToFilmIn: 6 });
    expect(far).toBeLessThan(near);
    expect(far).toBeGreaterThan(1);
  });
});

describe('projectedLength and actualLength', () => {
  it('invert each other', () => {
    const factor = magnification({ sourceToObjectIn: 24, objectToFilmIn: 6 });
    expect(actualLength(projectedLength(0.25, factor), factor)).toBeCloseTo(0.25, 12);
  });

  it('leaves a length alone at magnification one', () => {
    expect(projectedLength(0.25, 1)).toBe(0.25);
    expect(actualLength(0.25, 1)).toBe(0.25);
  });

  it('makes an indication read longer than it is', () => {
    expect(projectedLength(0.25, 1.25)).toBeCloseTo(0.3125, 9);
    expect(actualLength(0.3125, 1.25)).toBeCloseTo(0.25, 9);
  });

  it('refuses a magnification below one', () => {
    expect(() => projectedLength(0.25, 0.9)).toThrow(/not be below 1/);
    expect(() => actualLength(0.25, 0)).toThrow(/not be below 1/);
  });

  it('refuses a negative length', () => {
    expect(() => projectedLength(-0.25, 1)).toThrow(/not be below 0/);
  });
});

describe('resolveDistances', () => {
  it('completes the picture from source to object and standoff', () => {
    expect(resolveDistances({ sourceToObjectIn: 24, objectToFilmIn: 6 })).toEqual({
      sourceToFilmIn: 30,
      sourceToObjectIn: 24,
      objectToFilmIn: 6,
      magnification: 1.25,
    });
  });

  it('completes it from source to film and standoff', () => {
    expect(resolveDistances({ sourceToFilmIn: 30, objectToFilmIn: 6 })).toEqual({
      sourceToFilmIn: 30,
      sourceToObjectIn: 24,
      objectToFilmIn: 6,
      magnification: 1.25,
    });
  });

  it('completes it from the two source distances', () => {
    expect(resolveDistances({ sourceToFilmIn: 30, sourceToObjectIn: 24 })).toEqual({
      sourceToFilmIn: 30,
      sourceToObjectIn: 24,
      objectToFilmIn: 6,
      magnification: 1.25,
    });
  });

  it('accepts all three when they agree', () => {
    const out = resolveDistances({
      sourceToFilmIn: 30,
      sourceToObjectIn: 24,
      objectToFilmIn: 6,
    });
    expect(out.magnification).toBe(1.25);
  });

  it('refuses all three when they do not agree', () => {
    expect(() =>
      resolveDistances({ sourceToFilmIn: 30, sourceToObjectIn: 24, objectToFilmIn: 8 })
    ).toThrow(/do not agree/);
  });

  it('refuses fewer than two', () => {
    expect(() => resolveDistances({ sourceToFilmIn: 30 })).toThrow(/two of the three/);
    expect(() => resolveDistances({})).toThrow(/two of the three/);
  });

  it('treats a null the same as a missing value', () => {
    expect(() => resolveDistances({ sourceToFilmIn: 30, objectToFilmIn: null })).toThrow(
      /two of the three/
    );
  });
});
