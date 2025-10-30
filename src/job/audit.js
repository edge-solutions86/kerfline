import { requireArray, requireLabel } from '../util/guard.js';
import { compareIds } from '../util/ids.js';
import { renderTable } from '../util/text.js';
import { heading, block, page, pageWidth } from '../report/layout.js';
import { subjects, byKind, awaitingVerdict } from '../store/query.js';
import { verify } from '../store/journal.js';
import { reopenings } from '../store/snapshot.js';
import { buildCoverage, shortfalls } from '../report/coverage.js';
import { untraceableJoints } from '../joint/heat.js';
import { overdueNcrs, openNcrs } from '../report/nonconformance.js';

/**
 * What an audit would find.
 *
 * Run before the auditor arrives rather than after. Every check here is one
 * that has actually been raised against this shop at some point, and each one
 * is cheap to answer now and expensive to answer in a meeting.
 *
 * The findings are graded, and the grading is not by how much work each one
 * is to fix. A broken checksum chain is severe because it means the record
 * cannot be relied on at all, and one joint short of coverage is minor
 * because it is a joint. Sorting by effort would put those the wrong way
 * round, which is exactly what a spreadsheet of findings usually does.
 */

const WIDTH = pageWidth();
const SEVERITY = ['minor', 'major', 'severe'];

export function severities() {
  return [...SEVERITY];
}

function finding(severity, check, detail, items = []) {
  return { severity, check, detail, items, count: items.length };
}

/** Every check, run. Returns findings only; a clean check produces nothing. */
export function runAudit({
  job,
  register,
  journal,
  heats = [],
  nonconformances = [],
  day,
  level = 'five',
}) {
  requireLabel(job, 'an audit names the job it covers');
  requireArray(heats, 'heats');
  requireArray(nonconformances, 'nonconformances');

  const findings = [];

  const chain = verify(journal);
  if (!chain.intact) {
    findings.push(
      finding('severe', 'record integrity', `the journal breaks at entry ${chain.brokenAt}`, [
        String(chain.brokenAt),
      ])
    );
  }

  const registered = new Set(register.joints.map((joint) => joint.id));
  const strays = subjects(journal).filter((subject) => !registered.has(subject));
  if (strays.length > 0) {
    findings.push(
      finding(
        'major',
        'joints not on the register',
        'the journal mentions joints the register does not carry',
        strays
      )
    );
  }

  const shotIds = new Set(byKind(journal, 'shot').map((entry) => entry.subject));
  const neverShot = [...registered].filter((id) => !shotIds.has(id)).sort(compareIds);

  const unread = awaitingVerdict(journal);
  if (unread.length > 0) {
    findings.push(
      finding('major', 'films not read', 'these joints were shot and never interpreted', unread)
    );
  }

  const reopened = reopenings(journal);
  if (reopened.length > 0) {
    findings.push(
      finding(
        'major',
        'joints reopened after acceptance',
        'an accepted joint was later called something else',
        reopened.map((row) => `${row.subject} on ${row.day}`)
      )
    );
  }

  const traceless = untraceableJoints(
    register.joints.filter((joint) => joint.heats !== undefined),
    heats
  );
  if (traceless.length > 0) {
    findings.push(
      finding(
        'major',
        'material traceability',
        'these joints cannot be traced to a mill report',
        traceless.map((trace) => trace.jointId)
      )
    );
  }

  const coverage = buildCoverage({
    job,
    joints: register.joints.map((joint) => ({ ...joint, shot: shotIds.has(joint.id) })),
    level,
  });
  const short = shortfalls(coverage);
  if (short.length > 0) {
    findings.push(
      finding(
        'minor',
        'coverage',
        'these are short of the radiography called for',
        short.map((entry) => `${entry.what} by ${entry.shortBy}`)
      )
    );
  }

  const late = day ? overdueNcrs(nonconformances, day) : [];
  if (late.length > 0) {
    findings.push(
      finding(
        'major',
        'nonconformances past due',
        'these reports are open past their due date',
        late.map((ncr) => `${ncr.id} by ${ncr.daysLate} days`)
      )
    );
  }

  return {
    job: job.trim(),
    day: day ?? null,
    findings: findings.sort(
      (a, b) => SEVERITY.indexOf(b.severity) - SEVERITY.indexOf(a.severity) || b.count - a.count
    ),
    clean: findings.length === 0,
    checked: {
      joints: register.joints.length,
      entries: journal.entries.length,
      neverShot: neverShot.length,
      openNonconformances: openNcrs(nonconformances).length,
    },
  };
}

/** Findings at or above a severity, for a report that only wants the bad news. */
export function atLeast(audit, severity) {
  const floor = SEVERITY.indexOf(severity);
  return audit.findings.filter((entry) => SEVERITY.indexOf(entry.severity) >= floor);
}

export function renderAudit(audit) {
  const blocks = [heading(`AUDIT READINESS - ${audit.job}`, WIDTH)];

  if (audit.clean) {
    blocks.push(block('FINDINGS', 'Nothing found.', WIDTH));
  } else {
    blocks.push(
      block(
        'FINDINGS',
        renderTable(
          audit.findings.map((entry) => ({
            severity: entry.severity,
            check: entry.check,
            count: entry.count,
          })),
          [
            { key: 'severity', label: 'severity' },
            { key: 'check', label: 'check' },
            { key: 'count', label: 'items', align: 'right' },
          ]
        ),
        WIDTH
      )
    );
    for (const entry of audit.findings) {
      blocks.push(
        block(
          entry.check.toUpperCase(),
          [entry.detail, ...entry.items.map((item) => `  - ${item}`)].join('\n'),
          WIDTH
        )
      );
    }
  }
  return page(blocks);
}
