import { BadInput } from '../errors.js';
import { round } from '../util/num.js';
import { requireArray, requireWhole, requireAtLeast } from '../util/guard.js';

/**
 * How much of a job gets radiographed.
 *
 * The fraction is set by the code and the service, and it is a floor rather
 * than a target: a job written for ten percent that shoots nine is short, and
 * a job that shoots twelve is not in breach of anything. The rounding always
 * goes up for the same reason.
 *
 * The awkward case is a small job. Ten percent of seven joints is 0.7, which
 * rounds up to one, and one joint out of seven is fourteen percent. That is
 * correct and it is worth knowing about before a client asks why they are
 * paying for more radiography than the specification says.
 */

const LEVELS = {
  full: { fraction: 1, label: 'full radiography' },
  ten: { fraction: 0.1, label: 'ten percent' },
  five: { fraction: 0.05, label: 'five percent' },
  spot: { fraction: 0.02, label: 'spot radiography' },
  none: { fraction: 0, label: 'no radiography' },
};

export function levels() {
  return Object.keys(LEVELS);
}

export function levelFraction(level) {
  const found = LEVELS[level];
  if (!found) {
    throw new BadInput(`no radiography level called ${level}`, { level, known: levels() });
  }
  return found.fraction;
}

export function levelLabel(level) {
  const found = LEVELS[level];
  if (!found) {
    throw new BadInput(`no radiography level called ${level}`, { level, known: levels() });
  }
  return found.label;
}

/**
 * The level a job runs at. Severe cyclic service and anything a client has
 * named goes to full radiography; the rest falls out of the fluid service.
 */
export function levelFor({ service = 'normal', clientCalled = null } = {}) {
  if (clientCalled !== null) {
    levelFraction(clientCalled);
    return clientCalled;
  }
  if (service === 'severe_cyclic') return 'full';
  if (service === 'category_d') return 'none';
  return 'five';
}

/** How many joints have to be shot, always rounded up. */
export function jointsToShoot(totalJoints, level) {
  requireWhole(totalJoints, 'joint count');
  requireAtLeast(totalJoints, 0, 'joint count');
  const fraction = levelFraction(level);
  if (fraction === 0) return 0;
  return Math.min(totalJoints, Math.ceil(totalJoints * fraction));
}

/**
 * What was actually achieved, and whether it clears the floor. The achieved
 * fraction is reported alongside the required one because on a small job they
 * are noticeably different and somebody always asks.
 */
export function coverageAchieved({ totalJoints, shotJoints, level }) {
  requireWhole(totalJoints, 'joint count');
  requireWhole(shotJoints, 'joints shot');
  requireAtLeast(shotJoints, 0, 'joints shot');
  if (shotJoints > totalJoints) {
    throw new BadInput('more joints were shot than exist on the job', {
      totalJoints,
      shotJoints,
    });
  }
  const required = jointsToShoot(totalJoints, level);
  return {
    level,
    totalJoints,
    shotJoints,
    requiredJoints: required,
    requiredFraction: levelFraction(level),
    achievedFraction: totalJoints === 0 ? 0 : round(shotJoints / totalJoints, 4),
    compliant: shotJoints >= required,
    shortBy: Math.max(0, required - shotJoints),
  };
}

/**
 * Coverage broken down by welder, because a job can clear its overall
 * percentage while a welder on it has never been shot at all. Most
 * specifications require every welder to be covered and the overall number
 * hides that completely.
 */
export function coverageByWelder(joints, level) {
  requireArray(joints, 'joints');
  const byWelder = new Map();
  for (const joint of joints) {
    const welder = joint.welder ?? 'unknown';
    if (!byWelder.has(welder)) byWelder.set(welder, { total: 0, shot: 0 });
    const entry = byWelder.get(welder);
    entry.total += 1;
    if (joint.shot) entry.shot += 1;
  }
  return [...byWelder.entries()]
    .map(([welder, counts]) => ({
      welder,
      ...coverageAchieved({ totalJoints: counts.total, shotJoints: counts.shot, level }),
    }))
    .sort((a, b) => a.welder.localeCompare(b.welder));
}

/** Welders who have not been covered at all, which is the failure that hides. */
export function weldersNotCovered(joints) {
  requireArray(joints, 'joints');
  const byWelder = new Map();
  for (const joint of joints) {
    const welder = joint.welder ?? 'unknown';
    byWelder.set(welder, (byWelder.get(welder) ?? false) || Boolean(joint.shot));
  }
  return [...byWelder.entries()]
    .filter(([, covered]) => !covered)
    .map(([welder]) => welder)
    .sort();
}
