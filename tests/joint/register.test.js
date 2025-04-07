import { describe, it, expect } from 'vitest';
import {
  makeRegister,
  addJoint,
  addJoints,
  getJoint,
  hasJoint,
  joints,
  jointIds,
  filterJoints,
  byWelder,
  bySpool,
  welders,
  spools,
  registerTotals,
  sizesOnJob,
} from '../../src/joint/register.js';

const inputs = [
  { id: 'J-10', spool: 'SP-1', welder: 'A17', nominalSize: 6, schedule: 40 },
  { id: 'J-2', spool: 'SP-1', welder: 'A17', nominalSize: 6, schedule: 40 },
  { id: 'J-3', spool: 'SP-2', welder: 'B04', nominalSize: 2, schedule: 40 },
];

const register = addJoints(makeRegister('C-4471'), inputs);

describe('makeRegister', () => {
  it('starts empty and belongs to a job', () => {
    const empty = makeRegister('C-4471');
    expect(empty.job).toBe('C-4471');
    expect(empty.joints).toEqual([]);
  });

  it('refuses a register with no job', () => {
    expect(() => makeRegister('  ')).toThrow(/belongs to a job/);
  });
});

describe('addJoint', () => {
  it('adds a joint', () => {
    expect(register.joints).toHaveLength(3);
  });

  it('leaves the register it was given alone', () => {
    const before = makeRegister('C-4471');
    addJoint(before, inputs[0]);
    expect(before.joints).toHaveLength(0);
  });

  it('refuses the same joint number twice', () => {
    expect(() => addJoint(register, inputs[0])).toThrow(/already on this register/);
  });

  it('refuses a joint the spec will not build', () => {
    expect(() => addJoint(register, { id: 'J-99' })).toThrow(/either a size and schedule/);
  });

  it('refuses something that is not a register', () => {
    expect(() => addJoint({}, inputs[0])).toThrow(/not a register/);
  });
});

describe('getJoint and hasJoint', () => {
  it('finds a joint', () => {
    expect(getJoint(register, 'J-2').welder).toBe('A17');
  });

  it('refuses one that is not there, naming the job', () => {
    expect(() => getJoint(register, 'J-99')).toThrow(/no joint J-99 on C-4471/);
  });

  it('answers hasJoint without throwing', () => {
    expect(hasJoint(register, 'J-2')).toBe(true);
    expect(hasJoint(register, 'J-99')).toBe(false);
  });
});

describe('joints', () => {
  it('reads in register order rather than entry order', () => {
    expect(joints(register).map((joint) => joint.id)).toEqual(['J-2', 'J-3', 'J-10']);
  });

  it('lists the ids the same way', () => {
    expect(jointIds(register)).toEqual(['J-2', 'J-3', 'J-10']);
  });

  it('filters on any predicate', () => {
    expect(filterJoints(register, (joint) => joint.odIn > 3).map((j) => j.id)).toEqual([
      'J-2',
      'J-10',
    ]);
  });

  it('refuses a predicate that is not a function', () => {
    expect(() => filterJoints(register, 'big')).toThrow(/needs a predicate/);
  });
});

describe('grouping', () => {
  it('finds a welder work', () => {
    expect(byWelder(register, 'A17').map((j) => j.id)).toEqual(['J-2', 'J-10']);
  });

  it('finds a spool', () => {
    expect(bySpool(register, 'SP-2').map((j) => j.id)).toEqual(['J-3']);
  });

  it('lists the welders and the spools', () => {
    expect(welders(register)).toEqual(['A17', 'B04']);
    expect(spools(register)).toEqual(['SP-1', 'SP-2']);
  });

  it('leaves out joints with no welder recorded', () => {
    const anon = addJoint(register, { id: 'J-50', nominalSize: 6, schedule: 40 });
    expect(welders(anon)).toEqual(['A17', 'B04']);
  });
});

describe('registerTotals', () => {
  const totals = registerTotals(register);

  it('counts the job', () => {
    expect(totals.joints).toBe(3);
    expect(totals.welders).toBe(2);
    expect(totals.spools).toBe(2);
  });

  it('adds the weld length up', () => {
    expect(totals.weldLengthIn).toBeCloseTo(20.813 * 2 + 7.461, 1);
  });

  it('counts the small bore joints', () => {
    expect(totals.smallBore).toBe(1);
  });

  it('counts the fixed position joints', () => {
    expect(totals.fixedPosition).toBe(3);
  });

  it('totals an empty register to nothing', () => {
    const totals_ = registerTotals(makeRegister('C-0'));
    expect(totals_.joints).toBe(0);
    expect(totals_.weldLengthIn).toBe(0);
  });
});

describe('sizesOnJob', () => {
  it('counts the distinct sizes', () => {
    const sizes = sizesOnJob(register);
    expect(sizes[0]).toEqual({ size: '6 in sch 40', count: 2 });
    expect(sizes[1]).toEqual({ size: '2 in sch 40', count: 1 });
  });

  it('puts the commonest size first', () => {
    const sizes = sizesOnJob(register);
    expect(sizes[0].count).toBeGreaterThanOrEqual(sizes[1].count);
  });

  it('describes a measured joint by its measurements', () => {
    const measured = addJoint(register, { id: 'J-77', odIn: 4.5, wallIn: 0.237 });
    expect(sizesOnJob(measured).some((row) => row.size.includes('OD'))).toBe(true);
  });

  it('is empty for an empty register', () => {
    expect(sizesOnJob(makeRegister('C-0'))).toEqual([]);
  });
});
