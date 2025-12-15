import { requireArray, requireLabel } from '../util/guard.js';
import { requireDay } from '../util/dates.js';
import { rule } from '../util/text.js';
import { pageWidth } from './layout.js';
import { jointState } from '../pipeline/close.js';
import { buildRegisterReport, renderRegisterReport } from './register.js';
import { buildSummary, renderSummary } from './summary.js';
import { buildCoverage, renderCoverage } from './coverage.js';
import { renderNcrs } from './nonconformance.js';
import { renderReview } from './filmreview.js';
import { runAudit, renderAudit } from '../job/audit.js';
import { exportRegister } from './export.js';

/**
 * The whole folder, in one call.
 *
 * At the end of a job somebody assembles six documents that all describe the
 * same work, and assembling them by hand is where they stop agreeing: the
 * summary gets rebuilt on Tuesday and the register on Thursday and two joints
 * moved in between.
 *
 * So the pack is built once, from one register and one journal, and every
 * page in it is derived from that same pair. Two pages of a pack cannot
 * disagree, because there is only one reading of the record behind them.
 */

const WIDTH = pageWidth();

/** Turn the register and the journal into the rows every report wants. */
export function jointRows(register, journal) {
  return register.joints.map((joint) => {
    const state = jointState(journal, joint.id);
    return {
      id: joint.id,
      welder: joint.welder,
      spool: joint.spool,
      shot: state.everShot,
      disposition:
        state.status === 'accepted' ? 'accept' : state.status === 'cut out' ? 'reject' : null,
    };
  });
}

export function buildPack({
  job,
  register,
  journal,
  day,
  level = 'five',
  nonconformances = [],
  reviews = [],
  heats = [],
}) {
  requireLabel(job, 'a document pack belongs to a job');
  requireDay(day, 'pack date');
  requireArray(nonconformances, 'nonconformances');
  requireArray(reviews, 'reviews');

  const rows = jointRows(register, journal);

  return {
    job: job.trim(),
    day,
    level,
    register: buildRegisterReport({ register, journal }),
    summary: buildSummary({ job, joints: rows, level }),
    coverage: buildCoverage({ job, joints: rows, level }),
    audit: runAudit({ job, register, journal, heats, nonconformances, day, level }),
    nonconformances,
    reviews,
  };
}

/**
 * What is in the folder, in the order it goes in, and how much is on each
 * page. A contents list that only names the pages cannot tell a reader that
 * the nonconformance page is empty, which is the thing they most want to know
 * before turning to it.
 */
export function packContents(pack) {
  return [
    { page: 1, title: 'Radiography summary', items: pack.summary.counts.joints },
    { page: 2, title: 'Joint register', items: pack.register.rows.length },
    { page: 3, title: 'Radiography coverage', items: pack.coverage.byWelder.length },
    { page: 4, title: 'Second reading', items: pack.reviews.length },
    { page: 5, title: 'Open nonconformances', items: pack.nonconformances.length },
    { page: 6, title: 'Audit readiness', items: pack.audit.findings.length },
  ];
}

/** The pack as one document, pages separated by a rule. */
export function renderPack(pack) {
  const pages = [
    renderSummary(pack.summary),
    renderRegisterReport(pack.register),
    renderCoverage(pack.coverage),
    renderReview(pack.reviews, { job: pack.job }),
    renderNcrs(pack.nonconformances, { job: pack.job, day: pack.day }),
    renderAudit(pack.audit),
  ];
  return pages.join(`\n\n${rule(WIDTH, '_')}\n\n`);
}

/** The machine readable half of the pack, for a client system. */
export function packExport(pack) {
  return exportRegister(
    pack.register.rows.map((row) => ({ ...row, nominalSize: row.size, schedule: '' }))
  );
}

/**
 * Whether the pack is fit to issue. A pack with a severe audit finding in it
 * is a pack that says the record cannot be relied on, and issuing it with a
 * covering note is not the same as fixing it.
 */
export function issuable(pack) {
  const severe = pack.audit.findings.filter((finding) => finding.severity === 'severe');
  const reasons = [];
  if (severe.length > 0) {
    reasons.push(...severe.map((finding) => finding.detail));
  }
  if (!pack.coverage.overall.compliant) {
    reasons.push(`coverage is short by ${pack.coverage.overall.shortBy} joints`);
  }
  if (pack.summary.outstanding > 0) {
    reasons.push(`${pack.summary.outstanding} film(s) shot and not read`);
  }
  return { issuable: reasons.length === 0, reasons };
}
