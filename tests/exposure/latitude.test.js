import { describe, it, expect } from 'vitest';
import {
  latitudeIn,
  coveredBand,
  coversRange,
  doubleLoadBand,
  filmsCovering,
} from '../../src/exposure/latitude.js';

describe('latitudeIn', () => {
  it('gives a positive band', () => {
    expect(latitudeIn({ isotopeId: 'IR192', filmId: 'D7' })).toBeGreaterThan(0);
  });

  it('gives a fine film LESS latitude than a fast one', () => {
    const fine = latitudeIn({ isotopeId: 'IR192', filmId: 'D4' });
    const fast = latitudeIn({ isotopeId: 'IR192', filmId: 'D8' });
    expect(fine).toBeLessThan(fast);
  });

  it('gives a harder source more latitude', () => {
    const iridium = latitudeIn({ isotopeId: 'IR192', filmId: 'D7' });
    const cobalt = latitudeIn({ isotopeId: 'CO60', filmId: 'D7' });
    expect(cobalt).toBeGreaterThan(iridium);
  });

  it('grows with the density span allowed', () => {
    const tight = latitudeIn({ isotopeId: 'IR192', filmId: 'D7', densitySpan: 1 });
    const wide = latitudeIn({ isotopeId: 'IR192', filmId: 'D7', densitySpan: 2 });
    expect(wide).toBeCloseTo(tight * 2, 3);
  });

  it('refuses a span at zero', () => {
    expect(() => latitudeIn({ isotopeId: 'IR192', filmId: 'D7', densitySpan: 0 })).toThrow(
      /density span/
    );
  });

  it('refuses a source with no chart', () => {
    expect(() => latitudeIn({ isotopeId: 'YB169', filmId: 'D7' })).toThrow(/no exposure chart/);
  });
});

describe('coveredBand', () => {
  it('brackets the thickness it was aimed at', () => {
    const band = coveredBand({ isotopeId: 'IR192', filmId: 'D7', thicknessIn: 1 });
    expect(band.minimumIn).toBeLessThan(1);
    expect(band.maximumIn).toBeGreaterThan(1);
  });

  it('is as wide as the latitude', () => {
    const band = coveredBand({ isotopeId: 'IR192', filmId: 'D7', thicknessIn: 1 });
    expect(band.maximumIn - band.minimumIn).toBeCloseTo(band.latitudeIn, 3);
  });

  it('never goes below nothing on a thin section', () => {
    const band = coveredBand({ isotopeId: 'IR192', filmId: 'D8', thicknessIn: 0.3 });
    expect(band.minimumIn).toBeGreaterThanOrEqual(0);
  });

  it('refuses a thickness at zero', () => {
    expect(() => coveredBand({ isotopeId: 'IR192', filmId: 'D7', thicknessIn: 0 })).toThrow(
      /thickness/
    );
  });
});

describe('coversRange', () => {
  it('covers a section that hardly varies', () => {
    const check = coversRange({
      isotopeId: 'IR192',
      filmId: 'D7',
      minimumIn: 0.5,
      maximumIn: 0.52,
    });
    expect(check.covers).toBe(true);
    expect(check.shortIn).toBe(0);
  });

  it('does not cover a section that varies a lot, and says by how much', () => {
    const check = coversRange({
      isotopeId: 'IR192',
      filmId: 'D7',
      minimumIn: 0.5,
      maximumIn: 2,
    });
    expect(check.covers).toBe(false);
    expect(check.shortIn).toBeGreaterThan(0);
    expect(check.neededIn).toBeCloseTo(check.availableIn + check.shortIn, 3);
  });

  it('covers more on a faster film', () => {
    const range = { isotopeId: 'IR192', minimumIn: 0.5, maximumIn: 1 };
    const fine = coversRange({ ...range, filmId: 'D4' });
    const fast = coversRange({ ...range, filmId: 'D8' });
    expect(fast.availableIn).toBeGreaterThan(fine.availableIn);
  });

  it('refuses a range that runs backwards', () => {
    expect(() =>
      coversRange({ isotopeId: 'IR192', filmId: 'D7', minimumIn: 2, maximumIn: 1 })
    ).toThrow(/thinner than the thinnest/);
  });
});

describe('doubleLoadBand', () => {
  it('covers more than either film alone', () => {
    const pair = doubleLoadBand({ isotopeId: 'IR192', slowFilmId: 'D5', fastFilmId: 'D8' });
    expect(pair.combinedIn).toBeGreaterThan(pair.slowLatitudeIn);
    expect(pair.combinedIn).toBeGreaterThan(pair.fastLatitudeIn);
  });

  it('offsets the two by their speed ratio', () => {
    const near = doubleLoadBand({ isotopeId: 'IR192', slowFilmId: 'D7', fastFilmId: 'D8' });
    const far = doubleLoadBand({ isotopeId: 'IR192', slowFilmId: 'D4', fastFilmId: 'D8' });
    expect(far.offsetIn).toBeGreaterThan(near.offsetIn);
  });

  it('names both films', () => {
    const pair = doubleLoadBand({ isotopeId: 'IR192', slowFilmId: 'D5', fastFilmId: 'D8' });
    expect(pair.slow).toBe('D5');
    expect(pair.fast).toBe('D8');
  });

  it('refuses a pair where the second is not faster', () => {
    expect(() =>
      doubleLoadBand({ isotopeId: 'IR192', slowFilmId: 'D8', fastFilmId: 'D5' })
    ).toThrow(/has to be faster/);
    expect(() =>
      doubleLoadBand({ isotopeId: 'IR192', slowFilmId: 'D7', fastFilmId: 'D7' })
    ).toThrow(/has to be faster/);
  });
});

describe('filmsCovering', () => {
  it('lists the films that reach, finest first', () => {
    const films = filmsCovering({ isotopeId: 'IR192', minimumIn: 0.5, maximumIn: 0.55 });
    expect(films.length).toBeGreaterThan(0);
    const latitudes = films.map((entry) => entry.latitudeIn);
    expect(latitudes).toEqual([...latitudes].sort((a, b) => a - b));
  });

  it('drops the fine films as the section varies more', () => {
    const narrow = filmsCovering({ isotopeId: 'IR192', minimumIn: 0.5, maximumIn: 0.55 });
    const wide = filmsCovering({ isotopeId: 'IR192', minimumIn: 0.5, maximumIn: 0.8 });
    expect(wide.length).toBeLessThanOrEqual(narrow.length);
  });

  it('finds nothing for a section that varies far too much', () => {
    expect(filmsCovering({ isotopeId: 'IR192', minimumIn: 0.3, maximumIn: 2.5 })).toEqual([]);
  });
});
