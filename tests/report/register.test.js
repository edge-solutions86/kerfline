import { describe, it, expect } from 'vitest';
import {
  buildRegisterReport,
  outstandingRows,
  byWelderRows,
  registerColumns,
  renderRegisterReport,
  exportRows,
  stateCounts,
  judgedJoints,
} from '../../src/report/register.js';
import { makeRegister, addJoints } from '../../src/joint/register.js';
import { makeJournal, appendAll } from '../../src/store/journal.js';

const register = addJoints(makeRegister('C-4471'), [
  { id: 'J-2', spool: 'SP-1', welder: 'A17', nominalSize: 6, schedule: 40 },
  { id: 'J-10', spool: 'SP-1', welder: 'A17', nominalSize: 6, schedule: 40 },
  { id: 'J-3', spool: 'SP-2', welder: 'B04', nominalSize: 2, schedule: 40 },
]);

const entry = (kind, day, subject, payload = {}) => ({ kind, day, subject, payload });

// The journal only takes entries in date order, so the fixture is written
// the way the day actually ran rather than one joint at a time.
const journal = appendAll(makeJournal('C-4471'), [
  entry('shot', '2025-03-10', 'J-2'),
  entry('shot', '2025-03-10', 'J-3'),
  entry('verdict', '2025-03-11', 'J-2', { disposition: 'repair', clause: '9.3.6' }),
  entry('verdict', '2025-03-14', 'J-2', { disposition: 'accept', clause: '9.3' }),
]);

const report = buildRegisterReport({ register, journal });

describe('buildRegisterReport', () => {
  it('makes one row per joint on the register', () => {
    expect(report.rows).toHaveLength(3);
  });

  it('reads in register order', () => {
    expect(report.rows.map((row) => row.id)).toEqual(['J-2', 'J-3', 'J-10']);
  });

  it('takes what a joint is from the register', () => {
    const row = report.rows.find((r) => r.id === 'J-2');
    expect(row.welder).toBe('A17');
    expect(row.size).toBe('6/40');
    expect(row.wallIn).toBe('0.280');
  });

  it('takes what happened from the journal', () => {
    const row = report.rows.find((r) => r.id === 'J-2');
    expect(row.shotOn).toBe('2025-03-10');
    expect(row.disposition).toBe('accept');
    expect(row.clause).toBe('9.3');
  });

  it('reports the latest verdict and not the first', () => {
    const row = report.rows.find((r) => r.id === 'J-2');
    expect(row.disposition).toBe('accept');
    expect(row.state).toBe('accept');
  });

  it('marks a joint shot but not read', () => {
    expect(report.rows.find((r) => r.id === 'J-3').state).toBe('awaiting reading');
  });

  it('marks a joint that has not been shot at all', () => {
    expect(report.rows.find((r) => r.id === 'J-10').state).toBe('not shot');
  });

  it('finds nothing stray when the journal stays on the register', () => {
    expect(report.strays).toEqual([]);
  });

  it('surfaces a joint the journal knows and the register does not', () => {
    const withStray = appendAll(journal, [entry('shot', '2025-03-15', 'J-99')]);
    const other = buildRegisterReport({ register, journal: withStray });
    expect(other.strays).toEqual(['J-99']);
  });

  it('refuses a report with no register or no journal', () => {
    expect(() => buildRegisterReport({ register: {}, journal })).toThrow(/needs a register/);
    expect(() => buildRegisterReport({ register, journal: {} })).toThrow(/needs a journal/);
  });
});

describe('filtering', () => {
  it('lists only what is still owing', () => {
    expect(outstandingRows(report).map((row) => row.id)).toEqual(['J-3', 'J-10']);
  });

  it('lists one welder work', () => {
    expect(byWelderRows(report, 'A17').map((row) => row.id)).toEqual(['J-2', 'J-10']);
  });

  it('counts the states', () => {
    expect(stateCounts(report)).toEqual({
      accept: 1,
      'awaiting reading': 1,
      'not shot': 1,
    });
  });
});

describe('renderRegisterReport', () => {
  it('prints the heading and the job', () => {
    const text = renderRegisterReport(report);
    expect(text).toContain('JOINT REGISTER');
    expect(text).toContain('C-4471');
  });

  it('prints a row per joint under the column headings', () => {
    const text = renderRegisterReport(report);
    expect(text).toContain('joint');
    expect(text).toContain('welder');
    expect(text).toContain('J-10');
  });

  it('prints only the outstanding ones when asked', () => {
    const text = renderRegisterReport(report, { outstandingOnly: true });
    expect(text).toContain('OUTSTANDING JOINTS');
    expect(text).not.toContain('J-2 ');
  });

  it('says so plainly when there is nothing outstanding', () => {
    const clean = buildRegisterReport({
      register: addJoints(makeRegister('C-1'), [{ id: 'J-1', nominalSize: 6, schedule: 40 }]),
      journal: appendAll(makeJournal('C-1'), [
        entry('shot', '2025-03-10', 'J-1'),
        entry('verdict', '2025-03-11', 'J-1', { disposition: 'accept' }),
      ]),
    });
    expect(renderRegisterReport(clean, { outstandingOnly: true })).toContain(
      'Nothing to report'
    );
  });

  it('calls out a stray joint at the foot', () => {
    const withStray = appendAll(journal, [entry('shot', '2025-03-15', 'J-99')]);
    const other = buildRegisterReport({ register, journal: withStray });
    const text = renderRegisterReport(other);
    expect(text).toContain('NOT ON THE REGISTER');
    expect(text).toContain('J-99');
  });

  it('keeps every line inside the paper width', () => {
    for (const line of renderRegisterReport(report).split('\n')) {
      expect(line.length).toBeLessThanOrEqual(96);
    }
  });
});

describe('exportRows and the columns', () => {
  it('names the columns for the printed table', () => {
    expect(registerColumns().map((column) => column.key)).toContain('welder');
  });

  it('hands back copies of the columns', () => {
    const columns = registerColumns();
    columns[0].label = 'changed';
    expect(registerColumns()[0].label).toBe('joint');
  });

  it('shapes the rows for the CSV export', () => {
    const rows = exportRows(report);
    expect(rows[0]).toHaveProperty('disposition');
    expect(rows[0]).toHaveProperty('day');
    expect(rows).toHaveLength(3);
  });
});

describe('judgedJoints', () => {
  it('reads the current disposition of everything judged', () => {
    const judged = judgedJoints(journal);
    expect(judged).toHaveLength(1);
    expect(judged[0]).toMatchObject({ subject: 'J-2', disposition: 'accept' });
  });
});
