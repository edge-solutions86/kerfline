import { BadInput } from '../errors.js';

/**
 * Fixed width text. Interpretation sheets and technique sheets get printed on
 * a shop printer and faxed to a client, so the output has to be columns of
 * monospace text rather than anything that needs a browser.
 */

export function padRight(text, width, fill = ' ') {
  return pad(text, width, fill, 'right');
}

export function padLeft(text, width, fill = ' ') {
  return pad(text, width, fill, 'left');
}

export function padCenter(text, width, fill = ' ') {
  return pad(text, width, fill, 'center');
}

function pad(text, width, fill, side) {
  const body = String(text ?? '');
  if (!Number.isInteger(width) || width < 0) {
    throw new BadInput('width must be a whole number of columns', { width });
  }
  if (typeof fill !== 'string' || fill.length !== 1) {
    throw new BadInput('fill must be a single character', { fill });
  }
  if (body.length >= width) return body;
  const short = width - body.length;
  if (side === 'right') return body + fill.repeat(short);
  if (side === 'left') return fill.repeat(short) + body;
  const before = Math.floor(short / 2);
  return fill.repeat(before) + body + fill.repeat(short - before);
}

/** Cut a string to width, marking the cut so nobody reads a truncated id. */
export function truncate(text, width) {
  const body = String(text ?? '');
  if (!Number.isInteger(width) || width < 1) {
    throw new BadInput('width must be at least one column', { width });
  }
  if (body.length <= width) return body;
  if (width === 1) return '~';
  return `${body.slice(0, width - 1)}~`;
}

export function rule(width, char = '-') {
  if (!Number.isInteger(width) || width < 0) {
    throw new BadInput('width must be a whole number of columns', { width });
  }
  if (typeof char !== 'string' || char.length !== 1) {
    throw new BadInput('rule character must be a single character', { char });
  }
  return char.repeat(width);
}

/**
 * Render rows as a column table. Columns are
 * { key, label, align, format, width }; width is a floor, the column grows to
 * fit its widest cell, and anything wider than an explicit max gets cut.
 */
export function renderTable(rows, columns, options = {}) {
  if (!Array.isArray(rows)) throw new BadInput('rows must be an array', { got: rows });
  if (!Array.isArray(columns) || columns.length === 0) {
    throw new BadInput('a table needs at least one column', { got: columns });
  }
  const gap = options.gap ?? 2;
  const cells = columns.map((col) => {
    if (typeof col.key !== 'string' || col.key === '') {
      throw new BadInput('every column needs a key', { col });
    }
    const format = col.format ?? ((v) => (v === null || v === undefined ? '' : String(v)));
    const body = rows.map((row) => {
      const text = format(row[col.key], row);
      return col.max ? truncate(text, col.max) : text;
    });
    const label = col.label ?? col.key;
    const width = Math.max(col.width ?? 0, label.length, ...body.map((b) => b.length));
    return { col, label, body, width };
  });

  const separator = ' '.repeat(gap);
  const line = (parts) => parts.join(separator).replace(/\s+$/, '');
  const header = line(cells.map((c) => align(c.label, c.width, c.col.align)));
  const divider = line(cells.map((c) => rule(c.width)));
  const body = rows.map((_row, i) =>
    line(cells.map((c) => align(c.body[i], c.width, c.col.align)))
  );

  const out = options.header === false ? body : [header, divider, ...body];
  return out.join('\n');
}

function align(text, width, how) {
  if (how === 'right') return padLeft(text, width);
  if (how === 'center') return padCenter(text, width);
  return padRight(text, width);
}

/** Wrap prose to a width, breaking on spaces and keeping existing breaks. */
export function wrap(text, width) {
  if (!Number.isInteger(width) || width < 1) {
    throw new BadInput('width must be at least one column', { width });
  }
  const out = [];
  for (const paragraph of String(text).split('\n')) {
    let current = '';
    for (const word of paragraph.split(/\s+/).filter((w) => w !== '')) {
      if (current === '') {
        current = word;
      } else if (current.length + 1 + word.length <= width) {
        current = `${current} ${word}`;
      } else {
        out.push(current);
        current = word;
      }
    }
    out.push(current);
  }
  return out.join('\n');
}

export function indent(text, spaces = 2) {
  if (!Number.isInteger(spaces) || spaces < 0) {
    throw new BadInput('indent must be a whole number of columns', { spaces });
  }
  const prefix = ' '.repeat(spaces);
  return String(text)
    .split('\n')
    .map((line) => (line === '' ? line : prefix + line))
    .join('\n');
}

/** A label and value block, labels right padded to line the values up. */
export function keyValueBlock(pairs, options = {}) {
  const entries = Array.isArray(pairs) ? pairs : Object.entries(pairs ?? {});
  if (entries.length === 0) return '';
  const gap = options.gap ?? 2;
  const width = Math.max(...entries.map(([k]) => String(k).length));
  return entries
    .map(([k, v]) => `${padRight(String(k), width)}${' '.repeat(gap)}${v ?? ''}`)
    .map((line) => line.replace(/\s+$/, ''))
    .join('\n');
}
