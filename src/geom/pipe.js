import { BadInput } from '../errors.js';
import { round } from '../util/num.js';
import { requireAbove, requireNumber } from '../util/guard.js';

/**
 * Pipe geometry, and the clock face the whole trade locates things on.
 *
 * An indication is never reported at "212 degrees". It is reported at 7:04,
 * measured from the top of the pipe with the crew looking downstream, and the
 * conversion between the two has to be exact in both directions or the repair
 * crew grinds out the wrong six inches of weld.
 */

export function outsideRadius(odIn) {
  requireAbove(odIn, 0, 'outside diameter');
  return odIn / 2;
}

export function insideDiameter(odIn, wallIn) {
  requireAbove(odIn, 0, 'outside diameter');
  requireAbove(wallIn, 0, 'wall thickness');
  if (wallIn * 2 >= odIn) {
    throw new BadInput('two walls do not fit inside the outside diameter', { odIn, wallIn });
  }
  return odIn - 2 * wallIn;
}

export function circumference(odIn) {
  requireAbove(odIn, 0, 'outside diameter');
  return Math.PI * odIn;
}

export function arcLength(odIn, degrees) {
  requireAbove(odIn, 0, 'outside diameter');
  requireNumber(degrees, 'arc in degrees');
  return (circumference(odIn) * degrees) / 360;
}

export function degreesForArc(odIn, arcIn) {
  requireAbove(odIn, 0, 'outside diameter');
  requireNumber(arcIn, 'arc length');
  return (arcIn / circumference(odIn)) * 360;
}

/**
 * Degrees clockwise from top, as a clock position. Minutes are rounded to
 * whole minutes because that is the resolution a crew can mark on a pipe.
 */
export function clockFromDegrees(degrees) {
  requireNumber(degrees, 'position in degrees');
  const wrapped = ((degrees % 360) + 360) % 360;
  const totalMinutes = Math.round((wrapped / 360) * 720);
  const settled = totalMinutes % 720;
  const hour = Math.floor(settled / 60);
  const minute = settled % 60;
  const shown = hour === 0 ? 12 : hour;
  return `${shown}:${String(minute).padStart(2, '0')}`;
}

const CLOCK = /^(\d{1,2}):([0-5]\d)$/;

export function degreesFromClock(text) {
  if (typeof text !== 'string') {
    throw new BadInput('a clock position must be a string like 7:04', { got: text });
  }
  const match = CLOCK.exec(text.trim());
  if (!match) {
    throw new BadInput(`cannot read ${text} as a clock position`, { text });
  }
  const hour = Number(match[1]);
  const minute = Number(match[2]);
  if (hour < 1 || hour > 12) {
    throw new BadInput('a clock hour runs from 1 to 12', { text });
  }
  const totalMinutes = ((hour % 12) * 60 + minute) % 720;
  return (totalMinutes / 720) * 360;
}

/** Distance along the weld from the top of the pipe to a clock position. */
export function arcFromTop(odIn, clock) {
  return arcLength(odIn, degreesFromClock(clock));
}

/**
 * The shorter way round between two clock positions, in degrees. A crew
 * reporting a two inch indication starting at 11:50 and ending at 12:10 has
 * crossed the top of the pipe, and a subtraction gets that badly wrong.
 */
export function clockSpanDegrees(fromClock, toClock) {
  const a = degreesFromClock(fromClock);
  const b = degreesFromClock(toClock);
  const forward = (((b - a) % 360) + 360) % 360;
  return Math.min(forward, 360 - forward);
}

/** Degrees travelled going clockwise, which is how a weld is walked. */
export function clockwiseSpanDegrees(fromClock, toClock) {
  const a = degreesFromClock(fromClock);
  const b = degreesFromClock(toClock);
  return (((b - a) % 360) + 360) % 360;
}

/**
 * Small bore, which is where the elliptical shot lives. The cut off is the
 * shop's, taken from the technique procedure rather than from a code.
 */
export function isSmallBore(odIn) {
  requireAbove(odIn, 0, 'outside diameter');
  return odIn <= 3.5;
}

/** A summary block for the header of a technique sheet. */
export function pipeSummary(odIn, wallIn) {
  const id = insideDiameter(odIn, wallIn);
  return {
    odIn: round(odIn, 3),
    wallIn: round(wallIn, 3),
    idIn: round(id, 3),
    circumferenceIn: round(circumference(odIn), 3),
    smallBore: isSmallBore(odIn),
  };
}
