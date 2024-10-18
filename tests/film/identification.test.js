import { describe, it, expect } from 'vitest';
import {
  identificationFields,
  checkIdentification,
  backscatterVerdict,
  locationMarkerPlan,
  marksForShot,
} from '../../src/film/identification.js';

describe('checkIdentification', () => {
  const complete = { job: 'C-4471', day: '2025-03-14', jointId: 'J-118', position: '3' };

  it('passes a film carrying everything', () => {
    const check = checkIdentification(complete);
    expect(check.complete).toBe(true);
    expect(check.missing).toEqual([]);
  });

  it('names the required fields', () => {
    expect(identificationFields()).toEqual(['job', 'day', 'jointId', 'position']);
  });

  it('lists everything missing rather than stopping at the first', () => {
    const check = checkIdentification({ job: 'C-4471' });
    expect(check.missing).toEqual(['day', 'jointId', 'position']);
    expect(check.complete).toBe(false);
  });

  it('counts a blank field as missing', () => {
    expect(checkIdentification({ ...complete, jointId: '   ' }).missing).toEqual(['jointId']);
  });

  it('catches a date that is not a real day', () => {
    const check = checkIdentification({ ...complete, day: '14/03/2025' });
    expect(check.dayIsReadable).toBe(false);
    expect(check.complete).toBe(false);
  });

  it('refuses something that is not a record at all', () => {
    expect(() => checkIdentification(null)).toThrow(/must be an object/);
  });
});

describe('backscatterVerdict', () => {
  it('rejects a light B on a darker background', () => {
    const verdict = backscatterVerdict({ letterDensity: 1.8, backgroundDensity: 2.6 });
    expect(verdict.appearance).toBe('light');
    expect(verdict.pass).toBe(false);
  });

  it('accepts a dark B on a lighter background', () => {
    const verdict = backscatterVerdict({ letterDensity: 3.1, backgroundDensity: 2.6 });
    expect(verdict.appearance).toBe('dark');
    expect(verdict.pass).toBe(true);
  });

  it('accepts a B that did not image at all', () => {
    const verdict = backscatterVerdict({ letterDensity: 2.6, backgroundDensity: 2.6 });
    expect(verdict.appearance).toBe('not visible');
    expect(verdict.pass).toBe(true);
  });

  it('turns entirely on which way round the contrast went', () => {
    const light = backscatterVerdict({ letterDensity: 2, backgroundDensity: 3 });
    const dark = backscatterVerdict({ letterDensity: 3, backgroundDensity: 2 });
    expect(light.pass).toBe(false);
    expect(dark.pass).toBe(true);
  });

  it('refuses a density at or below zero', () => {
    expect(() => backscatterVerdict({ letterDensity: 0, backgroundDensity: 2 })).toThrow(
      /greater than 0/
    );
  });
});

describe('locationMarkerPlan', () => {
  it('takes one film when the weld fits on it', () => {
    const plan = locationMarkerPlan({ weldLengthIn: 10, filmLengthIn: 14 });
    expect(plan.films).toBe(1);
    expect(plan.coversWholeWeld).toBe(true);
  });

  it('advances by the film length less the overlap', () => {
    const plan = locationMarkerPlan({ weldLengthIn: 40, filmLengthIn: 14, overlapIn: 1 });
    expect(plan.advanceIn).toBe(13);
    expect(plan.films).toBe(3);
  });

  it('numbers the markers from one', () => {
    const plan = locationMarkerPlan({ weldLengthIn: 40, filmLengthIn: 14 });
    expect(plan.markers.map((m) => m.marker)).toEqual(['1', '2', '3']);
  });

  it('never leaves a gap between films', () => {
    const plan = locationMarkerPlan({ weldLengthIn: 40, filmLengthIn: 14, overlapIn: 1 });
    for (let i = 1; i < plan.markers.length; i += 1) {
      expect(plan.markers[i].startIn).toBeLessThan(plan.markers[i - 1].endIn);
    }
  });

  it('covers the whole weld', () => {
    for (const length of [10, 25, 40, 63.5]) {
      const plan = locationMarkerPlan({ weldLengthIn: length, filmLengthIn: 14, overlapIn: 1 });
      expect(plan.coversWholeWeld).toBe(true);
    }
  });

  it('needs more films as the overlap grows', () => {
    const tight = locationMarkerPlan({ weldLengthIn: 40, filmLengthIn: 14, overlapIn: 1 });
    const wide = locationMarkerPlan({ weldLengthIn: 40, filmLengthIn: 14, overlapIn: 6 });
    expect(wide.films).toBeGreaterThan(tight.films);
  });

  it('refuses an overlap as long as the film', () => {
    expect(() =>
      locationMarkerPlan({ weldLengthIn: 40, filmLengthIn: 14, overlapIn: 14 })
    ).toThrow(/no film covers new weld/);
  });

  it('refuses a weld or film length at zero', () => {
    expect(() => locationMarkerPlan({ weldLengthIn: 0, filmLengthIn: 14 })).toThrow(
      /greater than 0/
    );
  });
});

describe('marksForShot', () => {
  it('always carries the identification and the location', () => {
    expect(marksForShot({ position: '3' })).toEqual(['identification', 'location 3']);
  });

  it('adds the F on a film side indicator', () => {
    expect(marksForShot({ position: '3', side: 'film' })).toContain('F');
  });

  it('adds the B when the backscatter check is called for', () => {
    expect(marksForShot({ position: '3', backscatterCheck: true })).toContain('B');
  });

  it('carries both when both apply', () => {
    const marks = marksForShot({ position: '1', side: 'film', backscatterCheck: true });
    expect(marks).toEqual(['identification', 'location 1', 'F', 'B']);
  });

  it('refuses a shot with no location marker', () => {
    expect(() => marksForShot({ position: '' })).toThrow(/carries a location marker/);
    expect(() => marksForShot({})).toThrow(/carries a location marker/);
  });
});
