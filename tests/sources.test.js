import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, dirname, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * Byte hygiene over the whole tree.
 *
 * This exists because the checksum module shipped with a literal NUL byte
 * where a separator was meant. The code worked and every test passed. Git
 * classified the file as binary, stopped diffing it, and the line counts in
 * review were wrong from then on. Nothing in the ordinary suite could see it,
 * because the tests were testing behaviour and the behaviour was fine.
 *
 * The checker refuses to pass on an empty file list, and it carries planted
 * inputs for every rule it applies, so a walk that silently finds nothing or
 * a predicate that has stopped matching reports failure rather than success.
 */

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, '..');

function walk(directory) {
  const found = [];
  for (const entry of readdirSync(directory)) {
    if (entry === 'node_modules' || entry === 'coverage' || entry.startsWith('.')) continue;
    const full = join(directory, entry);
    if (statSync(full).isDirectory()) {
      found.push(...walk(full));
    } else if (entry.endsWith('.js') || entry.endsWith('.mjs')) {
      found.push(full);
    }
  }
  return found;
}

const files = ['src', 'tests', 'bin', 'scripts']
  .map((name) => join(root, name))
  .filter((path) => {
    try {
      return statSync(path).isDirectory();
    } catch {
      return false;
    }
  })
  .flatMap((path) => walk(path));

// The whole job of this file is to look for control characters, so the
// rule that bans naming them in a pattern is the one rule it has to be
// exempt from. The escape sequences are deliberate and are checked below.
// eslint-disable-next-line no-control-regex
const CONTROL = /[\u0000-\u0008\u000b-\u000c\u000e-\u001f]/;
const NUL = '\u0000';
const BACKSPACE = '\u0008';
const BOM = '\ufeff';

function offendersBy(predicate) {
  return files
    .filter((file) => predicate(readFileSync(file, 'utf8')))
    .map((file) => relative(root, file));
}

describe('the source sweep', () => {
  it('found files to check, which is the point of saying so', () => {
    expect(files.length).toBeGreaterThan(40);
  });

  it('reads every one of them', () => {
    for (const file of files) {
      expect(readFileSync(file, 'utf8').length).toBeGreaterThan(0);
    }
  });
});

describe('every source file', () => {
  it('carries no control characters outside tab and newline', () => {
    expect(offendersBy((text) => CONTROL.test(text))).toEqual([]);
  });

  it('carries no carriage returns', () => {
    expect(offendersBy((text) => text.includes('\r'))).toEqual([]);
  });

  it('carries no byte order mark', () => {
    expect(offendersBy((text) => text.charCodeAt(0) === 0xfeff)).toEqual([]);
  });

  it('ends with exactly one newline', () => {
    expect(offendersBy((text) => !text.endsWith('\n') || text.endsWith('\n\n'))).toEqual([]);
  });

  it('leaves no trailing whitespace on any line', () => {
    expect(
      offendersBy((text) => text.split('\n').some((line) => /[ \t]+$/.test(line)))
    ).toEqual([]);
  });
});

describe('the predicates themselves', () => {
  it('catches a planted control character', () => {
    expect(CONTROL.test('a' + NUL + 'b')).toBe(true);
    expect(CONTROL.test('a' + BACKSPACE + 'b')).toBe(true);
  });

  it('lets tab and newline through', () => {
    expect(CONTROL.test('a\tb\nc')).toBe(false);
  });

  it('catches a planted carriage return', () => {
    expect('a\r\nb'.includes('\r')).toBe(true);
  });

  it('catches a planted byte order mark', () => {
    expect((BOM + 'import').charCodeAt(0)).toBe(0xfeff);
  });

  it('catches planted trailing whitespace', () => {
    expect(/[ \t]+$/.test('const a = 1; ')).toBe(true);
    expect(/[ \t]+$/.test('const a = 1;')).toBe(false);
  });

  it('would have caught the bug that caused this file to exist', () => {
    expect(CONTROL.test('fnv1a32(base + ' + NUL + ' + text)')).toBe(true);
  });
});
