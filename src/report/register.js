import { BadInput } from '../errors.js';
import { fixed } from '../util/num.js';
import { renderTable, rule } from '../util/text.js';
import { compareIds } from '../util/ids.js';
import { joints as registerJoints } from '../joint/register.js';
import { latestFor, currentDispositions } from '../store/query.js';
import { heading, page, widePageWidth } from './layout.js';

/**
 * The register report.
 *
 * One row per joint on the job, with what the joint is on the left and what
 * has happened to it on the right. It is the page a client asks for by name
 * and the page an auditor starts from.
 *
 * The two halves come from different places on purpose. What a joint is comes
 * from the register, which is a list. What has happened to it comes from the
 * journal, which is a history, and is read as the latest entry rather than as
 * a count of entries. A joint reopened and re-accepted appears once here with
 * its current state, and its three verdicts are still all in the journal for
 * anybody who asks why.
 */

const WIDTH = widePageWidth();

export function buildRegisterReport({ register, journal }) {
  if (typeof register !== 'object' || register === null || !Array.isArray(register.joints)) {
    throw new BadInput('a register report needs a register', { got: register });
  }
  if (typeof journal !== 'object' || journal === null || !Array.isArray(journal.entries)) {
    throw new BadInput('a register report needs a journal', { got: journal });
  }

  const rows = registerJoints(register).map((joint) => {
    const shot = latestFor(journal, { subject: joint.id, kind: 'shot' });
    const verdict = latestFor(journal, { subject: joint.id, kind: 'verdict' });
    return {
      id: joint.id,
      spool: joint.spool ?? '',
      welder: joint.welder ?? '',
      size: joint.nominalSize ? `${joint.nominalSize}/${joint.schedule}` : `${joint.odIn} OD`,
      wallIn: fixed(joint.wallIn, 3),
      shotOn: shot ? shot.day : '',
      disposition: verdict ? (verdict.payload.disposition ?? '') : '',
      clause: verdict ? (verdict.payload.clause ?? '') : '',
      state: !shot ? 'not shot' : !verdict ? 'awaiting reading' : verdict.payload.disposition,
    };
  });

  // Joints the journal knows about that never made it onto the register. This
  // is a numbering mistake somewhere and it is worth surfacing rather than
  // quietly dropping.
  const registered = new Set(register.joints.map((joint) => joint.id));
  const strays = [...new Set(journal.entries.map((entry) => entry.subject))]
    .filter((subject) => !registered.has(subject))
    .sort(compareIds);

  return { job: register.job, rows, strays };
}

/** Only the joints still owing something, for a punch list. */
export function outstandingRows(report) {
  return report.rows.filter((row) => row.state !== 'accept');
}

export function byWelderRows(report, welder) {
  return report.rows.filter((row) => row.welder === welder);
}

const COLUMNS = [
  { key: 'id', label: 'joint' },
  { key: 'spool', label: 'spool' },
  { key: 'welder', label: 'welder' },
  { key: 'size', label: 'size' },
  { key: 'wallIn', label: 'wall', align: 'right' },
  { key: 'shotOn', label: 'shot' },
  { key: 'state', label: 'state' },
  { key: 'clause', label: 'clause' },
];

export function registerColumns() {
  return COLUMNS.map((column) => ({ ...column }));
}

export function renderRegisterReport(report, { outstandingOnly = false } = {}) {
  const rows = outstandingOnly ? outstandingRows(report) : report.rows;
  const title = outstandingOnly ? 'OUTSTANDING JOINTS' : 'JOINT REGISTER';
  const blocks = [
    heading(`${title} - ${report.job}`, WIDTH),
    rows.length === 0 ? 'Nothing to report.' : renderTable(rows, COLUMNS),
  ];
  if (report.strays.length > 0) {
    blocks.push(
      '',
      'NOT ON THE REGISTER',
      rule(WIDTH),
      `The journal mentions joints the register does not carry: ${report.strays.join(', ')}`
    );
  }
  return page(blocks);
}

/** The rows in the shape the CSV export wants. */
export function exportRows(report) {
  return report.rows.map((row) => ({
    id: row.id,
    spool: row.spool,
    welder: row.welder,
    nominalSize: row.size,
    schedule: '',
    wallIn: row.wallIn,
    day: row.shotOn,
    disposition: row.disposition,
    clause: row.clause,
  }));
}

/** A count of each state, for the top of a summary. */
export function stateCounts(report) {
  const counts = {};
  for (const row of report.rows) {
    counts[row.state] = (counts[row.state] ?? 0) + 1;
  }
  return counts;
}

/** Joints the journal has judged, straight from the query layer. */
export function judgedJoints(journal) {
  return currentDispositions(journal);
}
