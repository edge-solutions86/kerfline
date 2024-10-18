import { BadInput } from '../errors.js';
import { round } from '../util/num.js';
import { requireAbove, requireAtLeast } from '../util/guard.js';
import { isDay } from '../util/dates.js';

/**
 * What has to appear on the film itself.
 *
 * All of this is laid in lead on the part before the shot, so it is in the
 * image rather than written on the jacket afterwards. That is the point: a
 * film that has come out of its jacket still says which joint it is, which
 * part of that joint, and when it was shot.
 *
 * The backscatter check is the one rule here that is not symmetric, and the
 * asymmetry is the whole rule. A lead B is taped to the back of the cassette.
 * If it shows up LIGHT against a darker background, radiation is coming back
 * through the film from behind and the radiograph is rejected. If it shows up
 * DARK against a lighter background, that is just the B absorbing the beam on
 * its way through and the film is fine. People remember that the B matters
 * and forget which way round, so it is spelled out here.
 */

const REQUIRED = ['job', 'day', 'jointId', 'position'];

export function identificationFields() {
  return [...REQUIRED];
}

/**
 * Check a film carries everything it has to. Returns the list of what is
 * missing rather than the first thing missing, because a reader filling in a
 * rejection wants to write the whole list once.
 */
export function checkIdentification(record) {
  if (typeof record !== 'object' || record === null) {
    throw new BadInput('a film identification must be an object', { got: record });
  }
  const missing = REQUIRED.filter((field) => {
    const value = record[field];
    return value === null || value === undefined || String(value).trim() === '';
  });
  const badDay = record.day !== undefined && record.day !== null && !isDay(record.day);
  return {
    missing,
    dayIsReadable: !badDay,
    complete: missing.length === 0 && !badDay,
  };
}

/**
 * Read the backscatter check. The verdict turns entirely on which way round
 * the contrast went, so this takes the two densities rather than a boolean
 * somebody has already decided.
 */
export function backscatterVerdict({ letterDensity, backgroundDensity }) {
  requireAbove(letterDensity, 0, 'density through the letter');
  requireAbove(backgroundDensity, 0, 'background density');
  if (letterDensity < backgroundDensity) {
    return {
      appearance: 'light',
      pass: false,
      reason: 'a light B means radiation is coming back through the film from behind',
    };
  }
  if (letterDensity > backgroundDensity) {
    return {
      appearance: 'dark',
      pass: true,
      reason: 'a dark B is the letter absorbing the beam and is not a rejection',
    };
  }
  return {
    appearance: 'not visible',
    pass: true,
    reason: 'the B did not image, so there is nothing to read either way',
  };
}

/**
 * Where the location markers go along a weld, and how many films it takes.
 * Adjacent films have to overlap so nothing falls down the gap between them,
 * and the overlap is measured on the part rather than on the film.
 */
export function locationMarkerPlan({ weldLengthIn, filmLengthIn, overlapIn = 1 }) {
  requireAbove(weldLengthIn, 0, 'weld length');
  requireAbove(filmLengthIn, 0, 'film length');
  requireAtLeast(overlapIn, 0, 'overlap');
  if (overlapIn >= filmLengthIn) {
    throw new BadInput('the overlap is as long as the film, so no film covers new weld', {
      filmLengthIn,
      overlapIn,
    });
  }
  const advance = filmLengthIn - overlapIn;
  const count = Math.max(1, Math.ceil((weldLengthIn - overlapIn) / advance));
  const markers = Array.from({ length: count }, (_unused, index) => {
    const startIn = round(index * advance, 3);
    return {
      film: index + 1,
      marker: String(index + 1),
      startIn,
      endIn: round(Math.min(startIn + filmLengthIn, weldLengthIn), 3),
    };
  });
  const covered = markers[markers.length - 1].endIn;
  return {
    films: count,
    advanceIn: round(advance, 3),
    overlapIn,
    markers,
    coveredIn: covered,
    coversWholeWeld: covered >= weldLengthIn - 1e-9,
  };
}

/**
 * The marks one shot needs in lead. The F only appears when the indicator is
 * on the film side, and the B only when nothing behind the film is stopping
 * backscatter on its own.
 */
export function marksForShot({ side = 'source', backscatterCheck = false, position }) {
  if (position === undefined || position === null || String(position).trim() === '') {
    throw new BadInput('every shot carries a location marker', { position });
  }
  const marks = ['identification', `location ${position}`];
  if (side === 'film') marks.push('F');
  if (backscatterCheck) marks.push('B');
  return marks;
}
