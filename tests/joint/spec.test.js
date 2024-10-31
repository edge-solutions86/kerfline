import { describe, it, expect } from 'vitest';
import {
  weldPositions,
  weldProcesses,
  jointTypes,
  isFixedPosition,
  makeJoint,
  weldLengthIn,
  isSmallBoreJoint,
  describeJoint,
  diameterForSize,
} from '../../src/joint/spec.js';

describe('the vocabularies', () => {
  it('names the positions, processes and joint types', () => {
    expect(weldPositions()).toContain('5G');
    expect(weldProcesses()).toContain('SMAW');
    expect(jointTypes()).toContain('butt');
  });

  it('calls a pipe that cannot be turned a fixed position', () => {
    expect(isFixedPosition('5G')).toBe(true);
    expect(isFixedPosition('6G')).toBe(true);
    expect(isFixedPosition('1G')).toBe(false);
  });

  it('refuses a position nobody welds in', () => {
    expect(() => isFixedPosition('9G')).toThrow(/weld position must be one of/);
  });
});

describe('makeJoint from a size and schedule', () => {
  const joint = makeJoint({ id: 'J-118', nominalSize: 6, schedule: 40, welder: 'A17' });

  it('fills the geometry from the table', () => {
    expect(joint.odIn).toBeCloseTo(6.625, 4);
    expect(joint.wallIn).toBeCloseTo(0.28, 4);
    expect(joint.fromTable).toBe(true);
  });

  it('keeps the identifying fields', () => {
    expect(joint.id).toBe('J-118');
    expect(joint.welder).toBe('A17');
    expect(joint.nominalSize).toBe('6');
    expect(joint.schedule).toBe('40');
  });

  it('takes the shop defaults for position and process', () => {
    expect(joint.position).toBe('5G');
    expect(joint.process).toBe('SMAW');
    expect(joint.type).toBe('butt');
    expect(joint.fixedPosition).toBe(true);
  });

  it('lets a measured wall override the table while keeping the size', () => {
    const worn = makeJoint({
      id: 'J-9',
      nominalSize: 6,
      schedule: 40,
      odIn: 6.625,
      wallIn: 0.265,
    });
    expect(worn.wallIn).toBeCloseTo(0.265, 4);
    expect(worn.nominalSize).toBe('6');
  });

  it('refuses a measured diameter that contradicts the size given', () => {
    expect(() =>
      makeJoint({ id: 'J-9', nominalSize: 6, schedule: 40, odIn: 8.625, wallIn: 0.28 })
    ).toThrow(/does not match the size given/);
  });
});

describe('makeJoint from a caliper', () => {
  const joint = makeJoint({ id: 'J-201', odIn: 4.5, wallIn: 0.237 });

  it('takes the measurements as they are', () => {
    expect(joint.odIn).toBe(4.5);
    expect(joint.wallIn).toBe(0.237);
    expect(joint.fromTable).toBe(false);
    expect(joint.nominalSize).toBe(null);
  });

  it('refuses a wall that swallows the bore', () => {
    expect(() => makeJoint({ id: 'J-1', odIn: 1, wallIn: 0.5 })).toThrow(
      /two walls do not fit/
    );
  });

  it('refuses a measurement at or below zero', () => {
    expect(() => makeJoint({ id: 'J-1', odIn: 0, wallIn: 0.2 })).toThrow(/both be positive/);
  });

  it('refuses a joint with no geometry at all', () => {
    expect(() => makeJoint({ id: 'J-1' })).toThrow(/either a size and schedule/);
  });
});

describe('makeJoint validation', () => {
  it('refuses a joint with no id', () => {
    expect(() => makeJoint({ odIn: 4.5, wallIn: 0.237 })).toThrow(/needs an id/);
    expect(() => makeJoint({ id: '  ', odIn: 4.5, wallIn: 0.237 })).toThrow(/needs an id/);
  });

  it('refuses a process or type it does not know', () => {
    const base = { id: 'J-1', odIn: 4.5, wallIn: 0.237 };
    expect(() => makeJoint({ ...base, process: 'TIG' })).toThrow(/welding process/);
    expect(() => makeJoint({ ...base, type: 'lap' })).toThrow(/joint type/);
  });

  it('refuses a welder stencil that cannot be stamped', () => {
    expect(() => makeJoint({ id: 'J-1', odIn: 4.5, wallIn: 0.237, welder: 'BW-9' })).toThrow(
      /one to six letters or digits/
    );
  });

  it('refuses a weld date that is not a real day', () => {
    expect(() =>
      makeJoint({ id: 'J-1', odIn: 4.5, wallIn: 0.237, weldedOn: '2025-02-30' })
    ).toThrow(/not a real calendar day/);
  });

  it('leaves the optional fields null rather than undefined', () => {
    const joint = makeJoint({ id: 'J-1', odIn: 4.5, wallIn: 0.237 });
    expect(joint.spool).toBe(null);
    expect(joint.welder).toBe(null);
    expect(joint.weldedOn).toBe(null);
  });

  it('refuses something that is not an object', () => {
    expect(() => makeJoint('J-1')).toThrow(/must be described by an object/);
  });
});

describe('the derived measures', () => {
  it('measures the weld round the joint', () => {
    const joint = makeJoint({ id: 'J-1', nominalSize: 6, schedule: 40 });
    expect(weldLengthIn(joint)).toBeCloseTo(20.813, 3);
  });

  it('spots a small bore joint', () => {
    expect(isSmallBoreJoint(makeJoint({ id: 'J-1', nominalSize: 2, schedule: 40 }))).toBe(true);
    expect(isSmallBoreJoint(makeJoint({ id: 'J-2', nominalSize: 6, schedule: 40 }))).toBe(
      false
    );
  });

  it('describes a joint from the table for a register row', () => {
    const joint = makeJoint({ id: 'J-118', nominalSize: 6, schedule: 40, welder: 'A17' });
    expect(describeJoint(joint)).toBe('J-118 6 in sch 40 5G SMAW welder A17');
  });

  it('describes a measured joint by its measurements', () => {
    const joint = makeJoint({ id: 'J-201', odIn: 4.5, wallIn: 0.237 });
    expect(describeJoint(joint)).toBe('J-201 4.5 in OD x 0.237 wall 5G SMAW');
  });

  it('looks a diameter up without building a joint', () => {
    expect(diameterForSize(12)).toBeCloseTo(12.75, 4);
  });
});
