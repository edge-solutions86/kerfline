import { BadInput } from '../errors.js';
import { requireArray, requireAtLeast, requireLabel, requireWhole } from '../util/guard.js';

/**
 * Choosing which joints to shoot, and what happens when one fails.
 *
 * The selection has to be random, and it also has to be reproducible: a
 * client auditing the job a year later needs to be able to rerun the choice
 * and get the same joints. So the generator is seeded off the job number
 * rather than the clock, and the seed goes in the record.
 *
 * A generator was written out here rather than pulled in, because a package
 * whose output changed between versions would silently break every historical
 * selection, and there is no version of that failure that gets noticed early.
 */

/** Mulberry32. Small, fast, and fixed for the life of this package. */
export function generator(seed) {
  if (!Number.isInteger(seed)) {
    throw new BadInput('a seed must be a whole number', { got: seed });
  }
  let state = seed >>> 0;
  return function next() {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** Turn a job number into a seed, so the same job always selects the same. */
export function seedFrom(text) {
  requireLabel(text, 'a seed is derived from a non empty string');
  let hash = 2166136261;
  for (const char of text.trim()) {
    hash ^= char.codePointAt(0);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
}

/** Fisher and Yates, driven by the seeded generator. */
export function shuffle(items, seed) {
  requireArray(items, 'items');
  const next = generator(seed);
  const out = [...items];
  for (let i = out.length - 1; i > 0; i -= 1) {
    const j = Math.floor(next() * (i + 1));
    [out[i], out[j]] = [out[j], out[i]];
  }
  return out;
}

/**
 * Pick joints to shoot. The selection is spread across welders first and
 * filled at random after, because a purely random draw on a job where one
 * welder made most of the joints can miss another welder entirely, and every
 * specification this shop works to wants each welder covered.
 */
export function selectJoints(joints, count, seed) {
  requireArray(joints, 'joints');
  requireWhole(count, 'joint count');
  requireAtLeast(count, 0, 'joint count');
  if (count > joints.length) {
    throw new BadInput('cannot select more joints than the job has', {
      asked: count,
      available: joints.length,
    });
  }
  if (count === 0) return [];

  const byWelder = new Map();
  for (const joint of shuffle(joints, seed)) {
    const welder = joint.welder ?? 'unknown';
    if (!byWelder.has(welder)) byWelder.set(welder, []);
    byWelder.get(welder).push(joint);
  }

  const picked = [];
  const welders = [...byWelder.keys()];
  // One from each welder first, then round robin until the count is met.
  while (picked.length < count) {
    let tookOne = false;
    for (const welder of welders) {
      if (picked.length >= count) break;
      const theirs = byWelder.get(welder);
      if (theirs.length > 0) {
        picked.push(theirs.shift());
        tookOne = true;
      }
    }
    if (!tookOne) break;
  }
  return picked;
}

/**
 * Progressive sampling. A failed joint pulls two more joints by the same
 * welder into the shoot, and if either of those fails the whole of that
 * welder's work since the last accepted joint comes in.
 */
export function progressiveFor({ failed, allJoints, alreadyShot = [] }) {
  requireArray(allJoints, 'joints');
  requireArray(alreadyShot, 'joints already shot');
  if (typeof failed !== 'object' || failed === null) {
    throw new BadInput('a failed joint record is needed', { got: failed });
  }
  const welder = failed.welder ?? 'unknown';
  const shotIds = new Set(alreadyShot.map((joint) => joint.id));
  const candidates = allJoints.filter(
    (joint) =>
      (joint.welder ?? 'unknown') === welder && joint.id !== failed.id && !shotIds.has(joint.id)
  );
  return {
    welder,
    additional: candidates.slice(0, 2),
    exhausted: candidates.length < 2,
  };
}

/**
 * The escalation when a progressive sample also fails: everything by that
 * welder back to the last joint of theirs that was accepted.
 */
export function escalateFor({ welder, allJoints }) {
  requireArray(allJoints, 'joints');
  const theirs = allJoints.filter((joint) => (joint.welder ?? 'unknown') === welder);
  let lastAccepted = -1;
  theirs.forEach((joint, index) => {
    if (joint.disposition === 'accept') lastAccepted = index;
  });
  return theirs.slice(lastAccepted + 1);
}
