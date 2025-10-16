import { round } from '../util/num.js';
import { requireArray, requireLabel } from '../util/guard.js';
import { compareIds } from '../util/ids.js';
import { buildTechnique, summariseTechnique } from '../report/technique.js';

/**
 * One technique sheet per group of joints, not one per joint.
 *
 * A spread of three hundred joints is usually four or five sizes. Working out
 * three hundred technique sheets produces three hundred pieces of paper that
 * say five different things, and the crew reads the first one and works from
 * it all week.
 *
 * So joints are grouped by what actually changes the technique, and only
 * that: the outside diameter, the wall, and whether the source can get
 * inside. Two joints with different welders and different spools and the same
 * pipe get one sheet, and the sheet lists which joints it covers, so a joint
 * that later turns out to be different is visible as one that fell out of
 * its group rather than as a missing sheet.
 */

/** What makes two joints need different sheets, and nothing else. */
export function techniqueKey(joint, { sourceCanEnter = false } = {}) {
  return [
    round(joint.odIn, 4),
    round(joint.wallIn, 4),
    sourceCanEnter ? 'inside' : 'outside',
  ].join('/');
}

/** Group a list of joints by the technique they need. */
export function groupJoints(joints, options = {}) {
  requireArray(joints, 'joints');
  const groups = new Map();
  for (const joint of joints) {
    const key = techniqueKey(joint, options);
    if (!groups.has(key)) groups.set(key, { key, joints: [], example: joint });
    groups.get(key).joints.push(joint);
  }
  return [...groups.values()]
    .map((group) => ({
      ...group,
      joints: [...group.joints].sort((a, b) => compareIds(a.id, b.id)),
      count: group.joints.length,
    }))
    .sort((a, b) => b.count - a.count || a.key.localeCompare(b.key));
}

/**
 * Build one sheet per group. The sheet is built from the example joint and
 * carries the list of joints it covers, so the crew can check the joint in
 * front of them is on it.
 */
export function buildSheetSet({ job, joints, ...setup }) {
  requireLabel(job, 'a sheet set belongs to a job');
  const groups = groupJoints(joints, setup);
  return {
    job: job.trim(),
    sheets: groups.map((group) => {
      const technique = buildTechnique({ job, joint: group.example, ...setup });
      return {
        key: group.key,
        covers: group.joints.map((joint) => joint.id),
        count: group.count,
        technique,
        usable: technique.usable,
      };
    }),
    groups: groups.length,
    joints: joints.length,
  };
}

/** The one line summary of each sheet, for the front of the folder. */
export function sheetIndex(set) {
  return set.sheets.map((sheet) => ({
    key: sheet.key,
    joints: sheet.count,
    ...summariseTechnique(sheet.technique),
  }));
}

/** Sheets that did not resolve, which is the only thing worth chasing. */
export function unusableSheets(set) {
  return set.sheets.filter((sheet) => !sheet.usable);
}

/** Which sheet covers a joint, or null when nothing does. */
export function sheetFor(set, jointId) {
  return set.sheets.find((sheet) => sheet.covers.includes(jointId)) ?? null;
}

/**
 * How much work the grouping saved. Not vanity: a spread whose grouping saves
 * nothing is a spread where every joint really is different, and that is
 * worth knowing before the crew mobilises with one camera.
 */
export function grouping(set) {
  return {
    joints: set.joints,
    sheets: set.groups,
    largestGroup: set.sheets.length === 0 ? 0 : Math.max(...set.sheets.map((s) => s.count)),
    saved: set.joints - set.groups,
  };
}
