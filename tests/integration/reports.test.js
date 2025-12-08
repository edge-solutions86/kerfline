import { describe, it, expect } from 'vitest';
import { makeRegister, addJoints } from '../../src/joint/register.js';
import { makeJournal, appendAll } from '../../src/store/journal.js';
import { buildRegisterReport, stateCounts } from '../../src/report/register.js';
import { buildSummary } from '../../src/report/summary.js';
import { buildCoverage } from '../../src/report/coverage.js';
import { buildDaily, dailyTotals } from '../../src/report/daily.js';
import { runAudit } from '../../src/job/audit.js';
import { exportRegister, verifyChecksum, toCsv } from '../../src/report/export.js';
import { jointState } from '../../src/pipeline/close.js';
import { renderRegisterReport } from '../../src/report/register.js';
import { renderSummary } from '../../src/report/summary.js';
import { renderCoverage } from '../../src/report/coverage.js';
import { renderDaily } from '../../src/report/daily.js';
import { renderAudit } from '../../src/job/audit.js';

/**
 * Every report on one job, cross checked against every other.
 *
 * Each report has its own tests and each of them passes. What none of them
 * can see is the two reports disagreeing: the summary saying five joints were
 * shot while the register report shows four, because one of them counted
 * entries and the other counted subjects. That disagreement is invisible to a
 * unit test and obvious to a client reading both pages, which is the worst
 * possible place to find it.
 *
 * So this asserts the reports agree, and it deliberately derives the same
 * number two different ways to do it.
 */

const entry = (kind, day, subject, payload = {}) => ({ kind, day, subject, payload });

const register = addJoints(makeRegister('C-4471'), [
  { id: 'J-1', welder: 'A17', spool: 'SP-1', nominalSize: 6, schedule: 40 },
  { id: 'J-2', welder: 'A17', spool: 'SP-1', nominalSize: 6, schedule: 40 },
  { id: 'J-3', welder: 'B04', spool: 'SP-2', nominalSize: 6, schedule: 40 },
  { id: 'J-10', welder: 'B04', spool: 'SP-2', nominalSize: 2, schedule: 40 },
]);

// J-1 accepted, J-2 repaired then accepted, J-3 shot but unread, J-10 not shot.
const journal = appendAll(makeJournal('C-4471'), [
  entry('shot', '2025-03-10', 'J-1', { films: 3 }),
  entry('shot', '2025-03-10', 'J-2', { films: 3 }),
  entry('shot', '2025-03-10', 'J-3', { films: 3 }),
  entry('verdict', '2025-03-11', 'J-1', { disposition: 'accept', clause: '9.3' }),
  entry('verdict', '2025-03-11', 'J-2', { disposition: 'repair', clause: '9.3.6' }),
  entry('repair', '2025-03-13', 'J-2', { at: '3:00' }),
  entry('shot', '2025-03-14', 'J-2', { films: 3 }),
  entry('verdict', '2025-03-14', 'J-2', { disposition: 'accept', clause: '9.3' }),
]);

const registerReport = buildRegisterReport({ register, journal });

const jointRows = register.joints.map((joint) => {
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

const summary = buildSummary({ job: 'C-4471', joints: jointRows, level: 'ten' });
const coverage = buildCoverage({ job: 'C-4471', joints: jointRows, level: 'ten' });
const audit = runAudit({ job: 'C-4471', register, journal, level: 'ten', day: '2025-03-20' });

describe('the reports agree on how many joints there are', () => {
  it('the register report has a row per registered joint', () => {
    expect(registerReport.rows).toHaveLength(register.joints.length);
  });

  it('the summary counts the same joints', () => {
    expect(summary.counts.joints).toBe(register.joints.length);
  });

  it('the coverage report counts the same joints', () => {
    expect(coverage.overall.totalJoints).toBe(register.joints.length);
  });

  it('the audit checked the same joints', () => {
    expect(audit.checked.joints).toBe(register.joints.length);
  });
});

describe('the reports agree on what has been shot', () => {
  const shotFromRegisterReport = registerReport.rows.filter((row) => row.shotOn !== '').length;

  it('the register report and the summary agree', () => {
    expect(summary.counts.shot).toBe(shotFromRegisterReport);
  });

  it('the coverage report agrees with both', () => {
    expect(coverage.overall.shotJoints).toBe(shotFromRegisterReport);
  });

  it('counts a joint shot twice as one joint, not two', () => {
    // J-2 has two shot entries. Counting entries rather than subjects would
    // give four shot joints out of four registered, which is wrong twice.
    expect(shotFromRegisterReport).toBe(3);
    expect(journal.entries.filter((e) => e.kind === 'shot')).toHaveLength(4);
  });
});

describe('the reports agree on the dispositions', () => {
  it('the register states and the summary counts line up', () => {
    const states = stateCounts(registerReport);
    expect(states.accept).toBe(summary.counts.accepted);
  });

  it('a joint repaired and then accepted counts once, as accepted', () => {
    const row = registerReport.rows.find((r) => r.id === 'J-2');
    expect(row.state).toBe('accept');
    expect(
      journal.entries.filter((e) => e.kind === 'verdict' && e.subject === 'J-2')
    ).toHaveLength(2);
    expect(summary.counts.repaired).toBe(0);
  });

  it('a joint shot but unread is not counted as judged anywhere', () => {
    expect(registerReport.rows.find((r) => r.id === 'J-3').state).toBe('awaiting reading');
    expect(summary.outstanding).toBe(1);
  });

  it('the audit finds exactly the unread joint', () => {
    const found = audit.findings.find((f) => f.check === 'films not read');
    expect(found.items).toEqual(['J-3']);
  });
});

describe('the reports agree on coverage', () => {
  it('the summary and the coverage report reach the same fraction', () => {
    expect(summary.coverage.achievedFraction).toBeCloseTo(coverage.overall.achievedFraction, 6);
  });

  it('both agree on whether the job is compliant', () => {
    expect(summary.coverage.compliant).toBe(coverage.overall.compliant);
  });

  it('the per welder breakdown adds back to the whole', () => {
    const total = coverage.byWelder.reduce((sum, row) => sum + row.total, 0);
    const shot = coverage.byWelder.reduce((sum, row) => sum + row.shot, 0);
    expect(total).toBe(coverage.overall.totalJoints);
    expect(shot).toBe(coverage.overall.shotJoints);
  });

  it('the per spool breakdown adds back to the same whole', () => {
    const total = coverage.bySpool.reduce((sum, row) => sum + row.total, 0);
    expect(total).toBe(coverage.overall.totalJoints);
  });
});

describe('the daily report adds up to the job', () => {
  // Every day the journal touched, not a guess at which ones matter. Missing
  // one is exactly how a set of daily reports stops summing to the job.
  const days = [...new Set(journal.entries.map((e) => e.day))].sort();
  const dailies = days.map((day) => buildDaily({ job: 'C-4471', day, journal, source: null }));

  it('covers every day the journal touched', () => {
    expect(days).toEqual(['2025-03-10', '2025-03-11', '2025-03-13', '2025-03-14']);
  });

  it('the films exposed across the days match the journal', () => {
    const fromDays = dailies.reduce((sum, daily) => sum + dailyTotals(daily).films, 0);
    const fromJournal = journal.entries
      .filter((e) => e.kind === 'shot')
      .reduce((sum, e) => sum + (e.payload.films ?? 0), 0);
    expect(fromDays).toBe(fromJournal);
  });

  it('the readings across the days match the verdicts', () => {
    const read = dailies.reduce((sum, daily) => sum + dailyTotals(daily).read, 0);
    expect(read).toBe(journal.entries.filter((e) => e.kind === 'verdict').length);
  });

  it('the joints shot across the days match the shot entries', () => {
    const shot = dailies.reduce((sum, daily) => sum + daily.jointsShot.length, 0);
    expect(shot).toBe(journal.entries.filter((e) => e.kind === 'shot').length);
  });
});

describe('the export carries what the register report shows', () => {
  const csv = exportRegister(
    registerReport.rows.map((row) => ({ ...row, nominalSize: row.size, schedule: '' }))
  );

  it('checksums clean', () => {
    expect(verifyChecksum(csv).intact).toBe(true);
  });

  it('has a line per joint plus a header and a checksum', () => {
    expect(csv.split('\n')).toHaveLength(registerReport.rows.length + 2);
  });

  it('names every joint on the register', () => {
    for (const joint of register.joints) {
      expect(csv).toContain(joint.id);
    }
  });

  it('exports the same rows whichever way they are shaped', () => {
    const plain = toCsv(registerReport.rows, [{ key: 'id' }]);
    expect(plain.split('\n')).toHaveLength(registerReport.rows.length + 1);
  });
});

describe('every report renders inside its page', () => {
  const pages = [
    { text: renderRegisterReport(registerReport), width: 96 },
    { text: renderSummary(summary), width: 78 },
    { text: renderCoverage(coverage), width: 78 },
    {
      text: renderDaily(
        buildDaily({ job: 'C-4471', day: '2025-03-10', journal, source: null })
      ),
      width: 78,
    },
    { text: renderAudit(audit), width: 78 },
  ];

  it('prints something on every page', () => {
    for (const page of pages) {
      expect(page.text.length).toBeGreaterThan(50);
    }
  });

  it('keeps every line inside the width that page is set at', () => {
    for (const page of pages) {
      for (const line of page.text.split('\n')) {
        expect(line.length).toBeLessThanOrEqual(page.width);
      }
    }
  });

  it('names the job on every page', () => {
    for (const page of pages) {
      expect(page.text).toContain('C-4471');
    }
  });
});
