import { BadInput } from '../errors.js';
import { round } from '../util/num.js';
import { requireArray } from '../util/guard.js';
import { renderTable, keyValueBlock, rule, padCenter } from '../util/text.js';
import { compareIds } from '../util/ids.js';
import { repairRate, welderRates } from '../repair/rate.js';
import { coverageAchieved, weldersNotCovered } from '../sampling/coverage.js';

/**
 * The job roll up.
 *
 * Every number on this page is derived from the joint list handed in, and the
 * page deliberately reports the denominators next to the rates. A repair rate
 * of eleven percent means one thing over two hundred joints and nothing at
 * all over nine, and a summary that prints only the percentage invites the
 * reader to treat both the same way.
 *
 * The welder table is not a ranking to act on. It is there because the
 * overall figure hides a welder who is drifting, and because it hides a
 * welder who has never been shot at all, which is the more serious of the two
 * and does not show up in any rate.
 */

const WIDTH = 78;

export function buildSummary({ job, joints, level = 'five' }) {
  requireArray(joints, 'joints');
  if (typeof job !== 'string' || job.trim() === '') {
    throw new BadInput('a summary names the job it covers', { got: job });
  }

  const shot = joints.filter((joint) => joint.shot);
  const judged = shot.filter((joint) => joint.disposition);
  const counts = {
    joints: joints.length,
    shot: shot.length,
    judged: judged.length,
    accepted: judged.filter((joint) => joint.disposition === 'accept').length,
    repaired: judged.filter((joint) => joint.disposition === 'repair').length,
    rejected: judged.filter((joint) => joint.disposition === 'reject').length,
  };

  return {
    job: job.trim(),
    level,
    counts,
    outstanding: counts.shot - counts.judged,
    rate: repairRate(judged),
    coverage: coverageAchieved({
      totalJoints: joints.length,
      shotJoints: shot.length,
      level,
    }),
    welders: welderRates(judged),
    uncovered: weldersNotCovered(joints),
  };
}

/** Joints still owing something, so a foreman knows what is left. */
export function outstandingJoints(joints) {
  requireArray(joints, 'joints');
  return joints
    .filter((joint) => !joint.shot || !joint.disposition || joint.disposition !== 'accept')
    .map((joint) => ({
      id: joint.id,
      welder: joint.welder ?? '',
      state: !joint.shot
        ? 'not shot'
        : !joint.disposition
          ? 'awaiting interpretation'
          : joint.disposition,
    }))
    .sort((a, b) => compareIds(a.id, b.id));
}

export function renderSummary(summary) {
  const { counts, rate, coverage } = summary;

  const head = [
    padCenter('RADIOGRAPHY SUMMARY', WIDTH),
    rule(WIDTH, '='),
    keyValueBlock([
      ['Job', summary.job],
      ['Called for', `${coverage.requiredFraction * 100} percent`],
    ]),
  ].join('\n');

  const progress = [
    'PROGRESS',
    rule(WIDTH),
    keyValueBlock([
      ['Joints on the job', String(counts.joints)],
      ['Shot', `${counts.shot} of ${counts.joints}`],
      ['Interpreted', `${counts.judged} of ${counts.shot}`],
      ['Awaiting interpretation', String(summary.outstanding)],
      [
        'Coverage',
        `${round(coverage.achievedFraction * 100, 1)} percent, ${
          coverage.compliant ? 'compliant' : `short by ${coverage.shortBy} joints`
        }`,
      ],
    ]),
  ].join('\n');

  const findings = [
    'FINDINGS',
    rule(WIDTH),
    keyValueBlock([
      ['Accepted', String(counts.accepted)],
      ['Repaired', String(counts.repaired)],
      ['Cut out', String(counts.rejected)],
      ['Repair rate', `${round(rate.rate * 100, 1)} percent of ${rate.shot} judged`],
    ]),
  ].join('\n');

  const welders =
    summary.welders.length === 0
      ? ['BY WELDER', rule(WIDTH), 'Nothing judged yet.'].join('\n')
      : [
          'BY WELDER',
          rule(WIDTH),
          renderTable(
            summary.welders.map((row) => ({
              welder: row.welder,
              shot: row.shot,
              repaired: row.repaired,
              rate: `${round(row.rate * 100, 1)}%`,
            })),
            [
              { key: 'welder', label: 'stencil' },
              { key: 'shot', label: 'judged', align: 'right' },
              { key: 'repaired', label: 'repaired', align: 'right' },
              { key: 'rate', label: 'rate', align: 'right' },
            ]
          ),
        ].join('\n');

  const blocks = [head, progress, findings, welders];
  if (summary.uncovered.length > 0) {
    blocks.push(
      [
        'NOT COVERED',
        rule(WIDTH),
        `These welders have no radiographed joint on this job: ${summary.uncovered.join(', ')}`,
      ].join('\n')
    );
  }
  return blocks.join('\n\n');
}
