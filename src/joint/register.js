import { BadInput, Conflict, NotFound } from '../errors.js';
import { round, sum } from '../util/num.js';
import { requireArray } from '../util/guard.js';
import { compareIds, sortIds } from '../util/ids.js';
import { makeJoint, weldLengthIn } from './spec.js';

/**
 * The joint register.
 *
 * A flat list of every joint on a job, keyed by its id. It holds what the
 * joint is and not what has happened to it: the shooting, the reading and the
 * verdicts all live in the journal, and keeping the two apart is what stops
 * the register drifting into a second, disagreeing record of the same job.
 *
 * A duplicate joint number is refused rather than merged. Two joints called
 * J-118 on one job is a numbering mistake on the isometric, and quietly
 * accepting the second one loses the first.
 */

export function makeRegister(job) {
  if (typeof job !== 'string' || job.trim() === '') {
    throw new BadInput('a register belongs to a job', { got: job });
  }
  return Object.freeze({ job: job.trim(), joints: Object.freeze([]) });
}

export function addJoint(register, input) {
  assertRegister(register);
  const joint = makeJoint(input);
  if (register.joints.some((existing) => existing.id === joint.id)) {
    throw new Conflict(`${joint.id} is already on this register`, { id: joint.id });
  }
  return Object.freeze({
    job: register.job,
    joints: Object.freeze([...register.joints, joint]),
  });
}

export function addJoints(register, inputs) {
  requireArray(inputs, 'joints');
  return inputs.reduce((current, input) => addJoint(current, input), register);
}

export function getJoint(register, id) {
  assertRegister(register);
  const found = register.joints.find((joint) => joint.id === id);
  if (!found) {
    throw new NotFound(`no joint ${id} on ${register.job}`, { id, job: register.job });
  }
  return found;
}

export function hasJoint(register, id) {
  assertRegister(register);
  return register.joints.some((joint) => joint.id === id);
}

/** Every joint, in register order rather than the order they were entered. */
export function joints(register) {
  assertRegister(register);
  return [...register.joints].sort((a, b) => compareIds(a.id, b.id));
}

export function jointIds(register) {
  return sortIds(register.joints.map((joint) => joint.id));
}

export function filterJoints(register, predicate) {
  if (typeof predicate !== 'function') {
    throw new BadInput('filtering needs a predicate', { got: typeof predicate });
  }
  return joints(register).filter((joint) => predicate(joint));
}

export function byWelder(register, welder) {
  return filterJoints(register, (joint) => joint.welder === welder);
}

export function bySpool(register, spool) {
  return filterJoints(register, (joint) => joint.spool === spool);
}

export function welders(register) {
  const seen = new Set(register.joints.map((joint) => joint.welder).filter((w) => w !== null));
  return [...seen].sort();
}

export function spools(register) {
  const seen = new Set(register.joints.map((joint) => joint.spool).filter((s) => s !== null));
  return [...seen].sort();
}

/**
 * What the job amounts to. Weld length matters because it is what the
 * radiography is priced on and what the coverage percentage is measured
 * against on some contracts.
 */
export function registerTotals(register) {
  assertRegister(register);
  const all = register.joints;
  return {
    job: register.job,
    joints: all.length,
    welders: welders(register).length,
    spools: spools(register).length,
    weldLengthIn: round(sum(all.map((joint) => weldLengthIn(joint))), 2),
    smallBore: all.filter((joint) => joint.odIn <= 3.5).length,
    fixedPosition: all.filter((joint) => joint.fixedPosition).length,
  };
}

/** The distinct pipe sizes on the job, so a crew knows what to load out. */
export function sizesOnJob(register) {
  const counts = new Map();
  for (const joint of register.joints) {
    const key = joint.nominalSize
      ? `${joint.nominalSize} in sch ${joint.schedule}`
      : `${joint.odIn} in OD x ${joint.wallIn}`;
    counts.set(key, (counts.get(key) ?? 0) + 1);
  }
  return [...counts.entries()]
    .map(([size, count]) => ({ size, count }))
    .sort((a, b) => b.count - a.count || a.size.localeCompare(b.size));
}

function assertRegister(register) {
  if (typeof register !== 'object' || register === null || !Array.isArray(register.joints)) {
    throw new BadInput('not a register', { got: register });
  }
}
