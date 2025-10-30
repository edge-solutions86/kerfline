import { describe, it, expect } from 'vitest';
import { severities, runAudit, atLeast, renderAudit } from '../../src/job/audit.js';
import { makeRegister, addJoints } from '../../src/joint/register.js';
import { makeJournal, appendAll } from '../../src/store/journal.js';
import { makeHeat, assignHeats } from '../../src/joint/heat.js';
import { raiseNcr } from '../../src/report/nonconformance.js';

const entry = (kind, day, subject, payload = {}) => ({ kind, day, subject, payload });

const register = addJoints(makeRegister('C-4471'), [
  { id: 'J-1', welder: 'A17', spool: 'SP-1', nominalSize: 6, schedule: 40 },
  { id: 'J-2', welder: 'A17', spool: 'SP-1', nominalSize: 6, schedule: 40 },
]);

const clean = appendAll(makeJournal('C-4471'), [
  entry('shot', '2025-03-10', 'J-1'),
  entry('shot', '2025-03-10', 'J-2'),
  entry('verdict', '2025-03-11', 'J-1', { disposition: 'accept' }),
  entry('verdict', '2025-03-11', 'J-2', { disposition: 'accept' }),
]);

describe('runAudit on a clean job', () => {
  const audit = runAudit({ job: 'C-4471', register, journal: clean, day: '2025-03-20' });

  it('finds nothing', () => {
    expect(audit.clean).toBe(true);
    expect(audit.findings).toEqual([]);
  });

  it('still says what it checked', () => {
    expect(audit.checked.joints).toBe(2);
    expect(audit.checked.entries).toBe(4);
  });

  it('names the severities it grades by', () => {
    expect(severities()).toEqual(['minor', 'major', 'severe']);
  });

  it('refuses an audit with no job', () => {
    expect(() => runAudit({ job: ' ', register, journal: clean })).toThrow(/names the job/);
  });
});

describe('the checks', () => {
  it('catches a broken chain as severe', () => {
    const tampered = {
      name: clean.name,
      entries: clean.entries.map((e) => (e.seq === 2 ? { ...e, subject: 'J-9' } : e)),
    };
    const audit = runAudit({ job: 'C-4471', register, journal: tampered });
    const found = audit.findings.find((f) => f.check === 'record integrity');
    expect(found.severity).toBe('severe');
  });

  it('catches a joint the journal knows and the register does not', () => {
    const stray = appendAll(clean, [entry('shot', '2025-03-12', 'J-99')]);
    const audit = runAudit({ job: 'C-4471', register, journal: stray });
    const found = audit.findings.find((f) => f.check === 'joints not on the register');
    expect(found.items).toContain('J-99');
  });

  it('catches films that were shot and never read', () => {
    const unread = appendAll(makeJournal('C-4471'), [entry('shot', '2025-03-10', 'J-1')]);
    const audit = runAudit({ job: 'C-4471', register, journal: unread });
    expect(audit.findings.some((f) => f.check === 'films not read')).toBe(true);
  });

  it('catches a joint reopened after it was accepted', () => {
    const reopened = appendAll(clean, [
      entry('verdict', '2025-03-20', 'J-1', { disposition: 'repair' }),
    ]);
    const audit = runAudit({ job: 'C-4471', register, journal: reopened });
    const found = audit.findings.find((f) => f.check === 'joints reopened after acceptance');
    expect(found.items[0]).toMatch(/J-1/);
  });

  it('catches a joint with no mill report behind it', () => {
    const heats = [
      makeHeat({ number: 'H-1', materialId: 'CARBON_STEEL', receivedOn: '2025-02-01' }),
    ];
    const traced = {
      job: register.job,
      joints: register.joints.map((joint) => assignHeats(joint, { upstream: 'H-1' })),
    };
    const audit = runAudit({ job: 'C-4471', register: traced, journal: clean, heats });
    expect(audit.findings.some((f) => f.check === 'material traceability')).toBe(true);
  });

  it('catches coverage short of what was called for', () => {
    const barely = appendAll(makeJournal('C-4471'), [
      entry('shot', '2025-03-10', 'J-1'),
      entry('verdict', '2025-03-11', 'J-1', { disposition: 'accept' }),
    ]);
    const audit = runAudit({ job: 'C-4471', register, journal: barely, level: 'full' });
    expect(audit.findings.some((f) => f.check === 'coverage')).toBe(true);
  });

  it('catches a nonconformance open past its due date', () => {
    const ncr = raiseNcr({
      number: 1,
      job: 'C-4471',
      kind: 'procedure',
      detail: 'wrong code applied',
      raisedOn: '2025-03-01',
      raisedBy: 'K. Osei',
    });
    const audit = runAudit({
      job: 'C-4471',
      register,
      journal: clean,
      nonconformances: [ncr],
      day: '2025-05-01',
    });
    expect(audit.findings.some((f) => f.check === 'nonconformances past due')).toBe(true);
  });

  it('says nothing about nonconformances when no date was given', () => {
    const ncr = raiseNcr({
      number: 1,
      kind: 'procedure',
      detail: 'x',
      raisedOn: '2025-03-01',
      raisedBy: 'K. Osei',
    });
    const audit = runAudit({ job: 'C-4471', register, journal: clean, nonconformances: [ncr] });
    expect(audit.findings.some((f) => f.check === 'nonconformances past due')).toBe(false);
  });
});

describe('grading', () => {
  const tampered = {
    name: clean.name,
    entries: clean.entries.map((e) => (e.seq === 2 ? { ...e, subject: 'J-9' } : e)),
  };
  const audit = runAudit({ job: 'C-4471', register, journal: tampered, level: 'full' });

  it('puts the severe finding first, whatever its item count', () => {
    expect(audit.findings[0].severity).toBe('severe');
  });

  it('does not grade by how much work each one is', () => {
    const severe = audit.findings.find((f) => f.severity === 'severe');
    const minor = audit.findings.find((f) => f.severity === 'minor');
    if (minor) {
      expect(audit.findings.indexOf(severe)).toBeLessThan(audit.findings.indexOf(minor));
    }
    expect(severe).toBeDefined();
  });

  it('filters to what is at least a given severity', () => {
    expect(atLeast(audit, 'severe').every((f) => f.severity === 'severe')).toBe(true);
    expect(atLeast(audit, 'minor').length).toBe(audit.findings.length);
  });
});

describe('renderAudit', () => {
  it('says nothing found on a clean job', () => {
    const audit = runAudit({ job: 'C-4471', register, journal: clean });
    const text = renderAudit(audit);
    expect(text).toContain('AUDIT READINESS');
    expect(text).toContain('Nothing found.');
  });

  it('prints a summary table and then each finding', () => {
    const stray = appendAll(clean, [entry('shot', '2025-03-12', 'J-99')]);
    const text = renderAudit(runAudit({ job: 'C-4471', register, journal: stray }));
    expect(text).toContain('severity');
    expect(text).toContain('JOINTS NOT ON THE REGISTER');
    expect(text).toContain('J-99');
  });

  it('keeps every line inside the paper width', () => {
    const stray = appendAll(clean, [entry('shot', '2025-03-12', 'J-99')]);
    for (const line of renderAudit(runAudit({ job: 'C-4471', register, journal: stray })).split(
      '\n'
    )) {
      expect(line.length).toBeLessThanOrEqual(78);
    }
  });
});
