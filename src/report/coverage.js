import { BadInput } from '../errors.js';
import { round } from '../util/num.js';
import { requireArray, requireLabel } from '../util/guard.js';
import { renderTable, keyValueBlock } from '../util/text.js';
import { heading, block, page, pageWidth } from './layout.js';
import {
  levelFraction,
  levelLabel,
  jointsToShoot,
  coverageAchieved,
} from '../sampling/coverage.js';
import { rateOf } from '../util/rate.js';

/**
 * The coverage report.
 *
 * The overall percentage is the number a contract is written in and the least
 * useful one on the page. A job at ten percent overall can have a welder who
 * has never been shot, a spool that was skipped entirely, and a run of joints
 * all covered because they happened to be easy to reach.
 *
 * So the page leads with the overall figure because that is what was asked
 * for, and then immediately breaks it apart, because that is where the
 * finding will be.
 */

const WIDTH = pageWidth();

export function buildCoverage({ job, joints, level = 'five' }) {
  requireLabel(job, 'a coverage report names the job it covers');
  requireArray(joints, 'joints');

  const shot = joints.filter((joint) => joint.shot);
  const overall = coverageAchieved({
    totalJoints: joints.length,
    shotJoints: shot.length,
    level,
  });

  return {
    job: job.trim(),
    level,
    overall,
    byWelder: groupCoverage(joints, (joint) => joint.welder ?? 'unknown', level),
    bySpool: groupCoverage(joints, (joint) => joint.spool ?? 'unassigned', level),
    uncoveredWelders: uncoveredIn(joints, (joint) => joint.welder ?? 'unknown'),
    uncoveredSpools: uncoveredIn(joints, (joint) => joint.spool ?? 'unassigned'),
  };
}

function groupCoverage(joints, keyOf, level) {
  const groups = new Map();
  for (const joint of joints) {
    const key = keyOf(joint);
    if (!groups.has(key)) groups.set(key, { total: 0, shot: 0 });
    const entry = groups.get(key);
    entry.total += 1;
    if (joint.shot) entry.shot += 1;
  }
  return [...groups.entries()]
    .map(([key, counts]) => ({
      key,
      total: counts.total,
      shot: counts.shot,
      required: jointsToShoot(counts.total, level),
      fraction: rateOf(counts.shot, counts.total),
      compliant: counts.shot >= jointsToShoot(counts.total, level),
    }))
    .sort((a, b) => a.fraction - b.fraction || a.key.localeCompare(b.key));
}

function uncoveredIn(joints, keyOf) {
  const seen = new Map();
  for (const joint of joints) {
    const key = keyOf(joint);
    seen.set(key, (seen.get(key) ?? false) || Boolean(joint.shot));
  }
  return [...seen.entries()]
    .filter(([, covered]) => !covered)
    .map(([key]) => key)
    .sort();
}

/** Whether the job clears the contract, and everything that does not. */
export function shortfalls(report) {
  const found = [];
  if (!report.overall.compliant) {
    found.push({ what: 'the job', shortBy: report.overall.shortBy });
  }
  for (const row of report.byWelder) {
    if (!row.compliant) {
      found.push({ what: `welder ${row.key}`, shortBy: row.required - row.shot });
    }
  }
  for (const row of report.bySpool) {
    if (!row.compliant) {
      found.push({ what: `spool ${row.key}`, shortBy: row.required - row.shot });
    }
  }
  return found;
}

function table(rows, label) {
  if (rows.length === 0) return 'Nothing to break down.';
  return renderTable(
    rows.map((row) => ({
      key: row.key,
      total: row.total,
      shot: row.shot,
      required: row.required,
      percent: `${round(row.fraction * 100, 1)}%`,
      state: row.compliant ? 'ok' : 'short',
    })),
    [
      { key: 'key', label },
      { key: 'total', label: 'joints', align: 'right' },
      { key: 'shot', label: 'shot', align: 'right' },
      { key: 'required', label: 'required', align: 'right' },
      { key: 'percent', label: 'coverage', align: 'right' },
      { key: 'state', label: 'state' },
    ]
  );
}

export function renderCoverage(report) {
  const blocks = [
    heading(`RADIOGRAPHY COVERAGE - ${report.job}`, WIDTH),
    block(
      'OVERALL',
      keyValueBlock([
        ['Called for', levelLabel(report.level)],
        ['Joints', String(report.overall.totalJoints)],
        ['Shot', String(report.overall.shotJoints)],
        ['Required', String(report.overall.requiredJoints)],
        ['Achieved', `${round(report.overall.achievedFraction * 100, 1)} percent`],
        [
          'State',
          report.overall.compliant ? 'compliant' : `short by ${report.overall.shortBy}`,
        ],
      ]),
      WIDTH
    ),
    block('BY WELDER', table(report.byWelder, 'welder'), WIDTH),
    block('BY SPOOL', table(report.bySpool, 'spool'), WIDTH),
  ];

  const missed = [];
  if (report.uncoveredWelders.length > 0) {
    missed.push(`Welders with nothing shot: ${report.uncoveredWelders.join(', ')}`);
  }
  if (report.uncoveredSpools.length > 0) {
    missed.push(`Spools with nothing shot: ${report.uncoveredSpools.join(', ')}`);
  }
  if (missed.length > 0) {
    blocks.push(block('NOT COVERED AT ALL', missed.join('\n'), WIDTH));
  }
  return page(blocks);
}

/** The fraction the level asks for, so a caller need not import two modules. */
export function requiredFraction(level) {
  const fraction = levelFraction(level);
  if (!Number.isFinite(fraction)) {
    throw new BadInput('that level has no fraction', { level });
  }
  return fraction;
}
