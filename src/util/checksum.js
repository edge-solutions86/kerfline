import { BadInput } from '../errors.js';

/**
 * Checksums for record integrity.
 *
 * These are not cryptographic and are not trying to be. What they are for is
 * noticing that a record changed between being written and being read back,
 * which on this system means a file got edited by hand or a copy went wrong.
 * Somebody determined to forge a record can forge these too, and the defence
 * against that is the paper copy in the job file, not arithmetic.
 *
 * Both are here because they answer to different habits. FNV is what the
 * journal chains with because it is quick and the chaining is what carries
 * the weight. CRC32 is what the export files carry, because the people
 * receiving them already check CRC32 and will not install anything to check
 * something else.
 */

const FNV_OFFSET = 2166136261;
const FNV_PRIME = 16777619;

function requireText(value, what) {
  if (typeof value !== 'string') {
    throw new BadInput(`${what} must be a string`, { got: value });
  }
  return value;
}

function hex32(value) {
  return (value >>> 0).toString(16).padStart(8, '0');
}

/** FNV-1a over the UTF-16 code units, returned as eight hex digits. */
export function fnv1a32(text) {
  requireText(text, 'text');
  let hash = FNV_OFFSET;
  for (let i = 0; i < text.length; i += 1) {
    hash ^= text.charCodeAt(i);
    hash = Math.imul(hash, FNV_PRIME);
  }
  return hex32(hash);
}

const CRC_TABLE = (() => {
  const table = new Int32Array(256);
  for (let n = 0; n < 256; n += 1) {
    let c = n;
    for (let k = 0; k < 8; k += 1) {
      c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    table[n] = c;
  }
  return table;
})();

/** CRC32 over the UTF-8 bytes, returned as eight hex digits. */
export function crc32(text) {
  requireText(text, 'text');
  const bytes = new TextEncoder().encode(text);
  let crc = -1;
  for (let i = 0; i < bytes.length; i += 1) {
    crc = CRC_TABLE[(crc ^ bytes[i]) & 0xff] ^ (crc >>> 8);
  }
  return hex32(crc ^ -1);
}

/**
 * Chain a checksum onto the one before it. This is what makes a journal
 * tamper evident rather than merely checksummed: changing an entry in the
 * middle invalidates every entry after it, so the damage cannot be repaired
 * by recomputing one line.
 */
const SEPARATOR = '\u0000';

export function chain(previous, text) {
  requireText(text, 'text');
  const base =
    previous === null || previous === undefined ? '' : requireText(previous, 'previous');
  return fnv1a32(base + SEPARATOR + text);
}

/**
 * A stable key for a record, from its own contents. Object keys are sorted so
 * that two records with the same fields written in a different order get the
 * same id, which they should.
 */
export function digestOf(value, length = 8) {
  if (!Number.isInteger(length) || length < 4 || length > 8) {
    throw new BadInput('a digest is between four and eight characters', { length });
  }
  return fnv1a32(stableStringify(value)).slice(0, length);
}

/** JSON with object keys in sorted order, so the text is canonical. */
export function stableStringify(value) {
  if (value === null || typeof value !== 'object') return JSON.stringify(value) ?? 'null';
  if (Array.isArray(value)) {
    return `[${value.map((item) => stableStringify(item)).join(',')}]`;
  }
  const keys = Object.keys(value).sort();
  const parts = keys
    .filter((key) => value[key] !== undefined)
    .map((key) => `${JSON.stringify(key)}:${stableStringify(value[key])}`);
  return `{${parts.join(',')}}`;
}
