import { describe, it, expect } from 'vitest';
import {
  imageSeparationIn,
  offsetForSeparation,
  separationWindowIn,
  planEllipse,
  offsetIsAcceptable,
} from '../../src/geom/ellipse.js';

const bore = { odIn: 2.375, sourceToObjectIn: 12 };

describe('imageSeparationIn and offsetForSeparation', () => {
  it('separates nothing when the source sits on the weld plane', () => {
    expect(imageSeparationIn({ ...bore, offsetIn: 0 })).toBe(0);
  });

  it('grows the separation as the source is pushed back', () => {
    const near = imageSeparationIn({ ...bore, offsetIn: 1 });
    const far = imageSeparationIn({ ...bore, offsetIn: 3 });
    expect(far).toBeCloseTo(near * 3, 9);
  });

  it('is the offset scaled by diameter over standoff', () => {
    expect(imageSeparationIn({ ...bore, offsetIn: 6 })).toBeCloseTo(1.1875, 9);
  });

  it('inverts', () => {
    const offset = offsetForSeparation({ ...bore, separationIn: 0.75 });
    expect(imageSeparationIn({ ...bore, offsetIn: offset })).toBeCloseTo(0.75, 9);
  });

  it('needs a longer push on a smaller pipe for the same separation', () => {
    const small = offsetForSeparation({ odIn: 1.05, sourceToObjectIn: 12, separationIn: 0.75 });
    const large = offsetForSeparation({ odIn: 3.5, sourceToObjectIn: 12, separationIn: 0.75 });
    expect(small).toBeGreaterThan(large);
  });

  it('refuses a negative offset or separation', () => {
    expect(() => imageSeparationIn({ ...bore, offsetIn: -1 })).toThrow(/not be below 0/);
    expect(() => offsetForSeparation({ ...bore, separationIn: -1 })).toThrow(/not be below 0/);
  });

  it('refuses a standoff at zero', () => {
    expect(() => imageSeparationIn({ odIn: 2.375, sourceToObjectIn: 0, offsetIn: 1 })).toThrow(
      /greater than 0/
    );
  });
});

describe('separationWindowIn', () => {
  it('runs from one weld width to three', () => {
    expect(separationWindowIn(0.25)).toEqual({ minimumIn: 0.25, maximumIn: 0.75 });
  });

  it('refuses a weld width at zero', () => {
    expect(() => separationWindowIn(0)).toThrow(/greater than 0/);
  });
});

describe('planEllipse', () => {
  const plan = planEllipse({
    odIn: 2.375,
    wallIn: 0.218,
    sourceToObjectIn: 12,
    weldWidthIn: 0.25,
    filmLengthIn: 4.5,
  });

  it('aims at the middle of the window', () => {
    expect(plan.separationIn).toBeCloseTo(0.5, 3);
    expect(plan.windowIn).toEqual({ minimumIn: 0.25, maximumIn: 0.75 });
  });

  it('works out the offset that lands there', () => {
    expect(plan.offsetIn).toBeCloseTo(2.526, 2);
  });

  it('checks the film is long enough for both images', () => {
    expect(plan.filmNeededIn).toBeCloseTo(1, 3);
    expect(plan.fitsOnFilm).toBe(true);
    expect(plan.usable).toBe(true);
  });

  it('gives up when the film cannot hold both images', () => {
    const tight = planEllipse({
      odIn: 2.375,
      wallIn: 0.218,
      sourceToObjectIn: 12,
      weldWidthIn: 0.5,
      filmLengthIn: 1.2,
    });
    expect(tight.fitsOnFilm).toBe(false);
    expect(tight.usable).toBe(false);
  });

  it('is not usable on a pipe that is past small bore', () => {
    const big = planEllipse({
      odIn: 8.625,
      wallIn: 0.322,
      sourceToObjectIn: 12,
      weldWidthIn: 0.25,
      filmLengthIn: 14,
    });
    expect(big.smallBore).toBe(false);
    expect(big.usable).toBe(false);
  });

  it('refuses a film length at zero', () => {
    expect(() =>
      planEllipse({
        odIn: 2.375,
        wallIn: 0.218,
        sourceToObjectIn: 12,
        weldWidthIn: 0.25,
        filmLengthIn: 0,
      })
    ).toThrow(/film length/);
  });
});

describe('offsetIsAcceptable', () => {
  it('accepts an offset that lands inside the window', () => {
    const offset = offsetForSeparation({ ...bore, separationIn: 0.5 });
    expect(offsetIsAcceptable({ ...bore, offsetIn: offset, weldWidthIn: 0.25 })).toBe(true);
  });

  it('rejects an offset that leaves the images on top of each other', () => {
    expect(offsetIsAcceptable({ ...bore, offsetIn: 0.5, weldWidthIn: 0.25 })).toBe(false);
  });

  it('rejects an offset that throws the near wall too far', () => {
    expect(offsetIsAcceptable({ ...bore, offsetIn: 20, weldWidthIn: 0.25 })).toBe(false);
  });

  it('accepts both edges of the window', () => {
    const low = offsetForSeparation({ ...bore, separationIn: 0.25 });
    const high = offsetForSeparation({ ...bore, separationIn: 0.75 });
    expect(offsetIsAcceptable({ ...bore, offsetIn: low, weldWidthIn: 0.25 })).toBe(true);
    expect(offsetIsAcceptable({ ...bore, offsetIn: high, weldWidthIn: 0.25 })).toBe(true);
  });
});
