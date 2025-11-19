import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { stages, stageNames, stage, planStages } from '../../scripts/verify.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const source = readFileSync(join(here, '..', '..', 'scripts', 'verify.mjs'), 'utf8');

describe('the stage list', () => {
  it('runs the cheap checks before the expensive ones', () => {
    expect(stageNames()).toEqual(['format', 'lint', 'test', 'coverage', 'release', 'smoke']);
  });

  it('gives every stage a command and a description', () => {
    for (const entry of stages()) {
      expect(entry.command.length).toBeGreaterThan(0);
      expect(Array.isArray(entry.args)).toBe(true);
      expect(entry.describe.length).toBeGreaterThan(10);
    }
  });

  it('hands back copies so a caller cannot edit the plan', () => {
    const first = stages();
    first[0].args.push('--nonsense');
    expect(stages()[0].args).not.toContain('--nonsense');
  });

  it('finds one stage by name', () => {
    expect(stage('lint').args).toContain('eslint');
  });

  it('refuses a stage nobody wrote, and lists what there is', () => {
    expect(() => stage('typecheck')).toThrow(/no verification stage typecheck/);
    expect(() => stage('typecheck')).toThrow(/format, lint, test/);
  });

  it('ends with the one stage that runs the real tool', () => {
    const last = stages()[stages().length - 1];
    expect(last.name).toBe('smoke');
    expect(last.args).toContain('bin/kerf.js');
  });
});

describe('planStages', () => {
  it('plans everything when nothing is asked for', () => {
    expect(planStages([]).map((entry) => entry.name)).toEqual(stageNames());
    expect(planStages(undefined).map((entry) => entry.name)).toEqual(stageNames());
  });

  it('plans only what was asked for, in the order asked', () => {
    expect(planStages(['test', 'lint']).map((entry) => entry.name)).toEqual(['test', 'lint']);
  });

  it('refuses a plan naming a stage that does not exist', () => {
    expect(() => planStages(['lint', 'typecheck'])).toThrow(/no verification stage/);
  });
});

describe('the file itself', () => {
  it('guards its entry point so importing it does nothing', () => {
    expect(source).toContain('process.argv[1]');
    expect(source).toContain('import.meta.url');
  });

  it('has no shebang, which a Windows checkout would break', () => {
    expect(source.startsWith('#!')).toBe(false);
  });

  it('carries no carriage returns', () => {
    expect(source.includes('\r')).toBe(false);
  });
});
