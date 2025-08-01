import { BadInput, Conflict } from '../errors.js';
import { requireArray, requireOneOf, requireWhole } from '../util/guard.js';
import { requireDay, daysBetween, addWorkDays } from '../util/dates.js';
import { renderTable, keyValueBlock, rule, padCenter } from '../util/text.js';
import { sequence } from '../util/ids.js';

/**
 * Nonconformance reports.
 *
 * A weld that fails is not a nonconformance; it is a weld that fails, and the
 * repair procedure covers it. A nonconformance is something that departed
 * from what was agreed: a joint shot on the wrong code, a film read by
 * somebody whose certification had lapsed, a source used past its leak test.
 * Those are findings against the process rather than against the metal, and
 * they are the ones that get an auditor interested.
 *
 * Every report carries the day it was raised and the day it is due, and the
 * due date is in working days, because a report raised on a Friday afternoon
 * is not late on Monday morning.
 */

const KINDS = ['procedure', 'documentation', 'equipment', 'calibration', 'personnel'];
const SEVERITIES = ['observation', 'minor', 'major'];
const DUE_WORKING_DAYS = { observation: 20, minor: 10, major: 3 };

export function ncrKinds() {
  return [...KINDS];
}

export function severities() {
  return [...SEVERITIES];
}

export function workingDaysFor(severity) {
  requireOneOf(severity, SEVERITIES, 'severity');
  return DUE_WORKING_DAYS[severity];
}

export function raiseNcr({
  number,
  job,
  subject = null,
  kind,
  severity = 'minor',
  detail,
  raisedOn,
  raisedBy,
}) {
  requireOneOf(kind, KINDS, 'nonconformance kind');
  requireOneOf(severity, SEVERITIES, 'severity');
  requireDay(raisedOn, 'raised date');
  if (typeof detail !== 'string' || detail.trim() === '') {
    throw new BadInput('a nonconformance says what was wrong', { got: detail });
  }
  if (typeof raisedBy !== 'string' || raisedBy.trim() === '') {
    throw new BadInput('a nonconformance names who raised it', { got: raisedBy });
  }
  if (!Number.isInteger(number) || number < 1) {
    throw new BadInput('a nonconformance is numbered from one', { got: number });
  }

  return {
    id: sequence('NCR', number),
    job: job ?? null,
    subject,
    kind,
    severity,
    detail: detail.trim(),
    raisedOn,
    raisedBy: raisedBy.trim(),
    dueBy: addWorkDays(raisedOn, workingDaysFor(severity)),
    closedOn: null,
    closedBy: null,
    action: null,
  };
}

export function closeNcr(ncr, { day, by, action }) {
  requireDay(day, 'closing date');
  if (ncr.closedOn !== null) {
    throw new Conflict(`${ncr.id} was closed on ${ncr.closedOn}`, { id: ncr.id });
  }
  if (daysBetween(ncr.raisedOn, day) < 0) {
    throw new Conflict('a report cannot be closed before it was raised', {
      raisedOn: ncr.raisedOn,
      day,
    });
  }
  if (typeof action !== 'string' || action.trim() === '') {
    throw new BadInput('closing a nonconformance says what was done about it', { got: action });
  }
  return { ...ncr, closedOn: day, closedBy: by ?? null, action: action.trim() };
}

export function isOpen(ncr) {
  return ncr.closedOn === null;
}

export function openNcrs(reports) {
  requireArray(reports, 'reports');
  return reports.filter((ncr) => isOpen(ncr));
}

/** Open reports past their due date, worst and oldest first. */
export function overdueNcrs(reports, day) {
  requireDay(day, 'date');
  return openNcrs(reports)
    .filter((ncr) => daysBetween(ncr.dueBy, day) > 0)
    .map((ncr) => ({ ...ncr, daysOverdue: daysBetween(ncr.dueBy, day) }))
    .sort(
      (a, b) =>
        SEVERITIES.indexOf(b.severity) - SEVERITIES.indexOf(a.severity) ||
        b.daysOverdue - a.daysOverdue
    );
}

/** How long open reports have been open, bucketed the way an auditor asks. */
export function aging(reports, day, buckets = [7, 30, 90]) {
  requireDay(day, 'date');
  requireArray(buckets, 'buckets');
  buckets.forEach((bucket) => requireWhole(bucket, 'bucket'));
  const counts = {};
  const labels = [
    ...buckets.map((bucket) => `under ${bucket}`),
    `over ${buckets[buckets.length - 1]}`,
  ];
  for (const label of labels) counts[label] = 0;

  for (const ncr of openNcrs(reports)) {
    const age = daysBetween(ncr.raisedOn, day);
    const bucket = buckets.find((limit) => age < limit);
    counts[bucket === undefined ? labels[labels.length - 1] : `under ${bucket}`] += 1;
  }
  return counts;
}

/** Turn a contract deviation straight into a report, so nothing is retyped. */
export function fromDeviation({ deviation, job, number, raisedOn, raisedBy, subject = null }) {
  return raiseNcr({
    number,
    job,
    subject,
    kind: 'procedure',
    severity: deviation.field === 'code' ? 'major' : 'minor',
    detail: `${deviation.field} was ${deviation.actual} where ${deviation.agreed} was agreed`,
    raisedOn,
    raisedBy,
  });
}

export function renderNcrs(reports, { day, job = null } = {}) {
  const open = openNcrs(reports);
  const rows = open.map((ncr) => ({
    id: ncr.id,
    kind: ncr.kind,
    severity: ncr.severity,
    subject: ncr.subject ?? '',
    raised: ncr.raisedOn,
    due: ncr.dueBy,
  }));
  const blocks = [
    padCenter('OPEN NONCONFORMANCES', 78),
    rule(78, '='),
    keyValueBlock([
      ['Job', job ?? ''],
      ['As at', day ?? ''],
      ['Open', String(open.length)],
    ]),
    rows.length === 0
      ? 'Nothing open.'
      : renderTable(rows, [
          { key: 'id', label: 'report' },
          { key: 'kind', label: 'kind' },
          { key: 'severity', label: 'severity' },
          { key: 'subject', label: 'against' },
          { key: 'raised', label: 'raised' },
          { key: 'due', label: 'due' },
        ]),
  ];
  return blocks.join('\n\n');
}
