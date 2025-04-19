import { describe, it, expect } from 'vitest';
import {
  filmSizes,
  sizeOf,
  shelfLifeMonths,
  smallestCovering,
  makeStock,
  stockIsCurrent,
  daysOfLifeLeft,
  stockToUseUp,
  makeCassette,
  load,
  unload,
  screensMatch,
} from '../../src/film/cassette.js';

const stock = makeStock({
  batch: 'B-2291',
  size: '4.5x17',
  boxedOn: '2024-11-01',
  sheets: 100,
});
const cassette = makeCassette({ id: 'C-4', size: '4.5x17' });

describe('the stocked sizes', () => {
  it('lists what the store carries', () => {
    expect(filmSizes()).toContain('4.5x17');
    expect(filmSizes()).toContain('14x17');
  });

  it('works the area out', () => {
    expect(sizeOf('4.5x17').areaSquareInches).toBeCloseTo(76.5, 2);
  });

  it('refuses a size nobody stocks', () => {
    expect(() => sizeOf('5x20')).toThrow(/no film size 5x20/);
  });

  it('keeps film for eighteen months', () => {
    expect(shelfLifeMonths()).toBe(18);
  });
});

describe('smallestCovering', () => {
  it('picks the smallest film that reaches', () => {
    expect(smallestCovering(8).name).toBe('4.5x8.5');
    // 3.5x17 is 59.5 square inches against 76.5 for 4.5x17, so it wins on
    // area even though it is the longer roll of the two narrow sizes.
    expect(smallestCovering(12).name).toBe('3.5x17');
  });

  it('goes to the wider film once the weld needs the width', () => {
    expect(smallestCovering(12, { widthIn: 4 }).name).toBe('4.5x17');
  });

  it('takes a width requirement into account', () => {
    expect(smallestCovering(12, { widthIn: 6 }).name).toBe('7x17');
  });

  it('refuses when nothing stocked reaches', () => {
    expect(() => smallestCovering(30)).toThrow(/no stocked film covers/);
  });

  it('picks something for a very short weld', () => {
    expect(smallestCovering(1).lengthIn).toBeGreaterThan(0);
  });
});

describe('makeStock', () => {
  it('works the expiry out from the boxing date', () => {
    expect(stock.expiresOn).toBe('2026-05-01');
  });

  it('refuses stock with no batch number', () => {
    expect(() =>
      makeStock({ batch: ' ', size: '4.5x17', boxedOn: '2024-11-01', sheets: 10 })
    ).toThrow(/carries its batch number/);
  });

  it('refuses a fractional sheet count', () => {
    expect(() =>
      makeStock({ batch: 'B-1', size: '4.5x17', boxedOn: '2024-11-01', sheets: 1.5 })
    ).toThrow(/whole number/);
  });

  it('is current well before the expiry and not after it', () => {
    expect(stockIsCurrent(stock, '2025-03-14')).toBe(true);
    expect(stockIsCurrent(stock, '2026-05-01')).toBe(true);
    expect(stockIsCurrent(stock, '2026-05-02')).toBe(false);
  });

  it('counts the days of life left', () => {
    expect(daysOfLifeLeft(stock, '2026-04-30')).toBe(1);
    expect(daysOfLifeLeft(stock, '2026-06-01')).toBeLessThan(0);
  });
});

describe('stockToUseUp', () => {
  const older = makeStock({ batch: 'B-1', size: '4.5x17', boxedOn: '2023-11-01', sheets: 20 });
  const empty = makeStock({ batch: 'B-0', size: '4.5x17', boxedOn: '2023-11-01', sheets: 0 });

  it('finds stock going out of date', () => {
    const list = stockToUseUp([stock, older], { day: '2025-04-01' });
    expect(list.map((s) => s.batch)).toEqual(['B-1']);
  });

  it('puts the nearest to expiry first', () => {
    const list = stockToUseUp([stock, older], { day: '2025-04-01', withinDays: 5000 });
    expect(list[0].batch).toBe('B-1');
  });

  it('leaves out boxes with no sheets left', () => {
    expect(stockToUseUp([empty], { day: '2025-04-01' })).toEqual([]);
  });

  it('finds nothing when everything is fresh', () => {
    expect(stockToUseUp([stock], { day: '2025-01-01', withinDays: 30 })).toEqual([]);
  });
});

describe('makeCassette', () => {
  it('records what screens are in it', () => {
    expect(cassette.screens).toBe('LEAD_STANDARD');
    expect(cassette.frontIn).toBeCloseTo(0.005, 4);
    expect(cassette.backIn).toBeCloseTo(0.01, 4);
  });

  it('starts empty', () => {
    expect(cassette.loaded).toBe(null);
  });

  it('refuses a cassette with no number', () => {
    expect(() => makeCassette({ id: ' ', size: '4.5x17' })).toThrow(/identified by its number/);
  });

  it('refuses screens it does not stock', () => {
    expect(() => makeCassette({ id: 'C-1', size: '4.5x17', screens: 'FLUORO' })).toThrow(
      /no screen set called/
    );
  });

  it('answers whether it carries the screens a technique wants', () => {
    expect(screensMatch(cassette, 'LEAD_STANDARD')).toBe(true);
    expect(screensMatch(cassette, 'LEAD_HEAVY')).toBe(false);
  });
});

describe('load and unload', () => {
  it('takes a sheet out of the box and puts it in the cassette', () => {
    const result = load(cassette, stock, '2025-03-14');
    expect(result.cassette.loaded.batch).toBe('B-2291');
    expect(result.stock.sheets).toBe(99);
  });

  it('leaves the originals alone', () => {
    load(cassette, stock, '2025-03-14');
    expect(cassette.loaded).toBe(null);
    expect(stock.sheets).toBe(100);
  });

  it('refuses to load a cassette that already has film in it', () => {
    const once = load(cassette, stock, '2025-03-14');
    expect(() => load(once.cassette, once.stock, '2025-03-14')).toThrow(/already has film/);
  });

  it('refuses film of the wrong size', () => {
    const wrong = makeStock({ batch: 'B-9', size: '14x17', boxedOn: '2024-11-01', sheets: 10 });
    expect(() => load(cassette, wrong, '2025-03-14')).toThrow(/takes 4.5x17/);
  });

  it('refuses expired film', () => {
    expect(() => load(cassette, stock, '2026-06-01')).toThrow(/expired on 2026-05-01/);
  });

  it('refuses an empty box', () => {
    const none = makeStock({ batch: 'B-0', size: '4.5x17', boxedOn: '2024-11-01', sheets: 0 });
    expect(() => load(cassette, none, '2025-03-14')).toThrow(/is empty/);
  });

  it('unloads back to empty', () => {
    const once = load(cassette, stock, '2025-03-14');
    expect(unload(once.cassette).loaded).toBe(null);
  });

  it('refuses to unload a cassette that is already empty', () => {
    expect(() => unload(cassette)).toThrow(/already empty/);
  });
});
