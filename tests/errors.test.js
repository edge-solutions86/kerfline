import { describe, it, expect } from 'vitest';
import {
  KerflineError,
  BadInput,
  OutOfRange,
  NoRule,
  NotFound,
  Conflict,
  Unsupported,
  isKerflineError,
  errorCodes,
} from '../src/errors.js';

describe('KerflineError', () => {
  it('keeps the code and the details', () => {
    const err = new KerflineError('BAD_INPUT', 'nope', { field: 'thickness' });
    expect(err.code).toBe('BAD_INPUT');
    expect(err.details).toEqual({ field: 'thickness' });
    expect(err.message).toBe('nope');
  });

  it('is a real Error', () => {
    const err = new KerflineError('CONFLICT', 'clash');
    expect(err).toBeInstanceOf(Error);
    expect(typeof err.stack).toBe('string');
  });

  it('refuses a code that is not in the set', () => {
    expect(() => new KerflineError('WHATEVER', 'x')).toThrow(/unknown error code/);
  });

  it('freezes details so a report cannot edit them', () => {
    const err = new KerflineError('NOT_FOUND', 'gone', { id: 'J-114' });
    expect(() => {
      err.details.id = 'J-115';
    }).toThrow();
    expect(err.details.id).toBe('J-114');
  });

  it('copies details rather than holding the caller object', () => {
    const details = { id: 'J-114' };
    const err = new KerflineError('NOT_FOUND', 'gone', details);
    details.id = 'changed';
    expect(err.details.id).toBe('J-114');
  });

  it('serialises to plain data', () => {
    const err = new NoRule('no paragraph covers this', { code: 'API1104' });
    expect(err.toJSON()).toEqual({
      error: 'NO_RULE',
      message: 'no paragraph covers this',
      details: { code: 'API1104' },
    });
    expect(JSON.parse(JSON.stringify(err)).error).toBe('NO_RULE');
  });

  it('defaults details to an empty object', () => {
    expect(new BadInput('x').details).toEqual({});
  });
});

describe('the subclasses', () => {
  const cases = [
    [BadInput, 'BAD_INPUT'],
    [OutOfRange, 'OUT_OF_RANGE'],
    [NoRule, 'NO_RULE'],
    [NotFound, 'NOT_FOUND'],
    [Conflict, 'CONFLICT'],
    [Unsupported, 'UNSUPPORTED'],
  ];

  it.each(cases)('%o carries its own code', (Ctor, code) => {
    const err = new Ctor('boom');
    expect(err.code).toBe(code);
    expect(err).toBeInstanceOf(KerflineError);
    expect(err.name).toBe(Ctor.name);
  });
});

describe('isKerflineError', () => {
  it('accepts one of ours', () => {
    expect(isKerflineError(new OutOfRange('x'))).toBe(true);
  });

  it('narrows on the code when one is given', () => {
    const err = new OutOfRange('x');
    expect(isKerflineError(err, 'OUT_OF_RANGE')).toBe(true);
    expect(isKerflineError(err, 'BAD_INPUT')).toBe(false);
  });

  it('accepts a revived plain shape that crossed a boundary', () => {
    const revived = { code: 'CONFLICT', message: 'clash', details: {} };
    expect(isKerflineError(revived)).toBe(true);
    expect(isKerflineError(revived, 'CONFLICT')).toBe(true);
  });

  it('rejects a plain shape carrying a code we do not issue', () => {
    expect(isKerflineError({ code: 'ETIMEDOUT', message: 'x' })).toBe(false);
  });

  it('rejects anything else', () => {
    expect(isKerflineError(new Error('plain'))).toBe(false);
    expect(isKerflineError(null)).toBe(false);
    expect(isKerflineError('OUT_OF_RANGE')).toBe(false);
    expect(isKerflineError(undefined)).toBe(false);
  });
});

describe('errorCodes', () => {
  it('lists every code, sorted', () => {
    expect(errorCodes()).toEqual([
      'BAD_INPUT',
      'CONFLICT',
      'NOT_FOUND',
      'NO_RULE',
      'OUT_OF_RANGE',
      'UNSUPPORTED',
    ]);
  });

  it('hands back a fresh array each call', () => {
    const a = errorCodes();
    a.push('MADE_UP');
    expect(errorCodes()).toHaveLength(6);
  });
});
