import { BadInput, NotFound, Conflict } from '../errors.js';
import { round } from '../util/num.js';
import { requireArray, requireAtLeast, requireWhole } from '../util/guard.js';
import { requireDay, daysBetween, addMonths } from '../util/dates.js';
import { screen } from '../exposure/screens.js';

/**
 * Film stock and the cassettes it goes in.
 *
 * Two things here have bitten this shop and both are in the tests. Film has a
 * shelf life and film past it fogs, which reads as a light weld on a dark
 * background and gets called a technique problem. And a cassette loaded with
 * the wrong screens is invisible on the film until somebody works out that
 * every shot from one particular cassette is running a stop light.
 *
 * So a cassette knows what screens are in it and stock knows when it expires,
 * and both refuse rather than being helpful about it.
 */

const SIZES = {
  '4.5x17': { widthIn: 4.5, lengthIn: 17 },
  '4.5x10': { widthIn: 4.5, lengthIn: 10 },
  '4.5x8.5': { widthIn: 4.5, lengthIn: 8.5 },
  '3.5x17': { widthIn: 3.5, lengthIn: 17 },
  '7x17': { widthIn: 7, lengthIn: 17 },
  '10x12': { widthIn: 10, lengthIn: 12 },
  '14x17': { widthIn: 14, lengthIn: 17 },
};

const SHELF_MONTHS = 18;

export function filmSizes() {
  return Object.keys(SIZES);
}

export function sizeOf(name) {
  const found = SIZES[name];
  if (!found) {
    throw new NotFound(`no film size ${name} is stocked`, { asked: name, known: filmSizes() });
  }
  return { name, ...found, areaSquareInches: round(found.widthIn * found.lengthIn, 2) };
}

export function shelfLifeMonths() {
  return SHELF_MONTHS;
}

/** The smallest stocked film that covers a length of weld plus its overlap. */
export function smallestCovering(lengthIn, { widthIn = 0 } = {}) {
  requireAtLeast(lengthIn, 0, 'weld length');
  const fits = filmSizes()
    .map((name) => sizeOf(name))
    .filter((size) => size.lengthIn >= lengthIn && size.widthIn >= widthIn)
    .sort((a, b) => a.areaSquareInches - b.areaSquareInches);
  if (fits.length === 0) {
    throw new NotFound(`no stocked film covers ${lengthIn} in`, { lengthIn, widthIn });
  }
  return fits[0];
}

export function makeStock({ batch, size, boxedOn, sheets, filmId = 'D7' }) {
  if (typeof batch !== 'string' || batch.trim() === '') {
    throw new BadInput('film stock carries its batch number', { got: batch });
  }
  const dimensions = sizeOf(size);
  requireDay(boxedOn, 'boxing date');
  requireWhole(sheets, 'sheet count');
  requireAtLeast(sheets, 0, 'sheet count');
  return {
    batch: batch.trim(),
    size: dimensions.name,
    filmId,
    boxedOn,
    expiresOn: addMonths(boxedOn, SHELF_MONTHS),
    sheets,
  };
}

export function stockIsCurrent(stock, day) {
  requireDay(day, 'date');
  return daysBetween(day, stock.expiresOn) >= 0;
}

export function daysOfLifeLeft(stock, day) {
  requireDay(day, 'date');
  return daysBetween(day, stock.expiresOn);
}

/** Stock that has expired or will inside a window. */
export function stockToUseUp(stocks, { day, withinDays = 60 }) {
  requireArray(stocks, 'stock');
  requireAtLeast(withinDays, 0, 'window');
  return stocks
    .map((stock) => ({ ...stock, daysLeft: daysOfLifeLeft(stock, day) }))
    .filter((stock) => stock.daysLeft <= withinDays && stock.sheets > 0)
    .sort((a, b) => a.daysLeft - b.daysLeft);
}

export function makeCassette({ id, size, screens = 'LEAD_STANDARD' }) {
  if (typeof id !== 'string' || id.trim() === '') {
    throw new BadInput('a cassette is identified by its number', { got: id });
  }
  const dimensions = sizeOf(size);
  const set = screen(screens);
  return {
    id: id.trim(),
    size: dimensions.name,
    screens: set.name,
    frontIn: set.frontIn,
    backIn: set.backIn,
    loaded: null,
  };
}

/**
 * Load a cassette. Refuses expired stock, stock of the wrong size, and a
 * cassette that already has film in it, which is how a double exposure
 * happens.
 */
export function load(cassette, stock, day) {
  requireDay(day, 'loading date');
  if (cassette.loaded !== null) {
    throw new Conflict(`cassette ${cassette.id} already has film in it`, { id: cassette.id });
  }
  if (stock.size !== cassette.size) {
    throw new Conflict(`cassette ${cassette.id} takes ${cassette.size}, not ${stock.size}`, {
      cassette: cassette.size,
      stock: stock.size,
    });
  }
  if (!stockIsCurrent(stock, day)) {
    throw new Conflict(`batch ${stock.batch} expired on ${stock.expiresOn}`, {
      batch: stock.batch,
      expiresOn: stock.expiresOn,
    });
  }
  if (stock.sheets < 1) {
    throw new Conflict(`batch ${stock.batch} is empty`, { batch: stock.batch });
  }
  return {
    cassette: { ...cassette, loaded: { batch: stock.batch, filmId: stock.filmId, day } },
    stock: { ...stock, sheets: stock.sheets - 1 },
  };
}

export function unload(cassette) {
  if (cassette.loaded === null) {
    throw new Conflict(`cassette ${cassette.id} is already empty`, { id: cassette.id });
  }
  return { ...cassette, loaded: null };
}

/** Whether a cassette carries the screens a technique calls for. */
export function screensMatch(cassette, wanted) {
  return cassette.screens === screen(wanted).name;
}
