import { describe, it, expect } from 'vitest';
import {
  requireDay,
  isDay,
  formatDay,
  daysBetween,
  addDays,
  addMonths,
  addYears,
  weekday,
  isWeekend,
  addWorkDays,
  earliest,
  latest,
  withinSpan,
  monthsBetween,
} from '../../src/util/dates.js';

describe('requireDay', () => {
  it('accepts a real day and hands it straight back', () => {
    expect(requireDay('2025-03-14')).toBe('2025-03-14');
    expect(requireDay('2024-02-29')).toBe('2024-02-29');
  });

  it('refuses a day that does not exist', () => {
    expect(() => requireDay('2025-02-30')).toThrow(/not a real calendar day/);
    expect(() => requireDay('2025-13-01')).toThrow(/not a real calendar day/);
    expect(() => requireDay('2025-02-29')).toThrow(/not a real calendar day/);
  });

  it('refuses anything not written the one way', () => {
    expect(() => requireDay('14/03/2025')).toThrow(/YYYY-MM-DD/);
    expect(() => requireDay('2025-3-14')).toThrow(/YYYY-MM-DD/);
    expect(() => requireDay('')).toThrow(/YYYY-MM-DD/);
    expect(() => requireDay(null)).toThrow(/YYYY-MM-DD/);
  });

  it('names the field in the message', () => {
    expect(() => requireDay('nope', 'assay date')).toThrow(/assay date must be written/);
  });

  it('answers isDay without throwing', () => {
    expect(isDay('2025-03-14')).toBe(true);
    expect(isDay('2025-02-30')).toBe(false);
    expect(isDay(undefined)).toBe(false);
  });
});

describe('daysBetween and addDays', () => {
  it('counts forward', () => {
    expect(daysBetween('2025-03-01', '2025-03-14')).toBe(13);
  });

  it('counts backward as a negative', () => {
    expect(daysBetween('2025-03-14', '2025-03-01')).toBe(-13);
  });

  it('is zero on the same day', () => {
    expect(daysBetween('2025-03-14', '2025-03-14')).toBe(0);
  });

  it('crosses a leap day', () => {
    expect(daysBetween('2024-02-28', '2024-03-01')).toBe(2);
    expect(daysBetween('2025-02-28', '2025-03-01')).toBe(1);
  });

  it('crosses a daylight saving change without losing an hour', () => {
    expect(daysBetween('2025-03-08', '2025-03-10')).toBe(2);
    expect(daysBetween('2025-11-01', '2025-11-03')).toBe(2);
  });

  it('adds and subtracts days', () => {
    expect(addDays('2025-03-14', 10)).toBe('2025-03-24');
    expect(addDays('2025-03-01', -1)).toBe('2025-02-28');
    expect(addDays('2024-03-01', -1)).toBe('2024-02-29');
  });

  it('round trips a span', () => {
    const days = daysBetween('2024-06-03', '2025-12-15');
    expect(addDays('2024-06-03', days)).toBe('2025-12-15');
  });

  it('refuses a fractional day count', () => {
    expect(() => addDays('2025-03-14', 1.5)).toThrow(/whole number/);
  });

  it('formats a stamp back to a day', () => {
    expect(formatDay(Date.UTC(2025, 2, 14))).toBe('2025-03-14');
  });
});

describe('addMonths', () => {
  it('walks the calendar', () => {
    expect(addMonths('2025-03-14', 6)).toBe('2025-09-14');
    expect(addMonths('2025-03-14', -3)).toBe('2024-12-14');
  });

  it('clamps onto the end of a short month', () => {
    expect(addMonths('2025-03-31', 6)).toBe('2025-09-30');
    expect(addMonths('2025-01-31', 1)).toBe('2025-02-28');
    expect(addMonths('2024-01-31', 1)).toBe('2024-02-29');
  });

  it('crosses a year boundary', () => {
    expect(addMonths('2025-11-15', 3)).toBe('2026-02-15');
    expect(addMonths('2025-01-15', -2)).toBe('2024-11-15');
  });

  it('adds years through months', () => {
    expect(addYears('2025-03-14', 3)).toBe('2028-03-14');
    expect(addYears('2024-02-29', 1)).toBe('2025-02-28');
  });

  it('refuses a fractional month count', () => {
    expect(() => addMonths('2025-03-14', 0.5)).toThrow(/whole number/);
  });
});

describe('the working week', () => {
  it('names the weekday', () => {
    expect(weekday('2025-03-14')).toBe(5);
    expect(weekday('2025-03-16')).toBe(0);
  });

  it('spots a weekend', () => {
    expect(isWeekend('2025-03-15')).toBe(true);
    expect(isWeekend('2025-03-16')).toBe(true);
    expect(isWeekend('2025-03-14')).toBe(false);
  });

  it('skips the weekend when adding working days', () => {
    expect(addWorkDays('2025-03-14', 1)).toBe('2025-03-17');
    expect(addWorkDays('2025-03-14', 5)).toBe('2025-03-21');
  });

  it('adding no working days stays put even on a Saturday', () => {
    expect(addWorkDays('2025-03-15', 0)).toBe('2025-03-15');
  });

  it('refuses a negative working day count', () => {
    expect(() => addWorkDays('2025-03-14', -1)).toThrow(/whole number of days/);
  });
});

describe('earliest and latest', () => {
  it('picks the ends of a list', () => {
    const days = ['2025-03-14', '2024-12-31', '2025-11-02'];
    expect(earliest(days)).toBe('2024-12-31');
    expect(latest(days)).toBe('2025-11-02');
  });

  it('handles a single day', () => {
    expect(earliest(['2025-03-14'])).toBe('2025-03-14');
  });

  it('refuses an empty list', () => {
    expect(() => earliest([])).toThrow(/earliest of no days/);
    expect(() => latest([])).toThrow(/latest of no days/);
  });

  it('refuses a list holding something that is not a day', () => {
    expect(() => earliest(['2025-03-14', 'soon'])).toThrow(/YYYY-MM-DD/);
  });
});

describe('withinSpan', () => {
  it('includes both bounds', () => {
    expect(withinSpan('2025-03-01', '2025-03-01', '2025-03-31')).toBe(true);
    expect(withinSpan('2025-03-31', '2025-03-01', '2025-03-31')).toBe(true);
  });

  it('excludes outside', () => {
    expect(withinSpan('2025-02-28', '2025-03-01', '2025-03-31')).toBe(false);
    expect(withinSpan('2025-04-01', '2025-03-01', '2025-03-31')).toBe(false);
  });

  it('treats a missing bound as open', () => {
    expect(withinSpan('2030-01-01', '2025-03-01', null)).toBe(true);
    expect(withinSpan('1999-01-01', null, '2025-03-31')).toBe(true);
    expect(withinSpan('1999-01-01', undefined, undefined)).toBe(true);
  });
});

describe('monthsBetween', () => {
  it('counts only completed months', () => {
    expect(monthsBetween('2025-01-15', '2025-07-15')).toBe(6);
    expect(monthsBetween('2025-01-15', '2025-07-14')).toBe(5);
    expect(monthsBetween('2025-01-15', '2025-07-16')).toBe(6);
  });

  it('is zero inside one month', () => {
    expect(monthsBetween('2025-01-01', '2025-01-31')).toBe(0);
  });

  it('goes negative running backwards', () => {
    expect(monthsBetween('2025-07-15', '2025-01-15')).toBe(-6);
  });
});
