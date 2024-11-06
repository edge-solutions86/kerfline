import { describe, it, expect } from 'vitest';
import {
  dispositions,
  severityOf,
  makeVerdict,
  accept,
  worstOf,
  combine,
  isAcceptable,
  requiresRemoval,
} from '../../src/accept/verdict.js';

describe('the dispositions', () => {
  it('runs from accept to reject', () => {
    expect(dispositions()).toEqual(['accept', 'repair', 'reject']);
  });

  it('orders them by severity', () => {
    expect(severityOf('accept')).toBeLessThan(severityOf('repair'));
    expect(severityOf('repair')).toBeLessThan(severityOf('reject'));
  });

  it('refuses a disposition it does not know', () => {
    expect(() => severityOf('maybe')).toThrow(/disposition must be one of/);
  });
});

describe('makeVerdict', () => {
  it('carries the code and clause', () => {
    const verdict = makeVerdict({
      disposition: 'repair',
      code: 'API1104',
      clause: '9.3.6',
      reason: 'slag longer than allowed',
    });
    expect(verdict.code).toBe('API1104');
    expect(verdict.clause).toBe('9.3.6');
    expect(verdict.reason).toBe('slag longer than allowed');
  });

  it('freezes so a report cannot rewrite a finding', () => {
    const verdict = accept('API1104', '9.3');
    expect(() => {
      verdict.disposition = 'reject';
    }).toThrow();
  });

  it('lets an acceptance carry no reason', () => {
    expect(accept('API1104', '9.3').reason).toBe(null);
  });

  it('insists a finding carries a reason', () => {
    expect(() =>
      makeVerdict({ disposition: 'repair', code: 'API1104', clause: '9.3.6' })
    ).toThrow(/carries a reason/);
  });

  it('insists on a code and a clause', () => {
    expect(() => makeVerdict({ disposition: 'accept', code: '', clause: '9.3' })).toThrow(
      /names the code/
    );
    expect(() => makeVerdict({ disposition: 'accept', code: 'API1104', clause: '  ' })).toThrow(
      /names the clause/
    );
  });
});

describe('worstOf', () => {
  const ok = accept('API1104', '9.3');
  const repair = makeVerdict({
    disposition: 'repair',
    code: 'API1104',
    clause: '9.3.6',
    reason: 'slag',
  });
  const reject = makeVerdict({
    disposition: 'reject',
    code: 'API1104',
    clause: '9.3.9',
    reason: 'crack',
  });

  it('picks the most severe', () => {
    expect(worstOf([ok, repair, reject]).disposition).toBe('reject');
    expect(worstOf([ok, repair]).disposition).toBe('repair');
    expect(worstOf([ok, ok]).disposition).toBe('accept');
  });

  it('does not care what order they arrive in', () => {
    expect(worstOf([reject, ok, repair]).disposition).toBe('reject');
  });

  it('refuses an empty set', () => {
    expect(() => worstOf([])).toThrow(/no worst of an empty set/);
    expect(() => worstOf('accept')).toThrow(/must be an array/);
  });
});

describe('combine', () => {
  const ok = accept('API1104', '9.3');
  const repair = makeVerdict({
    disposition: 'repair',
    code: 'API1104',
    clause: '9.3.6',
    reason: 'slag longer than allowed',
  });
  const reject = makeVerdict({
    disposition: 'reject',
    code: 'API1104',
    clause: '9.3.9',
    reason: 'crack',
  });

  it('accepts a joint with nothing on it', () => {
    const rolled = combine([]);
    expect(rolled.disposition).toBe('accept');
    expect(rolled.findings).toEqual([]);
  });

  it('accepts a joint whose every verdict was an acceptance', () => {
    const rolled = combine([ok, ok]);
    expect(rolled.disposition).toBe('accept');
    expect(rolled.findings).toEqual([]);
  });

  it('takes the governing disposition from the worst finding', () => {
    expect(combine([ok, repair, reject]).disposition).toBe('reject');
  });

  it('keeps every finding and not only the governing one', () => {
    const rolled = combine([ok, repair, reject]);
    expect(rolled.findings).toHaveLength(2);
    expect(rolled.reasons).toEqual(['slag longer than allowed', 'crack']);
  });

  it('names the clause of the governing finding', () => {
    expect(combine([ok, repair, reject]).clause).toBe('9.3.9');
  });

  it('takes an overriding code label', () => {
    expect(combine([repair], { code: 'API 1104 2021' }).code).toBe('API 1104 2021');
  });
});

describe('isAcceptable and requiresRemoval', () => {
  it('reads a single verdict', () => {
    expect(isAcceptable(accept('API1104', '9.3'))).toBe(true);
    const reject = makeVerdict({
      disposition: 'reject',
      code: 'API1104',
      clause: '9.3.9',
      reason: 'crack',
    });
    expect(isAcceptable(reject)).toBe(false);
    expect(requiresRemoval(reject)).toBe(true);
  });

  it('reads a combined result', () => {
    const rolled = combine([accept('API1104', '9.3')]);
    expect(isAcceptable(rolled)).toBe(true);
    expect(requiresRemoval(rolled)).toBe(false);
  });

  it('does not call a repair a removal', () => {
    const repair = makeVerdict({
      disposition: 'repair',
      code: 'API1104',
      clause: '9.3.6',
      reason: 'slag',
    });
    expect(requiresRemoval(repair)).toBe(false);
    expect(isAcceptable(repair)).toBe(false);
  });
});
