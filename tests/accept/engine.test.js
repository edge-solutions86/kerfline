import { describe, it, expect } from 'vitest';
import {
  codes,
  describeCode,
  contextFor,
  judge,
  judgeAll,
  defaultCodeFor,
} from '../../src/accept/engine.js';
import { makeIndication } from '../../src/indication/measure.js';
import { makeJoint } from '../../src/joint/spec.js';

const ind = (type, measuredIn, extra = {}) => makeIndication({ type, measuredIn, ...extra });
const joint = makeJoint({ id: 'J-118', nominalSize: 6, schedule: 40 });

describe('the registry', () => {
  it('carries the three codes', () => {
    expect(codes()).toEqual(['API1104', 'ASME8', 'B31.3']);
  });

  it('says what each code needs', () => {
    expect(describeCode('API1104').needs).toEqual(['wallIn', 'weldLengthIn']);
    expect(describeCode('ASME8').needs).toEqual(['thicknessIn']);
    expect(describeCode('B31.3').needs).toEqual(['thinnerWallIn', 'service']);
  });

  it('refuses a code nobody wrote', () => {
    expect(() => describeCode('AWS')).toThrow(/no acceptance code AWS/);
  });
});

describe('contextFor', () => {
  it('gives API 1104 the wall and the weld length', () => {
    const context = contextFor('API1104', joint);
    expect(context.wallIn).toBeCloseTo(0.28, 4);
    expect(context.weldLengthIn).toBeCloseTo(20.8129, 3);
  });

  it('gives Section VIII the weld thickness', () => {
    expect(contextFor('ASME8', joint).thicknessIn).toBeCloseTo(0.28, 4);
  });

  it('gives B31.3 the thinner component and a service', () => {
    const context = contextFor('B31.3', joint);
    expect(context.thinnerWallIn).toBeCloseTo(0.28, 4);
    expect(context.service).toBe('normal');
  });

  it('takes the thinner of two mating walls', () => {
    const context = contextFor('B31.3', joint, { matingWallIn: 0.2 });
    expect(context.thinnerWallIn).toBeCloseTo(0.2, 4);
  });

  it('does not take the thicker one', () => {
    const context = contextFor('B31.3', joint, { matingWallIn: 0.5 });
    expect(context.thinnerWallIn).toBeCloseTo(0.28, 4);
  });

  it('takes an overriding weld length', () => {
    expect(contextFor('API1104', joint, { weldLengthIn: 30 }).weldLengthIn).toBe(30);
  });

  it('refuses a joint with no usable wall', () => {
    expect(() => contextFor('API1104', { odIn: 6.625 })).toThrow(/no usable wall/);
    expect(() => contextFor('API1104', null)).toThrow(/a joint record is needed/);
  });
});

describe('judge', () => {
  it('accepts a clean joint under any code', () => {
    for (const code of codes()) {
      expect(judge({ code, indications: [], joint }).disposition).toBe('accept');
    }
  });

  it('carries the joint id into the result', () => {
    expect(judge({ code: 'API1104', indications: [], joint }).jointId).toBe('J-118');
  });

  it('reports the context it judged on', () => {
    const result = judge({ code: 'API1104', indications: [], joint });
    expect(result.context.weldLengthIn).toBeGreaterThan(20);
  });

  it('takes a joint out for a crack under any code', () => {
    const indications = [ind('CRACK', 0.05, { at: 1 })];
    for (const code of codes()) {
      expect(judge({ code, indications, joint }).disposition).toBe('reject');
    }
  });

  it('refuses a code it does not carry', () => {
    expect(() => judge({ code: 'AWS', indications: [], joint })).toThrow(/no acceptance code/);
  });

  it('refuses indications that are not an array', () => {
    expect(() => judge({ code: 'API1104', indications: null, joint })).toThrow(
      /must be an array/
    );
  });
});

describe('the codes disagree, which is the point', () => {
  it('splits on a slag inclusion that one code caps tighter than another', () => {
    // 0.15 in of slag: B31.3 caps it at a third of 0.28 which is 0.093 in,
    // API 1104 caps it at 2 in and does not care.
    const indications = [ind('SLAG', 0.15, { at: 1 })];
    expect(judge({ code: 'API1104', indications, joint }).disposition).toBe('accept');
    expect(judge({ code: 'B31.3', indications, joint }).disposition).toBe('repair');
  });

  it('splits on incomplete penetration between pipeline and plant work', () => {
    const indications = [ind('IP', 0.5, { at: 1 })];
    expect(judge({ code: 'API1104', indications, joint }).disposition).toBe('accept');
    expect(judge({ code: 'B31.3', indications, joint }).disposition).toBe('reject');
  });

  it('moves the B31.3 answer with the fluid service', () => {
    const indications = [ind('IP', 0.5, { at: 1 })];
    const normal = judge({ code: 'B31.3', indications, joint, service: 'normal' });
    const light = judge({ code: 'B31.3', indications, joint, service: 'category_d' });
    expect(normal.disposition).toBe('reject');
    expect(light.disposition).toBe('accept');
  });
});

describe('judgeAll', () => {
  it('runs every code over the same joint', () => {
    const results = judgeAll({ indications: [], joint });
    expect(results).toHaveLength(3);
    expect(results.every((r) => r.disposition === 'accept')).toBe(true);
  });

  it('shows the codes disagreeing side by side', () => {
    const results = judgeAll({ indications: [ind('SLAG', 0.15, { at: 1 })], joint });
    const byCode = Object.fromEntries(results.map((r) => [r.code, r.disposition]));
    expect(byCode['API1104']).toBe('accept');
    expect(byCode['B31.3']).toBe('repair');
  });

  it('reports a code that could not run rather than throwing the lot away', () => {
    const results = judgeAll({ indications: [], joint: { id: 'J-1', odIn: 6.625 } });
    expect(results.every((r) => r.disposition === null)).toBe(true);
    expect(results[0].message).toMatch(/no usable wall/);
  });
});

describe('defaultCodeFor', () => {
  it('sends pipeline work to API 1104', () => {
    expect(defaultCodeFor('pipeline')).toBe('API1104');
  });

  it('sends vessel work to Section VIII', () => {
    expect(defaultCodeFor('vessel')).toBe('ASME8');
  });

  it('sends everything else to B31.3', () => {
    expect(defaultCodeFor('process')).toBe('B31.3');
    expect(defaultCodeFor(undefined)).toBe('B31.3');
  });
});
