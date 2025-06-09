import { describe, it, expect } from 'vitest';
import { exitCodes, version, renderError, run } from '../../src/cli/run.js';
import { BadInput } from '../../src/errors.js';

const codes = exitCodes();

const technique = [
  'technique',
  '--joint',
  'J-118',
  '--size',
  '6',
  '--schedule',
  '40',
  '--activity',
  '40',
  '--distance',
  '48',
  '--standoff',
  '6.625',
];

describe('exit codes', () => {
  it('keeps a finding apart from a mistake', () => {
    expect(codes.ok).toBe(0);
    expect(codes.finding).toBe(1);
    expect(codes.misuse).toBe(2);
    expect(new Set([codes.ok, codes.finding, codes.misuse]).size).toBe(3);
  });
});

describe('help and version', () => {
  it('prints usage and fails when given nothing', () => {
    const result = run([]);
    expect(result.out).toContain('usage: kerf <command>');
    expect(result.code).toBe(codes.misuse);
  });

  it('prints usage and succeeds when help is asked for', () => {
    for (const flag of ['--help', '-h', 'help']) {
      const result = run([flag]);
      expect(result.out).toContain('usage: kerf <command>');
      expect(result.code).toBe(codes.ok);
    }
  });

  it('prints the version', () => {
    expect(run(['--version']).out).toBe(version());
    expect(run(['-v']).code).toBe(codes.ok);
  });

  it('prints help for one command without needing valid arguments', () => {
    const result = run(['technique', '--help']);
    expect(result.out).toContain('usage: kerf technique');
    expect(result.code).toBe(codes.ok);
  });

  it('answers help even when the other arguments are nonsense', () => {
    const result = run(['technique', '--nonsense', '--help']);
    expect(result.code).toBe(codes.ok);
    expect(result.out).toContain('usage: kerf technique');
  });
});

describe('running a command', () => {
  it('prints the sheet and succeeds', () => {
    const result = run(technique);
    expect(result.out).toContain('RADIOGRAPHIC TECHNIQUE');
    expect(result.err).toBe('');
    expect(result.code).toBe(codes.ok);
  });

  it('reports a finding with the finding code and not the misuse one', () => {
    const result = run(['judge', '--size', '6', '--schedule', '40', 'CRACK:0.2:2']);
    expect(result.code).toBe(codes.finding);
    expect(result.err).toBe('');
    expect(result.out).toMatch(/REJECT/);
  });

  it('accepts a clean joint with the ok code', () => {
    expect(run(['judge', '--size', '6', '--schedule', '40']).code).toBe(codes.ok);
  });

  it('lists the sources', () => {
    const result = run(['sources']);
    expect(result.out).toContain('Ir-192');
    expect(result.code).toBe(codes.ok);
  });
});

describe('refusing', () => {
  it('refuses a command nobody wrote and shows the list', () => {
    const result = run(['shoot']);
    expect(result.err).toContain('no command shoot');
    expect(result.err).toContain('usage: kerf <command>');
    expect(result.code).toBe(codes.misuse);
    expect(result.out).toBe('');
  });

  it('refuses a missing required option and shows that command usage', () => {
    const result = run(['technique', '--size', '6']);
    expect(result.err).toContain('missing required option');
    expect(result.err).toContain('usage: kerf technique');
    expect(result.code).toBe(codes.misuse);
  });

  it('refuses an unknown option', () => {
    const result = run([...technique, '--nonsense', 'x']);
    expect(result.err).toContain('no such option --nonsense');
    expect(result.code).toBe(codes.misuse);
  });

  it('refuses a bad indication with the misuse code', () => {
    const result = run(['judge', '--size', '6', '--schedule', '40', 'LAMINATION:0.5']);
    expect(result.code).toBe(codes.misuse);
    expect(result.err).toContain('no indication type');
  });

  it('never writes to both streams at once', () => {
    for (const args of [[], ['--help'], technique, ['shoot'], ['technique']]) {
      const result = run(args);
      expect(result.out === '' || result.err === '').toBe(true);
    }
  });

  it('survives being handed something that is not a list', () => {
    expect(run(undefined).code).toBe(codes.misuse);
    expect(run(null).out).toContain('usage');
  });
});

describe('renderError', () => {
  it('prints the message with no stack trace', () => {
    const text = renderError(new BadInput('nope'));
    expect(text).toBe('kerf: nope');
    expect(text).not.toContain('at ');
  });

  it('prints the details underneath', () => {
    const text = renderError(new BadInput('nope', { option: 'wall', got: 'thick' }));
    expect(text).toContain('option: wall');
    expect(text).toContain('got: thick');
  });

  it('joins a list of known values', () => {
    const text = renderError(new BadInput('nope', { known: ['a', 'b'] }));
    expect(text).toContain('known: a, b');
  });

  it('handles a plain error too', () => {
    expect(renderError(new Error('boom'))).toBe('kerf: boom');
  });
});
