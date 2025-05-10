import { describe, it, expect } from 'vitest';
import {
  weldPositionOf,
  filmPositionOf,
  markerFromClock,
  locateAll,
  samePlace,
  spanOf,
} from '../../src/indication/locate.js';
import { circumference } from '../../src/geom/pipe.js';
import { makeIndication } from '../../src/indication/measure.js';

const odIn = 6.625;
const around = circumference(odIn);

describe('weldPositionOf', () => {
  it('puts the marker itself at the marker', () => {
    const placed = weldPositionOf({ markerAtIn: 0, alongFilmIn: 0, odIn });
    expect(placed.alongWeldIn).toBe(0);
    expect(placed.clock).toBe('12:00');
  });

  it('walks forward along the weld', () => {
    const placed = weldPositionOf({ markerAtIn: 0, alongFilmIn: around / 4, odIn });
    expect(placed.clock).toBe('3:00');
  });

  it('divides the film distance by the magnification', () => {
    const contact = weldPositionOf({ markerAtIn: 0, alongFilmIn: 2, odIn });
    const magnified = weldPositionOf({
      markerAtIn: 0,
      alongFilmIn: 2,
      magnification: 2,
      odIn,
    });
    expect(magnified.alongWeldIn).toBeCloseTo(contact.alongWeldIn / 2, 4);
  });

  it('starts from where the marker was laid', () => {
    const placed = weldPositionOf({ markerAtIn: around / 4, alongFilmIn: 0, odIn });
    expect(placed.clock).toBe('3:00');
  });

  it('wraps round the top rather than running off', () => {
    const placed = weldPositionOf({ markerAtIn: around - 1, alongFilmIn: 2, odIn });
    expect(placed.alongWeldIn).toBeCloseTo(1, 4);
    expect(placed.wrapped).toBe(true);
  });

  it('reads just short of the top as a late clock position', () => {
    const placed = weldPositionOf({ markerAtIn: around - 0.2, alongFilmIn: 0, odIn });
    expect(placed.clock.startsWith('11:')).toBe(true);
  });

  it('refuses a magnification below one', () => {
    expect(() =>
      weldPositionOf({ markerAtIn: 0, alongFilmIn: 1, magnification: 0.5, odIn })
    ).toThrow(/not be below 1/);
  });

  it('refuses a negative distance', () => {
    expect(() => weldPositionOf({ markerAtIn: 0, alongFilmIn: -1, odIn })).toThrow(
      /not be below 0/
    );
  });
});

describe('filmPositionOf', () => {
  it('inverts weldPositionOf', () => {
    const alongFilmIn = 3;
    const placed = weldPositionOf({ markerAtIn: 1, alongFilmIn, magnification: 1.25, odIn });
    const back = filmPositionOf({
      markerAtIn: 1,
      alongWeldIn: placed.alongWeldIn,
      magnification: 1.25,
      odIn,
    });
    expect(back).toBeCloseTo(alongFilmIn, 3);
  });

  it('is nothing at the marker', () => {
    expect(filmPositionOf({ markerAtIn: 2, alongWeldIn: 2, odIn })).toBe(0);
  });

  it('multiplies by the magnification', () => {
    const contact = filmPositionOf({ markerAtIn: 0, alongWeldIn: 2, odIn });
    const magnified = filmPositionOf({ markerAtIn: 0, alongWeldIn: 2, magnification: 2, odIn });
    expect(magnified).toBeCloseTo(contact * 2, 4);
  });
});

describe('markerFromClock', () => {
  it('turns a clock reading into inches along the weld', () => {
    expect(markerFromClock('3:00', odIn)).toBeCloseTo(around / 4, 3);
    expect(markerFromClock('12:00', odIn)).toBe(0);
  });

  it('refuses a clock position it cannot read', () => {
    expect(() => markerFromClock('quarter past', odIn)).toThrow(/cannot read/);
  });
});

describe('locateAll', () => {
  const context = { markerAtIn: 0, odIn, magnification: 1 };

  it('places every indication on the weld', () => {
    const set = [
      makeIndication({ type: 'SLAG', measuredIn: 0.2, at: 2 }),
      makeIndication({ type: 'POROSITY', measuredIn: 0.05, at: 5 }),
    ];
    const placed = locateAll(set, context);
    expect(placed).toHaveLength(2);
    expect(placed[0].clock).toBeDefined();
    expect(placed[1].degrees).toBeGreaterThan(placed[0].degrees);
  });

  it('keeps everything the indication already carried', () => {
    const set = [makeIndication({ type: 'SLAG', measuredIn: 0.2, at: 2, film: '1' })];
    const placed = locateAll(set, context);
    expect(placed[0].type).toBe('SLAG');
    expect(placed[0].film).toBe('1');
    expect(placed[0].sizeIn).toBe(0.2);
  });

  it('takes the magnification off the indication when it carries one', () => {
    const set = [makeIndication({ type: 'SLAG', measuredIn: 0.2, at: 4, magnification: 2 })];
    const placed = locateAll(set, context);
    expect(placed[0].at).toBeCloseTo(2, 3);
  });

  it('refuses an indication with no position on the film', () => {
    const set = [makeIndication({ type: 'SLAG', measuredIn: 0.2 })];
    expect(() => locateAll(set, context)).toThrow(/position measured on the film/);
  });
});

describe('samePlace', () => {
  it('matches two positions within the tolerance', () => {
    expect(samePlace({ a: 5, b: 5.2, odIn })).toBe(true);
  });

  it('does not match two positions apart', () => {
    expect(samePlace({ a: 5, b: 9, odIn })).toBe(false);
  });

  it('matches across the top of the pipe', () => {
    expect(samePlace({ a: around - 0.2, b: 0.2, odIn })).toBe(true);
  });

  it('takes a wider tolerance', () => {
    expect(samePlace({ a: 5, b: 6, odIn, toleranceIn: 2 })).toBe(true);
  });
});

describe('spanOf', () => {
  it('gives the two clock positions a crew can chalk', () => {
    const span = spanOf({ atIn: 0, lengthIn: around / 4, odIn });
    expect(span.from).toBe('12:00');
    expect(span.to).toBe('3:00');
    expect(span.crossesTop).toBe(false);
  });

  it('wraps a span that crosses the top', () => {
    const span = spanOf({ atIn: around - 1, lengthIn: 2, odIn });
    expect(span.crossesTop).toBe(true);
    expect(span.toIn).toBeCloseTo(1, 3);
  });

  it('is a point for a span of no length', () => {
    const span = spanOf({ atIn: 2, lengthIn: 0, odIn });
    expect(span.fromIn).toBe(span.toIn);
  });

  it('refuses a negative length', () => {
    expect(() => spanOf({ atIn: 0, lengthIn: -1, odIn })).toThrow(/not be below 0/);
  });
});
