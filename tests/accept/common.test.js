import { describe, it, expect } from 'vitest';
import { rejectPlanar, capVerdict, windowTotal, settle } from '../../src/accept/common.js';
import { makeIndication } from '../../src/indication/measure.js';
import { makeVerdict, accept } from '../../src/accept/verdict.js';

const ind = (type, measuredIn, extra = {}) => makeIndication({ type, measuredIn, ...extra });

describe('rejectPlanar', () => {
  it('rejects and names the indication', () => {
    const verdict = rejectPlanar({ code: 'X', clause: '1', indication: ind('CRACK', 0.1) });
    expect(verdict.disposition).toBe('reject');
    expect(verdict.reason).toBe('crack is not permitted at any size');
  });

  it('carries the code and clause it was given', () => {
    const verdict = rejectPlanar({ code: 'X', clause: '9.9', indication: ind('IF', 0.1) });
    expect(verdict.code).toBe('X');
    expect(verdict.clause).toBe('9.9');
  });

  it('measures the indication for the record', () => {
    const verdict = rejectPlanar({ code: 'X', clause: '1', indication: ind('IP', 0.4) });
    expect(verdict.measured).toBeCloseTo(0.4, 4);
  });
});

describe('capVerdict', () => {
  const base = { code: 'X', clause: '1', what: 'slag' };

  it('accepts under the cap', () => {
    expect(capVerdict({ ...base, sizeIn: 0.1, capIn: 0.2 }).disposition).toBe('accept');
  });

  it('accepts exactly at the cap', () => {
    expect(capVerdict({ ...base, sizeIn: 0.2, capIn: 0.2 }).disposition).toBe('accept');
  });

  it('calls a repair over the cap', () => {
    const verdict = capVerdict({ ...base, sizeIn: 0.3, capIn: 0.2 });
    expect(verdict.disposition).toBe('repair');
    expect(verdict.reason).toBe('slag 0.3 in, cap is 0.2 in');
  });

  it('takes a different unit for the reason', () => {
    const verdict = capVerdict({ ...base, sizeIn: 3, capIn: 2, units: 'percent' });
    expect(verdict.reason).toContain('percent');
  });

  it('lets the indication be left out for an aggregate finding', () => {
    const verdict = capVerdict({ ...base, sizeIn: 0.3, capIn: 0.2 });
    expect(verdict.indication).toBe(null);
  });

  it('keeps the indication when one is given', () => {
    const indication = ind('SLAG', 0.3);
    const verdict = capVerdict({ ...base, indication, sizeIn: 0.3, capIn: 0.2 });
    expect(verdict.indication).toBe(indication);
  });
});

describe('windowTotal', () => {
  const set = [
    ind('SLAG', 0.2, { at: 0 }),
    ind('SLAG', 0.3, { at: 1 }),
    ind('SLAG', 0.4, { at: 2 }),
    ind('SLAG', 0.5, { at: 20 }),
  ];

  it('finds the heaviest window', () => {
    const worst = windowTotal(set, { windowIn: 6 });
    expect(worst.totalIn).toBeCloseTo(0.9, 4);
    expect(worst.startIn).toBe(0);
    expect(worst.members).toHaveLength(3);
  });

  it('shrinks as the window narrows', () => {
    expect(windowTotal(set, { windowIn: 1.5 }).totalIn).toBeCloseTo(0.7, 4);
    expect(windowTotal(set, { windowIn: 0.5 }).totalIn).toBeCloseTo(0.5, 4);
  });

  it('moves to the isolated one when the window is small', () => {
    expect(windowTotal(set, { windowIn: 0.5 }).startIn).toBe(20);
  });

  it('is nothing when nothing carries a position', () => {
    const unplaced = [ind('SLAG', 0.2)];
    expect(windowTotal(unplaced, { windowIn: 6 })).toEqual({
      totalIn: 0,
      startIn: 0,
      members: [],
    });
  });

  it('takes a different measure', () => {
    const worst = windowTotal(set, { windowIn: 6, sizeOf: () => 1 });
    expect(worst.totalIn).toBe(3);
  });

  it('refuses a window at zero', () => {
    expect(() => windowTotal(set, { windowIn: 0 })).toThrow(/window length/);
  });
});

describe('settle', () => {
  const repair = makeVerdict({
    disposition: 'repair',
    code: 'X',
    clause: '1',
    reason: 'slag',
  });

  it('settles an empty set as an acceptance against the clause', () => {
    const rolled = settle([], { code: 'X', clause: '9.3' });
    expect(rolled.disposition).toBe('accept');
    expect(rolled.clause).toBe('9.3');
  });

  it('takes the governing disposition when there are findings', () => {
    expect(settle([accept('X', '1'), repair], { code: 'X', clause: '1' }).disposition).toBe(
      'repair'
    );
  });

  it('carries the findings through', () => {
    const rolled = settle([accept('X', '1'), repair], { code: 'X', clause: '1' });
    expect(rolled.findings).toHaveLength(1);
    expect(rolled.reasons).toEqual(['slag']);
  });

  it('labels the result with the code', () => {
    expect(settle([repair], { code: 'API1104', clause: '9.3' }).code).toBe('API1104');
  });

  it('refuses anything that is not a list of verdicts', () => {
    expect(() => settle(repair, { code: 'X', clause: '1' })).toThrow(/must be an array/);
  });
});
