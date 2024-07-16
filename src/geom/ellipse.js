import { BadInput } from '../errors.js';
import { isFiniteNumber, round } from '../util/num.js';
import { isSmallBore } from './pipe.js';

/**
 * The elliptical shot on small bore.
 *
 * The source is pushed off the plane of the weld so that the near wall and
 * the far wall land on the film in different places instead of on top of each
 * other. Put the pipe axis along x with the weld at x = 0, the source at
 * axial offset O and standoff g above the pipe, and the film flat under the
 * pipe. The far wall sits on the film and images at x = 0. The near wall
 * images at
 *
 *     x = OD * O / g
 *
 * so the separation between the two weld images is OD * O / g, and the offset
 * a crew needs for a wanted separation is g * S / OD. That is the whole
 * calculation, and it is worth writing down because the rule of thumb people
 * carry around ("about an inch back") is wrong on both ends of the range.
 */

export function imageSeparationIn({ odIn, sourceToObjectIn, offsetIn }) {
  requireAbove(odIn, 0, 'outside diameter');
  requireAbove(sourceToObjectIn, 0, 'source to object distance');
  requireAtLeast(offsetIn, 0, 'source offset');
  return (odIn * offsetIn) / sourceToObjectIn;
}

export function offsetForSeparation({ odIn, sourceToObjectIn, separationIn }) {
  requireAbove(odIn, 0, 'outside diameter');
  requireAbove(sourceToObjectIn, 0, 'source to object distance');
  requireAtLeast(separationIn, 0, 'wanted separation');
  return (sourceToObjectIn * separationIn) / odIn;
}

/**
 * The shop wants the two weld images clear of each other by at least one weld
 * width and not so far apart that the near wall runs off the film. Both ends
 * are procedure numbers rather than code ones.
 */
export function separationWindowIn(weldWidthIn) {
  requireAbove(weldWidthIn, 0, 'weld width');
  return { minimumIn: weldWidthIn, maximumIn: weldWidthIn * 3 };
}

/**
 * Plan an elliptical shot: the offset to use, the window it has to land in,
 * and whether the geometry works at all. When it does not the caller falls
 * back to a superimposed shot, which is the fork selectViewing takes.
 */
export function planEllipse({ odIn, wallIn, sourceToObjectIn, weldWidthIn, filmLengthIn }) {
  requireAbove(odIn, 0, 'outside diameter');
  requireAbove(wallIn, 0, 'wall thickness');
  requireAbove(filmLengthIn, 0, 'film length');
  const window = separationWindowIn(weldWidthIn);
  const wanted = (window.minimumIn + window.maximumIn) / 2;
  const offsetIn = offsetForSeparation({ odIn, sourceToObjectIn, separationIn: wanted });
  const separationIn = imageSeparationIn({ odIn, sourceToObjectIn, offsetIn });

  // The film has to hold the far wall image, the separation and the near wall
  // image, with the weld width on each. Anything tighter than that and the
  // two images cannot both be read.
  const neededIn = separationIn + 2 * weldWidthIn;
  const fits = neededIn <= filmLengthIn;

  return {
    smallBore: isSmallBore(odIn),
    offsetIn: round(offsetIn, 3),
    separationIn: round(separationIn, 3),
    windowIn: { minimumIn: round(window.minimumIn, 3), maximumIn: round(window.maximumIn, 3) },
    filmNeededIn: round(neededIn, 3),
    fitsOnFilm: fits,
    usable: fits && isSmallBore(odIn),
  };
}

/** True when a stated offset lands the separation inside the shop window. */
export function offsetIsAcceptable({ odIn, sourceToObjectIn, offsetIn, weldWidthIn }) {
  const separation = imageSeparationIn({ odIn, sourceToObjectIn, offsetIn });
  const window = separationWindowIn(weldWidthIn);
  return separation >= window.minimumIn && separation <= window.maximumIn;
}

function requireAbove(value, floor, what) {
  requireNumber(value, what);
  if (value <= floor) {
    throw new BadInput(`${what} must be greater than ${floor}`, { got: value });
  }
  return value;
}

function requireAtLeast(value, floor, what) {
  requireNumber(value, what);
  if (value < floor) {
    throw new BadInput(`${what} must not be below ${floor}`, { got: value });
  }
  return value;
}

function requireNumber(value, what) {
  if (!isFiniteNumber(value)) {
    throw new BadInput(`${what} must be a finite number`, { got: value });
  }
  return value;
}
