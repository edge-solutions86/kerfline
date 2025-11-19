import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { VERSION, versionParts, atLeastVersion } from '../../src/util/version.js';
import { version as cliVersion, run } from '../../src/cli/run.js';

const here = dirname(fileURLToPath(import.meta.url));
const pkg = JSON.parse(readFileSync(join(here, '..', '..', 'package.json'), 'utf8'));

describe('the version', () => {
  it('is written the one way', () => {
    expect(VERSION).toMatch(/^\d+\.\d+\.\d+$/);
  });

  it('agrees with package.json, which is the point of this file', () => {
    expect(VERSION).toBe(pkg.version);
  });

  it('is what the command line reports', () => {
    expect(cliVersion()).toBe(VERSION);
    expect(run(['--version']).out).toBe(pkg.version);
  });

  it('breaks into parts', () => {
    const parts = versionParts();
    expect(parts.major).toBeGreaterThanOrEqual(1);
    expect(Number.isInteger(parts.minor)).toBe(true);
    expect(Number.isInteger(parts.patch)).toBe(true);
  });
});

describe('atLeastVersion', () => {
  it('is at least itself', () => {
    expect(atLeastVersion(VERSION)).toBe(true);
  });

  it('is past everything before it', () => {
    expect(atLeastVersion('0.1.0')).toBe(true);
    expect(atLeastVersion('0.9.9')).toBe(true);
  });

  it('is not past what comes after it', () => {
    expect(atLeastVersion('99.0.0')).toBe(false);
  });

  it('compares by number and not as a string', () => {
    // A string comparison puts 1.10.0 before 1.9.0, which is the bug this
    // function exists to not have.
    expect(atLeastVersion('1.0.0')).toBe(true);
    expect(atLeastVersion('1.10.0')).toBe(false);
  });

  it('refuses something that is not a version', () => {
    expect(() => atLeastVersion('one')).toThrow(/is not a version/);
    expect(() => atLeastVersion('1.0')).toThrow(/is not a version/);
  });
});

describe('what the package ships', () => {
  it('has no runtime dependencies', () => {
    expect(pkg.dependencies).toBeUndefined();
  });

  it('ships the library and the binary and nothing else', () => {
    expect(pkg.files).toEqual(['src', 'bin']);
  });

  it('points its entry point and its binary at real paths', () => {
    expect(pkg.main).toBe('src/index.js');
    expect(pkg.bin.kerf).toBe('bin/kerf.js');
  });

  it('carries every script the build runs', () => {
    for (const script of ['test', 'coverage', 'lint', 'format:check', 'verify']) {
      expect(Object.keys(pkg.scripts)).toContain(script);
    }
  });

  it('is private, because this is not going to a registry', () => {
    expect(pkg.private).toBe(true);
  });
});
