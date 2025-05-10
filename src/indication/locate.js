import { BadInput } from '../errors.js';
import { round } from '../util/num.js';
import { requireArray, requireAbove, requireAtLeast } from '../util/guard.js';
import { circumference, clockFromDegrees, degreesFromClock } from '../geom/pipe.js';

/**
 * Putting an indication back on the pipe.
 *
 * Everything a reader measures is measured on the film, from the location
 * marker, in film inches. The repair crew works in inches along the weld from
 * the top of the pipe, in clock positions. Two things happen between those.
 *
 * The film is magnified, so a distance measured on it is longer than the
 * distance on the pipe by exactly the magnification, and dividing by it is
 * not optional on any double wall shot. And the weld is a closed loop, so a
 * position past the end of the circumference wraps round rather than running
 * off, which is what makes an indication near the top of the pipe read as
 * 11:58 rather than as a position past twelve o'clock.
 */

export function weldPositionOf({ markerAtIn, alongFilmIn, magnification = 1, odIn }) {
  requireAtLeast(markerAtIn, 0, 'marker position');
  requireAtLeast(alongFilmIn, 0, 'distance along the film');
  requireAtLeast(magnification, 1, 'magnification');
  requireAbove(odIn, 0, 'outside diameter');

  const around = circumference(odIn);
  const raw = markerAtIn + alongFilmIn / magnification;
  const alongWeldIn = round(((raw % around) + around) % around, 4);
  const degrees = round((alongWeldIn / around) * 360, 3);
  return { alongWeldIn, degrees, clock: clockFromDegrees(degrees), wrapped: raw >= around };
}

/** The inverse: where on the film a known weld position falls. */
export function filmPositionOf({ markerAtIn, alongWeldIn, magnification = 1, odIn }) {
  requireAtLeast(markerAtIn, 0, 'marker position');
  requireAtLeast(alongWeldIn, 0, 'position along the weld');
  requireAtLeast(magnification, 1, 'magnification');
  requireAbove(odIn, 0, 'outside diameter');

  const around = circumference(odIn);
  const forward = (((alongWeldIn - markerAtIn) % around) + around) % around;
  return round(forward * magnification, 4);
}

/** A marker position given as a clock reading rather than as inches. */
export function markerFromClock(clock, odIn) {
  requireAbove(odIn, 0, 'outside diameter');
  return round((degreesFromClock(clock) / 360) * circumference(odIn), 4);
}

/**
 * Locate a whole set of indications read off one film. Each keeps everything
 * it had and gains its position on the weld, so nothing measured is lost in
 * the translation.
 */
export function locateAll(indications, context) {
  requireArray(indications, 'indications');
  return indications.map((indication) => {
    const alongFilmIn = indication.alongFilmIn ?? indication.at;
    if (typeof alongFilmIn !== 'number') {
      throw new BadInput('every indication needs a position measured on the film', {
        got: indication,
      });
    }
    const placed = weldPositionOf({
      ...context,
      alongFilmIn,
      magnification: indication.magnification ?? context.magnification ?? 1,
    });
    return {
      ...indication,
      at: placed.alongWeldIn,
      clock: placed.clock,
      degrees: placed.degrees,
    };
  });
}

/**
 * Whether two positions on the weld are the same place, going the short way
 * round. Used when a repair is matched back to the indication that caused it.
 */
export function samePlace({ a, b, odIn, toleranceIn = 0.5 }) {
  requireAbove(odIn, 0, 'outside diameter');
  requireAtLeast(toleranceIn, 0, 'tolerance');
  const around = circumference(odIn);
  const forward = (((b - a) % around) + around) % around;
  return Math.min(forward, around - forward) <= toleranceIn;
}

/**
 * The span an indication occupies on the weld, as two clock positions. This
 * is what goes on the repair sketch, and it is the form a crew can chalk.
 */
export function spanOf({ atIn, lengthIn, odIn }) {
  requireAtLeast(atIn, 0, 'position');
  requireAtLeast(lengthIn, 0, 'length');
  requireAbove(odIn, 0, 'outside diameter');
  const around = circumference(odIn);
  const start = ((atIn % around) + around) % around;
  const end = (((start + lengthIn) % around) + around) % around;
  return {
    fromIn: round(start, 4),
    toIn: round(end, 4),
    from: clockFromDegrees((start / around) * 360),
    to: clockFromDegrees((end / around) * 360),
    crossesTop: start + lengthIn >= around,
  };
}
