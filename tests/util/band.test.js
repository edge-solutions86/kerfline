import { describe, it, expect } from 'vitest';
import {
  bandTable,
  bandRow,
  bandValue,
  bandValueOr,
  covers,
  describeBands,
} from '../../src/util/band.js';

const thickness = bandTable(
  [
    { upTo: 0.25, value: 'A' },
    { upTo: 0.75, value: 'B' },
    { upTo: 1.5, value: 'C' },
    { upTo: Infinity, value: 'D' },
  ],
  { name: 'thickness bands' }
);

describe('bandTable', () => {
  it('chains each row onto the one below it', () => {
    expect(thickness.rows.map((r) => [r.over, r.upTo])).toEqual([
      [0, 0.25],
      [0.25, 0.75],
      [0.75, 1.5],
      [1.5, Infinity],
    ]);
  });

  it('records the floor and the ceiling', () => {
    expect(thickness.floor).toBe(0);
    expect(thickness.ceiling).toBe(Infinity);
  });

  it('takes an explicit lower bound on the first row', () => {
    const table = bandTable([{ over: 2, upTo: 4, value: 'x' }]);
    expect(table.floor).toBe(2);
  });

  it('freezes the rows so a caller cannot edit the code table', () => {
    expect(() => {
      thickness.rows[0].value = 'Z';
    }).toThrow();
  });

  it('refuses an empty table', () => {
    expect(() => bandTable([])).toThrow(/at least one row/);
    expect(() => bandTable(null)).toThrow(/at least one row/);
  });

  it('refuses a row that is not an object', () => {
    expect(() => bandTable([1])).toThrow(/is not an object/);
  });

  it('refuses a row that does not go upwards', () => {
    expect(() => bandTable([{ over: 1, upTo: 1, value: 'x' }])).toThrow(/does not go upwards/);
    expect(() => bandTable([{ over: 2, upTo: 1, value: 'x' }])).toThrow(/does not go upwards/);
  });

  it('refuses a gap between rows', () => {
    const rows = [
      { upTo: 0.25, value: 'A' },
      { over: 0.5, upTo: 1, value: 'B' },
    ];
    expect(() => bandTable(rows)).toThrow(/gap or overlaps/);
  });

  it('refuses an overlap between rows', () => {
    const rows = [
      { upTo: 0.5, value: 'A' },
      { over: 0.25, upTo: 1, value: 'B' },
    ];
    expect(() => bandTable(rows)).toThrow(/gap or overlaps/);
  });

  it('refuses an open ended row anywhere but last', () => {
    const rows = [
      { upTo: Infinity, value: 'A' },
      { over: Infinity, upTo: 2, value: 'B' },
    ];
    expect(() => bandTable(rows)).toThrow(/open ended but is not the last/);
  });

  it('refuses a bound that is not a number', () => {
    expect(() => bandTable([{ upTo: '1', value: 'x' }])).toThrow(/bad upper bound/);
    expect(() => bandTable([{ over: '0', upTo: 1, value: 'x' }])).toThrow(/bad lower bound/);
  });
});

describe('bandRow and bandValue', () => {
  it('reads the code convention: over the lower, through the upper', () => {
    expect(bandValue(thickness, 0.25)).toBe('A');
    expect(bandValue(thickness, 0.2501)).toBe('B');
    expect(bandValue(thickness, 0.75)).toBe('B');
    expect(bandValue(thickness, 0.7501)).toBe('C');
    expect(bandValue(thickness, 1.5)).toBe('C');
    expect(bandValue(thickness, 1.5001)).toBe('D');
  });

  it('includes the very bottom of the table', () => {
    expect(bandValue(thickness, 0)).toBe('A');
  });

  it('runs off to the open ended row', () => {
    expect(bandValue(thickness, 12)).toBe('D');
  });

  it('hands back the whole row when asked', () => {
    expect(bandRow(thickness, 0.5)).toEqual({ over: 0.25, upTo: 0.75, value: 'B' });
  });

  it('refuses a value below the floor', () => {
    const table = bandTable([{ over: 1, upTo: 2, value: 'x' }], { name: 'shim table' });
    expect(() => bandValue(table, 0.5)).toThrow(/below the bottom of shim table/);
  });

  it('refuses a value above a closed ceiling', () => {
    const table = bandTable([{ upTo: 2, value: 'x' }], { name: 'shim table' });
    expect(() => bandValue(table, 2.5)).toThrow(/above the top of shim table/);
  });

  it('refuses a lookup that is not a number', () => {
    expect(() => bandValue(thickness, '0.5')).toThrow(/finite lookup value/);
  });

  it('refuses something that is not a table', () => {
    expect(() => bandValue({}, 1)).toThrow(/not a band table/);
    expect(() => bandValue(null, 1)).toThrow(/not a band table/);
  });
});

describe('bandValueOr', () => {
  const table = bandTable([{ over: 1, upTo: 2, value: 'x' }]);

  it('reads a value inside the table', () => {
    expect(bandValueOr(table, 1.5, 'fallback')).toBe('x');
  });

  it('falls back off either end', () => {
    expect(bandValueOr(table, 0, 'fallback')).toBe('fallback');
    expect(bandValueOr(table, 9, 'fallback')).toBe('fallback');
  });

  it('still throws on a bad input rather than swallowing it', () => {
    expect(() => bandValueOr(table, 'x', 'fallback')).toThrow(/finite lookup value/);
  });
});

describe('covers', () => {
  it('answers without throwing', () => {
    const table = bandTable([{ over: 1, upTo: 2, value: 'x' }]);
    expect(covers(table, 1.5)).toBe(true);
    expect(covers(table, 0)).toBe(false);
    expect(covers(table, 'x')).toBe(false);
  });
});

describe('describeBands', () => {
  it('renders the bands the way the code prints them', () => {
    expect(describeBands(thickness)).toEqual([
      'over 0 through 0.25: A',
      'over 0.25 through 0.75: B',
      'over 0.75 through 1.5: C',
      'over 1.5 and above: D',
    ]);
  });

  it('takes a formatter for the bounds', () => {
    const lines = describeBands(thickness, (v) => `${v} in`);
    expect(lines[0]).toBe('over 0 in through 0.25 in: A');
  });
});
