import { describe, it, expect } from 'vitest';
import { readFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import * as kerfline from '../src/index.js';
import { commandNames } from '../src/cli/commands.js';
import { tableNames } from '../src/cli/tables.js';

/**
 * The README, checked.
 *
 * Documentation goes stale silently, which is the whole problem with it. A
 * name in the README that no longer exists reads exactly like one that does,
 * and nobody finds out until somebody tries it.
 *
 * So every identifier the README mentions in backticks has to be either an
 * export, a command, a table or a file that is really there. That is a much
 * weaker check than "the README is accurate", and it is the part of accuracy
 * that can be checked mechanically, which makes it the part worth checking.
 */

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, '..');
const readme = readFileSync(join(root, 'README.md'), 'utf8');

// Prose is wrapped, so a sentence to look for has to be matched against the
// text with its line breaks flattened. Matching the raw file finds a phrase
// only when the wrapping happens not to have split it, which is a test that
// passes or fails on where the margin fell.
const flat = readme.replace(/\s+/g, ' ');

/** Everything the README puts in backticks. */
function backticked() {
  return [...readme.matchAll(/`([^`\n]+)`/g)].map((match) => match[1].trim());
}

const KNOWN_FILES = new Set(['src/', 'tests/', 'src/index.js', 'bin/kerf.js']);

function isKnown(token) {
  if (Object.prototype.hasOwnProperty.call(kerfline, token)) return true;
  if (KNOWN_FILES.has(token)) return true;
  if (existsSync(join(root, token))) return true;
  return false;
}

describe('the README', () => {
  it('mentions something at all', () => {
    expect(backticked().length).toBeGreaterThan(3);
  });

  it('names only exports and files that exist', () => {
    const unknown = backticked().filter((token) => !isKnown(token));
    expect(unknown).toEqual([]);
  });

  it('names the package and what it is for', () => {
    expect(readme).toMatch(/^# kerfline/);
    expect(flat).toContain('weld inspection');
  });

  it('says which node it needs, and agrees with package.json', () => {
    const pkg = JSON.parse(readFileSync(join(root, 'package.json'), 'utf8'));
    const wanted = pkg.engines.node.replace('>=', '');
    expect(readme).toContain(wanted);
  });

  it('lists only scripts that package.json really has', () => {
    const pkg = JSON.parse(readFileSync(join(root, 'package.json'), 'utf8'));
    const mentioned = [...readme.matchAll(/npm run ([a-z:]+)/g)].map((m) => m[1]);
    expect(mentioned.length).toBeGreaterThan(0);
    for (const script of mentioned) {
      expect(Object.keys(pkg.scripts), `npm run ${script}`).toContain(script);
    }
  });

  it('shows only commands the tool really has', () => {
    const shown = [...readme.matchAll(/bin\/kerf\.js ([a-z]+)/g)].map((m) => m[1]);
    expect(shown.length).toBeGreaterThan(0);
    for (const command of shown) {
      expect(commandNames(), command).toContain(command);
    }
  });

  it('shows only tables the tables command really has', () => {
    const shown = [...readme.matchAll(/--table ([a-z]+)/g)].map((m) => m[1]);
    for (const table of shown) {
      expect(tableNames(), table).toContain(table);
    }
  });

  it('names every acceptance code the engine carries', () => {
    for (const label of ['API 1104', 'ASME Section VIII', 'ASME B31.3', 'AWS D1.1']) {
      expect(readme).toContain(label);
    }
    expect(kerfline.codes()).toHaveLength(4);
  });

  it('claims no runtime dependencies, and package.json agrees', () => {
    const pkg = JSON.parse(readFileSync(join(root, 'package.json'), 'utf8'));
    expect(flat).toContain('no runtime dependencies');
    expect(pkg.dependencies).toBeUndefined();
  });

  it('is honest that the store is the only thing that writes', () => {
    expect(flat).toContain('the store appends rather than mutating');
  });

  it('says what it does about numbers that are this shop and not a code', () => {
    expect(flat).toContain("this shop's own practice rather than a code requirement");
  });
});
