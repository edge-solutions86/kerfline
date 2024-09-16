import { BadInput } from '../errors.js';
import { round } from '../util/num.js';
import { requireAbove, requireAtLeast } from '../util/guard.js';

/**
 * The three distances a setup is described by, and the relations between
 * them. Source to film is the one written on the technique sheet, source to
 * object is the one the unsharpness check wants, and object to film is the
 * standoff. Two of the three fix the third, and a surprising number of
 * disagreements between a sheet and a shot come down to somebody writing one
 * of them where another belonged.
 */

export function sourceToFilm({ sourceToObjectIn, objectToFilmIn }) {
  requireAbove(sourceToObjectIn, 0, 'source to object distance');
  requireAtLeast(objectToFilmIn, 0, 'object to film distance');
  return sourceToObjectIn + objectToFilmIn;
}

export function sourceToObject({ sourceToFilmIn, objectToFilmIn }) {
  requireAbove(sourceToFilmIn, 0, 'source to film distance');
  requireAtLeast(objectToFilmIn, 0, 'object to film distance');
  if (objectToFilmIn >= sourceToFilmIn) {
    throw new BadInput('the object cannot sit at or behind the film', {
      sourceToFilmIn,
      objectToFilmIn,
    });
  }
  return sourceToFilmIn - objectToFilmIn;
}

export function objectToFilm({ sourceToFilmIn, sourceToObjectIn }) {
  requireAbove(sourceToFilmIn, 0, 'source to film distance');
  requireAbove(sourceToObjectIn, 0, 'source to object distance');
  if (sourceToObjectIn > sourceToFilmIn) {
    throw new BadInput('the object cannot sit behind the film', {
      sourceToFilmIn,
      sourceToObjectIn,
    });
  }
  return sourceToFilmIn - sourceToObjectIn;
}

/**
 * How much bigger the image is than the thing. A magnification of 1 is an
 * object lying on the film; anything above that projects an indication longer
 * than it really is, which matters once a length is being judged against a
 * code limit.
 */
export function magnification({ sourceToObjectIn, objectToFilmIn }) {
  requireAbove(sourceToObjectIn, 0, 'source to object distance');
  requireAtLeast(objectToFilmIn, 0, 'object to film distance');
  return (sourceToObjectIn + objectToFilmIn) / sourceToObjectIn;
}

export function projectedLength(actualIn, factor) {
  requireAtLeast(actualIn, 0, 'actual length');
  requireAtLeast(factor, 1, 'magnification');
  return actualIn * factor;
}

/**
 * Back out the real length from what was measured on the film. Every length
 * judged against an acceptance limit goes through here first.
 */
export function actualLength(measuredIn, factor) {
  requireAtLeast(measuredIn, 0, 'measured length');
  requireAtLeast(factor, 1, 'magnification');
  return measuredIn / factor;
}

/**
 * The complete distance picture from any two of the three, so a caller can
 * hand over whatever the sheet happens to record.
 */
export function resolveDistances(given) {
  const has = (key) => given[key] !== undefined && given[key] !== null;
  const count = ['sourceToFilmIn', 'sourceToObjectIn', 'objectToFilmIn'].filter(has).length;
  if (count < 2) {
    throw new BadInput('two of the three distances are needed', { given });
  }

  let sfd;
  let sod;
  let ofd;
  if (has('sourceToObjectIn') && has('objectToFilmIn')) {
    sod = given.sourceToObjectIn;
    ofd = given.objectToFilmIn;
    sfd = sourceToFilm({ sourceToObjectIn: sod, objectToFilmIn: ofd });
  } else if (has('sourceToFilmIn') && has('objectToFilmIn')) {
    sfd = given.sourceToFilmIn;
    ofd = given.objectToFilmIn;
    sod = sourceToObject({ sourceToFilmIn: sfd, objectToFilmIn: ofd });
  } else {
    sfd = given.sourceToFilmIn;
    sod = given.sourceToObjectIn;
    ofd = objectToFilm({ sourceToFilmIn: sfd, sourceToObjectIn: sod });
  }

  if (count === 3) {
    const stated = given.sourceToFilmIn;
    const derived = given.sourceToObjectIn + given.objectToFilmIn;
    if (Math.abs(stated - derived) > 1e-6) {
      throw new BadInput('the three distances given do not agree', {
        sourceToFilmIn: stated,
        sum: derived,
      });
    }
  }

  return {
    sourceToFilmIn: round(sfd, 4),
    sourceToObjectIn: round(sod, 4),
    objectToFilmIn: round(ofd, 4),
    magnification: round(magnification({ sourceToObjectIn: sod, objectToFilmIn: ofd }), 4),
  };
}
