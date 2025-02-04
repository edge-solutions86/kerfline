import { describe, it, expect } from 'vitest';
import { fnv1a32, crc32, chain, digestOf, stableStringify } from '../../src/util/checksum.js';

describe('fnv1a32', () => {
  it('is eight hex digits', () => {
    expect(fnv1a32('J-118')).toMatch(/^[0-9a-f]{8}$/);
  });

  it('is stable for the same text', () => {
    expect(fnv1a32('J-118')).toBe(fnv1a32('J-118'));
  });

  it('changes on a one character difference', () => {
    expect(fnv1a32('J-118')).not.toBe(fnv1a32('J-119'));
  });

  it('hashes the empty string to the offset basis', () => {
    expect(fnv1a32('')).toBe('811c9dc5');
  });

  it('matches the published vector for a known input', () => {
    expect(fnv1a32('a')).toBe('e40c292c');
    expect(fnv1a32('foobar')).toBe('bf9cf968');
  });

  it('refuses anything that is not a string', () => {
    expect(() => fnv1a32(118)).toThrow(/must be a string/);
  });
});

describe('crc32', () => {
  it('is eight hex digits', () => {
    expect(crc32('J-118')).toMatch(/^[0-9a-f]{8}$/);
  });

  it('matches the published vector', () => {
    expect(crc32('123456789')).toBe('cbf43926');
  });

  it('is zero over nothing', () => {
    expect(crc32('')).toBe('00000000');
  });

  it('differs from the FNV hash of the same text', () => {
    expect(crc32('J-118')).not.toBe(fnv1a32('J-118'));
  });

  it('handles text outside the ASCII range', () => {
    expect(crc32('rohrleitung 12 zoll')).toMatch(/^[0-9a-f]{8}$/);
  });
});

describe('chain', () => {
  it('starts from nothing on the first entry', () => {
    expect(chain(null, 'first')).toBe(chain(undefined, 'first'));
  });

  it('depends on what came before', () => {
    const a = chain(null, 'first');
    expect(chain(a, 'second')).not.toBe(chain(null, 'second'));
  });

  it('is stable for the same pair', () => {
    const a = chain(null, 'first');
    expect(chain(a, 'second')).toBe(chain(a, 'second'));
  });

  it('separates the parts so two splits do not collide', () => {
    expect(chain('ab', 'c')).not.toBe(chain('a', 'bc'));
  });

  it('refuses a previous value that is not a string', () => {
    expect(() => chain(42, 'text')).toThrow(/must be a string/);
  });
});

describe('stableStringify', () => {
  it('sorts object keys', () => {
    expect(stableStringify({ b: 1, a: 2 })).toBe('{"a":2,"b":1}');
  });

  it('gives the same text whatever order the keys were written in', () => {
    expect(stableStringify({ joint: 'J-1', wall: 0.28 })).toBe(
      stableStringify({ wall: 0.28, joint: 'J-1' })
    );
  });

  it('sorts nested objects too', () => {
    const one = stableStringify({ outer: { b: 1, a: 2 } });
    const two = stableStringify({ outer: { a: 2, b: 1 } });
    expect(one).toBe(two);
  });

  it('keeps array order, which is meaningful', () => {
    expect(stableStringify([1, 2])).not.toBe(stableStringify([2, 1]));
  });

  it('drops undefined fields rather than writing them', () => {
    expect(stableStringify({ a: 1, b: undefined })).toBe('{"a":1}');
  });

  it('handles the scalars', () => {
    expect(stableStringify(null)).toBe('null');
    expect(stableStringify(42)).toBe('42');
    expect(stableStringify('x')).toBe('"x"');
    expect(stableStringify(true)).toBe('true');
  });
});

describe('digestOf', () => {
  it('is the same for records written in a different key order', () => {
    expect(digestOf({ joint: 'J-1', wall: 0.28 })).toBe(digestOf({ wall: 0.28, joint: 'J-1' }));
  });

  it('differs when a value differs', () => {
    expect(digestOf({ joint: 'J-1' })).not.toBe(digestOf({ joint: 'J-2' }));
  });

  it('takes a length', () => {
    expect(digestOf({ joint: 'J-1' }, 4)).toHaveLength(4);
    expect(digestOf({ joint: 'J-1' }, 8)).toHaveLength(8);
  });

  it('is a prefix of the longer digest', () => {
    const long = digestOf({ joint: 'J-1' }, 8);
    expect(long.startsWith(digestOf({ joint: 'J-1' }, 4))).toBe(true);
  });

  it('refuses a silly length', () => {
    expect(() => digestOf({}, 3)).toThrow(/between four and eight/);
    expect(() => digestOf({}, 9)).toThrow(/between four and eight/);
  });
});
