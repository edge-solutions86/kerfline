import { describe, it, expect } from 'vitest';
import {
  makeHeat,
  heatOf,
  assignHeats,
  traceJoint,
  untraceableHeats,
  untraceableJoints,
  jointsFromHeat,
} from '../../src/joint/heat.js';
import { makeJoint } from '../../src/joint/spec.js';

const heats = [
  makeHeat({
    number: 'H-4471',
    materialId: 'CARBON_STEEL',
    receivedOn: '2025-02-01',
    millReport: 'MTR-1',
  }),
  makeHeat({
    number: 'H-4472',
    materialId: 'CARBON_STEEL',
    receivedOn: '2025-02-01',
    millReport: 'MTR-2',
  }),
  makeHeat({ number: 'H-4480', materialId: 'STAINLESS', receivedOn: '2025-02-10' }),
];

const base = makeJoint({ id: 'J-101', nominalSize: 6, schedule: 40 });

describe('makeHeat', () => {
  it('records the metal and whether it can be traced', () => {
    expect(heats[0].material).toBe('carbon steel');
    expect(heats[0].traceable).toBe(true);
    expect(heats[2].traceable).toBe(false);
  });

  it('refuses a heat with no number', () => {
    expect(() =>
      makeHeat({ number: ' ', materialId: 'CARBON_STEEL', receivedOn: '2025-02-01' })
    ).toThrow(/identified by its number/);
  });

  it('refuses a material nobody stocks', () => {
    expect(() =>
      makeHeat({ number: 'H-1', materialId: 'LEAD', receivedOn: '2025-02-01' })
    ).toThrow(/material must be one of/);
  });

  it('refuses a date that is not a real day', () => {
    expect(() =>
      makeHeat({ number: 'H-1', materialId: 'CARBON_STEEL', receivedOn: '2025-02-30' })
    ).toThrow(/not a real calendar day/);
  });
});

describe('heatOf', () => {
  it('finds a heat', () => {
    expect(heatOf(heats, 'H-4471').materialId).toBe('CARBON_STEEL');
  });

  it('refuses one it does not have, naming what it does', () => {
    expect(() => heatOf(heats, 'H-9999')).toThrow(/no heat H-9999 on file/);
  });
});

describe('assignHeats', () => {
  it('takes one heat on a joint welded within a length', () => {
    expect(assignHeats(base, { upstream: 'H-4471' }).heats).toEqual(['H-4471']);
  });

  it('takes both on a joint between two lengths', () => {
    const joint = assignHeats(base, { upstream: 'H-4471', downstream: 'H-4472' });
    expect(joint.heats).toEqual(['H-4471', 'H-4472']);
  });

  it('leaves the joint it was given alone', () => {
    assignHeats(base, { upstream: 'H-4471' });
    expect(base.heats).toBeUndefined();
  });

  it('refuses a joint with no upstream heat', () => {
    expect(() => assignHeats(base, { upstream: '' })).toThrow(/upstream side/);
  });
});

describe('traceJoint', () => {
  it('traces a joint back to its metal', () => {
    const joint = assignHeats(base, { upstream: 'H-4471', downstream: 'H-4472' });
    const trace = traceJoint(joint, heats);
    expect(trace.traceable).toBe(true);
    expect(trace.heats).toEqual(['H-4471', 'H-4472']);
    expect(trace.dissimilar).toBe(false);
  });

  it('works the equivalent steel thickness out from the metal', () => {
    const joint = assignHeats(base, { upstream: 'H-4471' });
    expect(traceJoint(joint, heats).equivalentSteelIn).toBeCloseTo(0.28, 3);
  });

  it('takes the harder metal on a dissimilar joint', () => {
    const joint = assignHeats(base, { upstream: 'H-4471', downstream: 'H-4480' });
    const trace = traceJoint(joint, heats);
    expect(trace.dissimilar).toBe(true);
    expect(trace.governingMaterial).toBe('STAINLESS');
    expect(trace.equivalentSteelIn).toBeGreaterThan(0.28);
  });

  it('is not traceable when a mill report is missing', () => {
    const joint = assignHeats(base, { upstream: 'H-4480' });
    const trace = traceJoint(joint, heats);
    expect(trace.traceable).toBe(false);
    expect(trace.reason).toMatch(/no mill report for heat H-4480/);
  });

  it('is not traceable when no heat was recorded at all', () => {
    const trace = traceJoint(base, heats);
    expect(trace.traceable).toBe(false);
    expect(trace.reason).toMatch(/no heat recorded/);
  });

  it('refuses a heat that is not on file', () => {
    const joint = assignHeats(base, { upstream: 'H-9999' });
    expect(() => traceJoint(joint, heats)).toThrow(/no heat H-9999/);
  });
});

describe('untraceableHeats and untraceableJoints', () => {
  it('names the heats with no mill report', () => {
    expect(untraceableHeats(heats).map((heat) => heat.number)).toEqual(['H-4480']);
  });

  it('names the joints those heats went into', () => {
    const joints = [
      assignHeats(makeJoint({ id: 'J-1', nominalSize: 6, schedule: 40 }), {
        upstream: 'H-4471',
      }),
      assignHeats(makeJoint({ id: 'J-2', nominalSize: 6, schedule: 40 }), {
        upstream: 'H-4480',
      }),
    ];
    expect(untraceableJoints(joints, heats).map((trace) => trace.jointId)).toEqual(['J-2']);
  });

  it('finds nothing when every heat has its report', () => {
    const good = heats.filter((heat) => heat.traceable);
    const joints = [assignHeats(base, { upstream: 'H-4471' })];
    expect(untraceableJoints(joints, good)).toEqual([]);
  });
});

describe('jointsFromHeat', () => {
  const joints = [
    assignHeats(makeJoint({ id: 'J-10', nominalSize: 6, schedule: 40 }), {
      upstream: 'H-4471',
    }),
    assignHeats(makeJoint({ id: 'J-2', nominalSize: 6, schedule: 40 }), {
      upstream: 'H-4471',
      downstream: 'H-4472',
    }),
    assignHeats(makeJoint({ id: 'J-3', nominalSize: 6, schedule: 40 }), { upstream: 'H-4472' }),
  ];

  it('answers what a recall asks: every joint that heat went into', () => {
    expect(jointsFromHeat(joints, 'H-4471')).toEqual(['J-2', 'J-10']);
  });

  it('counts a joint welded between two heats under both', () => {
    expect(jointsFromHeat(joints, 'H-4472')).toContain('J-2');
  });

  it('finds nothing for a heat that never went anywhere', () => {
    expect(jointsFromHeat(joints, 'H-4480')).toEqual([]);
  });

  it('refuses a lookup that is not a heat number', () => {
    expect(() => jointsFromHeat(joints, 4471)).toThrow(/looked up by its number/);
  });
});
