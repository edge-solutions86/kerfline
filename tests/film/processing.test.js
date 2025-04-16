import { describe, it, expect } from 'vitest';
import {
  referenceTemperatureF,
  temperatureBandF,
  timeTemperatureCurve,
  developmentMinutes,
  temperatureForMinutes,
  judgeCycle,
  replenisherMl,
  batchAreaSquareInches,
  cycleRecord,
} from '../../src/film/processing.js';

describe('the curve', () => {
  it('is five minutes at sixty eight', () => {
    expect(referenceTemperatureF()).toBe(68);
    expect(developmentMinutes(68)).toBe(5);
  });

  it('runs from sixty to eighty', () => {
    expect(temperatureBandF()).toEqual({ minimumF: 60, maximumF: 80 });
  });

  it('gets faster as the bath gets warmer', () => {
    const times = timeTemperatureCurve().map((point) => point.minutes);
    expect(times).toEqual([...times].sort((a, b) => b - a));
  });

  it('reads a charted point back exactly', () => {
    expect(developmentMinutes(72)).toBe(4);
    expect(developmentMinutes(60)).toBe(8);
  });

  it('interpolates between two points', () => {
    const between = developmentMinutes(71);
    expect(between).toBeLessThan(4.5);
    expect(between).toBeGreaterThan(4);
  });

  it('refuses a bath outside the band rather than guessing', () => {
    expect(() => developmentMinutes(55)).toThrow(/outside the band/);
    expect(() => developmentMinutes(90)).toThrow(/outside the band/);
  });

  it('refuses a temperature at or below zero', () => {
    expect(() => developmentMinutes(0)).toThrow(/developer temperature/);
  });
});

describe('temperatureForMinutes', () => {
  it('inverts the curve at a charted point', () => {
    expect(temperatureForMinutes(5)).toBeCloseTo(68, 1);
    expect(temperatureForMinutes(4)).toBeCloseTo(72, 1);
  });

  it('round trips', () => {
    for (const temperature of [62, 68, 74, 79]) {
      const minutes = developmentMinutes(temperature);
      expect(temperatureForMinutes(minutes)).toBeCloseTo(temperature, 0);
    }
  });

  it('refuses a time off the curve', () => {
    expect(() => temperatureForMinutes(12)).toThrow(/off the time temperature curve/);
    expect(() => temperatureForMinutes(1)).toThrow(/off the time temperature curve/);
  });
});

describe('judgeCycle', () => {
  it('passes a cycle run to the curve', () => {
    const verdict = judgeCycle({ temperatureF: 68, minutes: 5 });
    expect(verdict.pass).toBe(true);
    expect(verdict.verdict).toBe('correct');
    expect(verdict.effect).toBe(null);
  });

  it('allows a little slack either side', () => {
    expect(judgeCycle({ temperatureF: 68, minutes: 5.2 }).pass).toBe(true);
    expect(judgeCycle({ temperatureF: 68, minutes: 4.8 }).pass).toBe(true);
  });

  it('calls a long cycle overdeveloped', () => {
    const verdict = judgeCycle({ temperatureF: 68, minutes: 7 });
    expect(verdict.verdict).toBe('overdeveloped');
    expect(verdict.effect).toMatch(/fog both up/);
  });

  it('calls a short cycle underdeveloped and says what it hides', () => {
    const verdict = judgeCycle({ temperatureF: 68, minutes: 3 });
    expect(verdict.verdict).toBe('underdeveloped');
    expect(verdict.effect).toMatch(/hides indications/);
  });

  it('moves the verdict on the same clock when the bath is warmer', () => {
    expect(judgeCycle({ temperatureF: 68, minutes: 5 }).pass).toBe(true);
    expect(judgeCycle({ temperatureF: 78, minutes: 5 }).verdict).toBe('overdeveloped');
  });

  it('takes a tighter tolerance', () => {
    expect(judgeCycle({ temperatureF: 68, minutes: 5.2, tolerance: 0.05 }).pass).toBe(false);
  });

  it('refuses a bath outside the band', () => {
    expect(() => judgeCycle({ temperatureF: 95, minutes: 5 })).toThrow(/outside the band/);
  });
});

describe('replenisherMl and batchAreaSquareInches', () => {
  const films = [
    { widthIn: 4.5, lengthIn: 17 },
    { widthIn: 4.5, lengthIn: 17 },
  ];

  it('adds the film areas up', () => {
    expect(batchAreaSquareInches(films)).toBeCloseTo(153, 1);
  });

  it('is nothing for an empty batch', () => {
    expect(batchAreaSquareInches([])).toBe(0);
    expect(replenisherMl(0)).toBe(0);
  });

  it('doses on area and not on film count', () => {
    const big = [{ widthIn: 14, lengthIn: 17 }];
    expect(batchAreaSquareInches(big)).toBeGreaterThan(batchAreaSquareInches(films));
    expect(replenisherMl(batchAreaSquareInches(big))).toBeGreaterThan(
      replenisherMl(batchAreaSquareInches(films))
    );
  });

  it('takes a different rate', () => {
    expect(replenisherMl(100, { mlPerHundred: 50 })).toBe(50);
    expect(replenisherMl(100)).toBe(75);
  });

  it('refuses a film with no size on it', () => {
    expect(() => batchAreaSquareInches([{ widthIn: 4.5 }])).toThrow(
      /needs a width and a length/
    );
  });

  it('refuses a negative area or rate', () => {
    expect(() => replenisherMl(-1)).toThrow(/not be below 0/);
    expect(() => replenisherMl(100, { mlPerHundred: 0 })).toThrow(/replenishment rate/);
  });
});

describe('cycleRecord', () => {
  const films = [
    { widthIn: 4.5, lengthIn: 17 },
    { widthIn: 4.5, lengthIn: 17 },
  ];

  it('fills the darkroom log', () => {
    const record = cycleRecord({ temperatureF: 68, minutes: 5, films });
    expect(record.pass).toBe(true);
    expect(record.films).toBe(2);
    expect(record.areaSquareInches).toBeCloseTo(153, 1);
    expect(record.replenisherMl).toBeCloseTo(114.8, 1);
  });

  it('totals the whole cycle including fix and wash', () => {
    const record = cycleRecord({ temperatureF: 68, minutes: 5, films });
    expect(record.totalMinutes).toBe(35);
  });

  it('takes different fix and wash times', () => {
    const record = cycleRecord({
      temperatureF: 68,
      minutes: 5,
      films,
      fixMinutes: 8,
      washMinutes: 15,
    });
    expect(record.totalMinutes).toBe(28);
  });

  it('carries a failed development through to the log', () => {
    const record = cycleRecord({ temperatureF: 78, minutes: 5, films });
    expect(record.pass).toBe(false);
    expect(record.verdict).toBe('overdeveloped');
  });
});
