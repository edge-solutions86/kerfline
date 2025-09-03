import { describe, it, expect } from 'vitest';
import {
  makeReadingSet,
  profile,
  judgeSet,
  failingReadings,
  compareSets,
} from '../../src/density/reading.js';
import { calibrationRecord } from '../../src/density/stepwedge.js';

const certified = [1, 1.5, 2, 2.5, 3, 3.5, 4];

const calibration = calibrationRecord({
  instrument: 'X-Rite 301',
  day: '2025-01-10',
  readings: certified,
  certified,
});

const base = {
  film: 'J-118-1',
  day: '2025-03-14',
  instrument: 'X-Rite 301',
  calibration,
  indicatorDensity: 2.4,
  area: [
    { at: 'A', density: 2.3 },
    { at: 'B', density: 2.5 },
    { at: 'C', density: 2.4 },
  ],
};

describe('makeReadingSet', () => {
  const set = makeReadingSet(base);

  it('keeps the reference apart from the area readings', () => {
    expect(set.indicatorDensity).toBe(2.4);
    expect(set.area).toHaveLength(3);
  });

  it('records the instrument and that it was calibrated', () => {
    expect(set.instrument).toBe('X-Rite 301');
    expect(set.calibrated).toBe(true);
  });

  it('refuses an instrument that was out of calibration that day', () => {
    expect(() => makeReadingSet({ ...base, day: '2025-08-01' })).toThrow(
      /was not in calibration on 2025-08-01/
    );
  });

  it('refuses a calibration belonging to another instrument', () => {
    expect(() => makeReadingSet({ ...base, instrument: 'Macbeth TD-903' })).toThrow(
      /for a different instrument/
    );
  });

  it('allows a set with no calibration on file, and says so', () => {
    const set_ = makeReadingSet({ ...base, calibration: null });
    expect(set_.calibrated).toBe(false);
  });

  it('refuses a set with no film or no instrument named', () => {
    expect(() => makeReadingSet({ ...base, film: ' ' })).toThrow(/names the film/);
    expect(() => makeReadingSet({ ...base, instrument: '', calibration: null })).toThrow(
      /names the instrument/
    );
  });

  it('names the area reading that carries no density', () => {
    expect(() => makeReadingSet({ ...base, area: [{ at: 'A' }] })).toThrow(
      /area reading 1 has no usable density/
    );
  });

  it('takes a bare number as an area reading', () => {
    const set_ = makeReadingSet({ ...base, area: [2.3, 2.5] });
    expect(set_.area[0]).toEqual({ at: null, density: 2.3 });
  });
});

describe('profile', () => {
  it('describes the shape of the film', () => {
    const shape = profile(makeReadingSet(base));
    expect(shape.readings).toBe(3);
    expect(shape.lightestDensity).toBe(2.3);
    expect(shape.darkestDensity).toBe(2.5);
    expect(shape.meanDensity).toBeCloseTo(2.4, 2);
    expect(shape.spread).toBeCloseTo(0.2, 2);
  });

  it('falls back to the reference when nothing was read across the area', () => {
    const shape = profile(makeReadingSet({ ...base, area: [] }));
    expect(shape.readings).toBe(0);
    expect(shape.meanDensity).toBe(2.4);
    expect(shape.spread).toBe(0);
  });
});

describe('judgeSet', () => {
  it('passes a good film', () => {
    const judged = judgeSet(makeReadingSet(base));
    expect(judged.pass).toBe(true);
    expect(judged.reference.pass).toBe(true);
    expect(judged.area.pass).toBe(true);
  });

  it('fails when the reference is outside the code limits', () => {
    const judged = judgeSet(makeReadingSet({ ...base, indicatorDensity: 1.6, area: [] }));
    expect(judged.pass).toBe(false);
    expect(judged.reference.verdict).toBe('light');
  });

  it('fails when the area wanders outside the variation band', () => {
    const judged = judgeSet(makeReadingSet({ ...base, area: [{ at: 'A', density: 1.5 }] }));
    expect(judged.pass).toBe(false);
    expect(judged.area.pass).toBe(false);
  });

  it('judges the area against the reference and not a constant', () => {
    const light = judgeSet(makeReadingSet({ ...base, indicatorDensity: 2, area: [2.5] }));
    const dark = judgeSet(makeReadingSet({ ...base, indicatorDensity: 3.5, area: [2.5] }));
    expect(light.area.pass).toBe(true);
    expect(dark.area.pass).toBe(false);
  });

  it('has no area verdict when nothing was read across it', () => {
    expect(judgeSet(makeReadingSet({ ...base, area: [] })).area).toBe(null);
  });

  it('lists the readings that failed', () => {
    const set = makeReadingSet({ ...base, area: [{ at: 'A', density: 1.5 }] });
    expect(failingReadings(set)).toHaveLength(1);
  });

  it('lists nothing failing on a good film', () => {
    expect(failingReadings(makeReadingSet(base))).toEqual([]);
  });
});

describe('compareSets', () => {
  const first = makeReadingSet(base);

  it('agrees when two instruments read the same', () => {
    const second = makeReadingSet({
      ...base,
      instrument: 'X-Rite 301',
      indicatorDensity: 2.42,
    });
    const check = compareSets(first, second);
    expect(check.agree).toBe(true);
    expect(check.difference).toBeCloseTo(0.02, 3);
  });

  it('disagrees when they do not', () => {
    const second = makeReadingSet({ ...base, indicatorDensity: 2.7 });
    expect(compareSets(first, second).agree).toBe(false);
  });

  it('names both instruments', () => {
    const second = makeReadingSet({ ...base, indicatorDensity: 2.42 });
    expect(compareSets(first, second).instruments).toHaveLength(2);
  });

  it('refuses to compare two different films', () => {
    const other = makeReadingSet({ ...base, film: 'J-119-1' });
    expect(() => compareSets(first, other)).toThrow(/off different films/);
  });

  it('takes a tighter tolerance', () => {
    const second = makeReadingSet({ ...base, indicatorDensity: 2.42 });
    expect(compareSets(first, second, 0.01).agree).toBe(false);
  });
});
