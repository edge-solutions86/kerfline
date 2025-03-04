import { BadInput } from '../errors.js';
import { requireArray } from '../util/guard.js';
import { crc32, stableStringify } from '../util/checksum.js';

/**
 * Handing records to somebody else.
 *
 * Clients ask for CSV and their systems read it with whatever is nearest to
 * hand, so the quoting here is the conservative form: a field is quoted if it
 * could possibly need to be, and a quote inside a field is doubled. Being
 * generous with quotes costs a few bytes and being clever with them costs an
 * afternoon on the phone.
 *
 * Every export carries a CRC32 of its own body on the last line. That is not
 * a signature and does not pretend to be. It catches the file that arrived
 * truncated, which is the failure that actually happens, and it is a checksum
 * the receiving end can already compute without installing anything.
 */

const NEEDS_QUOTING = /[",\r\n]/;

export function csvField(value) {
  if (value === null || value === undefined) return '';
  const text = String(value);
  if (!NEEDS_QUOTING.test(text)) return text;
  return `"${text.replace(/"/g, '""')}"`;
}

export function csvRow(values) {
  requireArray(values, 'values');
  return values.map((value) => csvField(value)).join(',');
}

/**
 * Rows to CSV. Columns are given explicitly rather than taken from the first
 * row, so a row missing a field produces an empty cell instead of shifting
 * every column after it.
 */
export function toCsv(rows, columns, options = {}) {
  requireArray(rows, 'rows');
  requireArray(columns, 'columns');
  if (columns.length === 0) {
    throw new BadInput('an export needs at least one column', { columns });
  }
  const keys = columns.map((column) => (typeof column === 'string' ? column : column.key));
  const labels = columns.map((column) =>
    typeof column === 'string' ? column : (column.label ?? column.key)
  );
  keys.forEach((key, index) => {
    if (typeof key !== 'string' || key === '') {
      throw new BadInput(`column ${index + 1} has no key`, { column: columns[index] });
    }
  });

  const lines = [];
  if (options.header !== false) lines.push(csvRow(labels));
  for (const row of rows) {
    lines.push(csvRow(keys.map((key) => row[key])));
  }
  return lines.join('\n');
}

/** JSON with sorted keys, so two exports of the same records compare equal. */
export function toJson(value, { pretty = false } = {}) {
  const text = stableStringify(value);
  return pretty ? JSON.stringify(JSON.parse(text), null, 2) : text;
}

/**
 * Append the checksum line. The body is checksummed as it stands, so the
 * receiving end strips the last line and checks the rest.
 */
export function withChecksum(body, { label = 'crc32' } = {}) {
  if (typeof body !== 'string') {
    throw new BadInput('an export body must be text', { got: body });
  }
  return `${body}\n# ${label} ${crc32(body)}`;
}

/** Check an export that arrived with a checksum line on it. */
export function verifyChecksum(text) {
  if (typeof text !== 'string') {
    throw new BadInput('an export must be text', { got: text });
  }
  const index = text.lastIndexOf('\n# ');
  if (index === -1) {
    return { checked: false, intact: false, reason: 'no checksum line' };
  }
  const body = text.slice(0, index);
  const line = text.slice(index + 3).trim();
  const parts = line.split(/\s+/);
  if (parts.length !== 2) {
    return { checked: false, intact: false, reason: 'the checksum line is malformed' };
  }
  const expected = crc32(body);
  return {
    checked: true,
    intact: parts[1] === expected,
    expected,
    found: parts[1],
    reason: parts[1] === expected ? null : 'the body does not match its checksum',
  };
}

/** The columns a client register export carries, in the order they expect. */
export function registerColumns() {
  return [
    { key: 'id', label: 'joint' },
    { key: 'spool', label: 'spool' },
    { key: 'welder', label: 'welder' },
    { key: 'nominalSize', label: 'size' },
    { key: 'schedule', label: 'sch' },
    { key: 'wallIn', label: 'wall' },
    { key: 'day', label: 'shot' },
    { key: 'disposition', label: 'result' },
    { key: 'clause', label: 'clause' },
  ];
}

/** A whole register export, checksummed and ready to send. */
export function exportRegister(rows) {
  return withChecksum(toCsv(rows, registerColumns()));
}
