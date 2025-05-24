import { describe, it, expect } from 'vitest';
import {
  optionKinds,
  defineOption,
  parseArgs,
  describeOption,
  usage,
} from '../../src/cli/args.js';

const options = [
  defineOption({ name: 'job', required: true, describe: 'the job number' }),
  defineOption({ name: 'wall', kind: 'number', fallback: 0.28 }),
  defineOption({ name: 'day', kind: 'day' }),
  defineOption({ name: 'verbose', kind: 'boolean' }),
];

describe('defineOption', () => {
  it('names the kinds', () => {
    expect(optionKinds()).toEqual(['string', 'number', 'boolean', 'day']);
  });

  it('refuses a name that is not a flag name', () => {
    expect(() => defineOption({ name: 'Job' })).toThrow(/lower case letters/);
    expect(() => defineOption({ name: '1job' })).toThrow(/lower case letters/);
  });

  it('refuses a kind it does not have', () => {
    expect(() => defineOption({ name: 'job', kind: 'date' })).toThrow(/an option is one of/);
  });

  it('refuses a required option with a fallback', () => {
    expect(() => defineOption({ name: 'job', required: true, fallback: 'x' })).toThrow(
      /cannot also have a fallback/
    );
  });
});

describe('parseArgs', () => {
  it('reads a value after the flag', () => {
    expect(parseArgs(['--job', 'C-4471'], options).options.job).toBe('C-4471');
  });

  it('reads a value joined by an equals sign', () => {
    expect(parseArgs(['--job=C-4471'], options).options.job).toBe('C-4471');
  });

  it('coerces a number', () => {
    const parsed = parseArgs(['--job', 'C-1', '--wall', '0.432'], options);
    expect(parsed.options.wall).toBe(0.432);
  });

  it('refuses a number that is not one, naming the option', () => {
    expect(() => parseArgs(['--job', 'C-1', '--wall', 'thick'], options)).toThrow(
      /--wall wants a number/
    );
  });

  it('refuses a date in the wrong shape', () => {
    expect(() => parseArgs(['--job', 'C-1', '--day', '14/03/2025'], options)).toThrow(
      /--day wants a date/
    );
  });

  it('takes a flag with no value', () => {
    expect(parseArgs(['--job', 'C-1', '--verbose'], options).options.verbose).toBe(true);
  });

  it('leaves an unset flag false', () => {
    expect(parseArgs(['--job', 'C-1'], options).options.verbose).toBe(false);
  });

  it('refuses a value on a flag', () => {
    expect(() => parseArgs(['--job', 'C-1', '--verbose=yes'], options)).toThrow(
      /is a flag and takes no value/
    );
  });

  it('fills in a fallback', () => {
    expect(parseArgs(['--job', 'C-1'], options).options.wall).toBe(0.28);
  });

  it('leaves an option with no fallback undefined', () => {
    expect(parseArgs(['--job', 'C-1'], options).options.day).toBeUndefined();
  });

  it('refuses an option nobody defined, and says what there is', () => {
    try {
      parseArgs(['--jobb', 'C-1'], options);
      throw new Error('should have refused');
    } catch (err) {
      expect(err.message).toMatch(/no such option --jobb/);
      expect(err.details.known).toContain('job');
    }
  });

  it('refuses a missing required option', () => {
    expect(() => parseArgs(['--wall', '0.3'], options)).toThrow(
      /missing required option: --job/
    );
  });

  it('names every missing option at once', () => {
    const both = [
      defineOption({ name: 'job', required: true }),
      defineOption({ name: 'joint', required: true }),
    ];
    expect(() => parseArgs([], both)).toThrow(/missing required options: --job, --joint/);
  });

  it('refuses a flag left dangling at the end', () => {
    expect(() => parseArgs(['--job'], options)).toThrow(/--job wants a value/);
  });

  it('refuses a flag followed by another flag', () => {
    expect(() => parseArgs(['--job', '--verbose'], options)).toThrow(/--job wants a value/);
  });

  it('collects positionals in order', () => {
    const parsed = parseArgs(['J-1', '--job', 'C-1', 'J-2'], options);
    expect(parsed.positionals).toEqual(['J-1', 'J-2']);
  });

  it('stops parsing options after a bare double dash', () => {
    const parsed = parseArgs(['--job', 'C-1', '--', '--not-an-option'], options);
    expect(parsed.positionals).toEqual(['--not-an-option']);
  });

  it('refuses an argument that is not a string', () => {
    expect(() => parseArgs([42], options)).toThrow(/must be a string/);
  });
});

describe('describeOption and usage', () => {
  it('shows the kind of value an option wants', () => {
    expect(describeOption(options[1])).toContain('--wall <number>');
  });

  it('marks a required option', () => {
    expect(describeOption(options[0])).toContain('(required)');
  });

  it('shows a default', () => {
    expect(describeOption(options[1])).toContain('(default 0.28)');
  });

  it('shows no value for a flag', () => {
    expect(describeOption(options[3])).toBe('--verbose');
  });

  it('appends the description when there is one', () => {
    expect(describeOption(options[0])).toContain('the job number');
  });

  it('builds a usage screen', () => {
    const text = usage('technique', options);
    expect(text.split('\n')[0]).toBe('usage: kerf technique [options]');
    expect(text).toContain('--job');
    expect(text).toContain('--verbose');
  });
});
