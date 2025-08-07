import { BadInput } from '../errors.js';
import { requireArray } from '../util/guard.js';
import { rule, padCenter } from '../util/text.js';

/**
 * The shape every report on this shop's paper has.
 *
 * Six report modules had each grown the same four lines: a page width, a
 * centred title over a double rule, a section title over a single one, and a
 * join with blank lines between. They had already drifted, which showed up as
 * two reports printing at different widths for no reason anybody could name.
 *
 * The width is a parameter rather than a constant because the register really
 * does need a wider page than the interpretation sheet, and pretending
 * otherwise would mean either a cramped register or a lot of white space on
 * everything else.
 */

const DEFAULT_WIDTH = 78;
const WIDE = 96;

export function pageWidth() {
  return DEFAULT_WIDTH;
}

export function widePageWidth() {
  return WIDE;
}

function checkWidth(width) {
  if (!Number.isInteger(width) || width < 20 || width > 200) {
    throw new BadInput('a page is between 20 and 200 columns', { width });
  }
  return width;
}

/** The title at the head of a report, centred over a double rule. */
export function heading(title, width = DEFAULT_WIDTH) {
  checkWidth(width);
  if (typeof title !== 'string' || title.trim() === '') {
    throw new BadInput('a report has a title', { got: title });
  }
  return [padCenter(title, width), rule(width, '=')].join('\n');
}

/** A section: its title, a single rule, then whatever it holds. */
export function block(title, body, width = DEFAULT_WIDTH) {
  checkWidth(width);
  if (typeof title !== 'string' || title.trim() === '') {
    throw new BadInput('a section has a title', { got: title });
  }
  return [title, rule(width), body === '' || body === null ? '' : body]
    .filter((part) => part !== '')
    .join('\n');
}

/** A section that has nothing to show, which still gets printed. */
export function emptyBlock(title, message, width = DEFAULT_WIDTH) {
  return block(title, message, width);
}

/** A line across the page, for a draft mark or a warning. */
export function banner(text, width = DEFAULT_WIDTH) {
  checkWidth(width);
  return padCenter(text, width);
}

/** Join the parts of a page with one blank line between each. */
export function page(blocks) {
  requireArray(blocks, 'blocks');
  return blocks
    .filter((part) => part !== null && part !== undefined && part !== '')
    .join('\n\n');
}

/**
 * Check a rendered page fits. Reports get faxed, and a line that runs over
 * comes out wrapped in a way that makes a table unreadable, so this is worth
 * asserting on rather than eyeballing.
 */
export function fitsWidth(text, width = DEFAULT_WIDTH) {
  checkWidth(width);
  return String(text)
    .split('\n')
    .every((line) => line.length <= width);
}

/** The lines that do not fit, for a test that wants to name them. */
export function overlongLines(text, width = DEFAULT_WIDTH) {
  checkWidth(width);
  return String(text)
    .split('\n')
    .map((line, index) => ({ line: index + 1, length: line.length, text: line }))
    .filter((entry) => entry.length > width);
}
