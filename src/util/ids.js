import { BadInput } from '../errors.js';

/**
 * Identifiers. Joints, films, reports and welder stencils all get written by
 * hand on a lead tape or a film jacket before they ever reach a keyboard, so
 * the parser here has to accept what a marker pen produces and the formatter
 * has to produce something that survives being read back off a light box.
 */

const SEQUENCE = /^([A-Z]{1,4})[-\s]?(\d{1,6})$/;

/** Format a prefixed sequence number, zero padded to a fixed width. */
export function sequence(prefix, number, width = 3) {
  if (typeof prefix !== 'string' || !/^[A-Za-z]{1,4}$/.test(prefix)) {
    throw new BadInput('prefix must be one to four letters', { prefix });
  }
  if (!Number.isInteger(number) || number < 0) {
    throw new BadInput('sequence number must be a whole number', { number });
  }
  if (!Number.isInteger(width) || width < 1 || width > 6) {
    throw new BadInput('width must be between one and six', { width });
  }
  return `${prefix.toUpperCase()}-${String(number).padStart(width, '0')}`;
}

/** Read a sequence back, accepting a dash, a space or nothing between. */
export function parseSequence(text) {
  if (typeof text !== 'string') {
    throw new BadInput('identifier must be a string', { got: text });
  }
  const match = SEQUENCE.exec(text.trim().toUpperCase());
  if (!match) {
    throw new BadInput(`cannot read ${text} as a prefixed sequence`, { text });
  }
  return { prefix: match[1], number: Number(match[2]) };
}

export function isSequence(text) {
  try {
    parseSequence(text);
    return true;
  } catch {
    return false;
  }
}

/** The next number in the same series, keeping the written width. */
export function nextInSequence(text) {
  const { prefix, number } = parseSequence(text);
  const digits = text.trim().replace(/^[A-Za-z]{1,4}[-\s]?/, '').length;
  return sequence(prefix, number + 1, Math.max(1, digits));
}

/**
 * Sort identifiers the way a register reads: J-2 before J-10, and letters
 * before digits. A plain string sort puts J-10 above J-2 and a technician
 * looking for a joint gives up on the list.
 */
export function compareIds(a, b) {
  const left = chunks(String(a));
  const right = chunks(String(b));
  const shared = Math.min(left.length, right.length);
  for (let i = 0; i < shared; i += 1) {
    const x = left[i];
    const y = right[i];
    if (typeof x === typeof y) {
      if (x < y) return -1;
      if (x > y) return 1;
    } else {
      return typeof x === 'number' ? -1 : 1;
    }
  }
  return left.length - right.length;
}

function chunks(text) {
  return text
    .toUpperCase()
    .split(/(\d+)/)
    .filter((part) => part !== '')
    .map((part) => (/^\d+$/.test(part) ? Number(part) : part));
}

export function sortIds(ids) {
  if (!Array.isArray(ids)) throw new BadInput('ids must be an array', { got: ids });
  return [...ids].sort(compareIds);
}

/**
 * A welder stencil. These are short, they are stamped into the pipe next to
 * the weld, and the shop allows letters and digits only so that a rubbing
 * taken off the stamp is unambiguous.
 */
export function requireStencil(text) {
  if (typeof text !== 'string' || !/^[A-Z0-9]{1,6}$/.test(text.trim().toUpperCase())) {
    throw new BadInput('a welder stencil is one to six letters or digits', { got: text });
  }
  return text.trim().toUpperCase();
}

export function isStencil(text) {
  try {
    requireStencil(text);
    return true;
  } catch {
    return false;
  }
}

/** A filesystem safe key for a record, derived from whatever it is called. */
export function slug(text) {
  const body = String(text ?? '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
  if (body === '') throw new BadInput('cannot slug an empty name', { got: text });
  return body;
}
