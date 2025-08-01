import { describe, it, expect } from 'vitest';
import {
  ncrKinds,
  severities,
  workingDaysFor,
  raiseNcr,
  closeNcr,
  isOpen,
  openNcrs,
  overdueNcrs,
  aging,
  fromDeviation,
  renderNcrs,
} from '../../src/report/nonconformance.js';

const base = {
  number: 1,
  job: 'C-4471',
  kind: 'procedure',
  detail: 'joint shot on the wrong code',
  raisedOn: '2025-03-14',
  raisedBy: 'K. Osei',
};

const ncr = raiseNcr(base);

describe('the vocabularies', () => {
  it('names the kinds and severities', () => {
    expect(ncrKinds()).toContain('procedure');
    expect(ncrKinds()).toContain('calibration');
    expect(severities()).toEqual(['observation', 'minor', 'major']);
  });

  it('gives a major report the shortest fuse', () => {
    expect(workingDaysFor('major')).toBeLessThan(workingDaysFor('minor'));
    expect(workingDaysFor('minor')).toBeLessThan(workingDaysFor('observation'));
  });

  it('refuses a severity it does not know', () => {
    expect(() => workingDaysFor('critical')).toThrow(/severity must be one of/);
  });
});

describe('raiseNcr', () => {
  it('numbers the report', () => {
    expect(ncr.id).toBe('NCR-001');
  });

  it('works the due date out in working days', () => {
    // 2025-03-14 is a Friday, so ten working days lands on the 28th.
    expect(ncr.dueBy).toBe('2025-03-28');
  });

  it('starts open', () => {
    expect(isOpen(ncr)).toBe(true);
    expect(ncr.closedOn).toBe(null);
  });

  it('refuses a report with no detail or nobody raising it', () => {
    expect(() => raiseNcr({ ...base, detail: '  ' })).toThrow(/says what was wrong/);
    expect(() => raiseNcr({ ...base, raisedBy: '' })).toThrow(/names who raised it/);
  });

  it('refuses a kind nobody uses', () => {
    expect(() => raiseNcr({ ...base, kind: 'attitude' })).toThrow(/nonconformance kind/);
  });

  it('refuses a number that is not a whole number from one', () => {
    expect(() => raiseNcr({ ...base, number: 0 })).toThrow(/numbered from one/);
    expect(() => raiseNcr({ ...base, number: 1.5 })).toThrow(/numbered from one/);
  });

  it('gives a major report a nearer due date than a minor one', () => {
    const major = raiseNcr({ ...base, severity: 'major' });
    expect(major.dueBy < ncr.dueBy).toBe(true);
  });
});

describe('closeNcr', () => {
  it('closes with an action and a date', () => {
    const closed = closeNcr(ncr, {
      day: '2025-03-20',
      by: 'K. Osei',
      action: 'rejudged under API 1104 and reissued',
    });
    expect(isOpen(closed)).toBe(false);
    expect(closed.closedOn).toBe('2025-03-20');
    expect(closed.action).toMatch(/rejudged/);
  });

  it('leaves the report it was given alone', () => {
    closeNcr(ncr, { day: '2025-03-20', by: 'x', action: 'done' });
    expect(ncr.closedOn).toBe(null);
  });

  it('refuses to close one twice', () => {
    const closed = closeNcr(ncr, { day: '2025-03-20', by: 'x', action: 'done' });
    expect(() => closeNcr(closed, { day: '2025-03-21', by: 'x', action: 'again' })).toThrow(
      /was closed on 2025-03-20/
    );
  });

  it('refuses to close before it was raised', () => {
    expect(() => closeNcr(ncr, { day: '2025-03-01', by: 'x', action: 'done' })).toThrow(
      /before it was raised/
    );
  });

  it('refuses to close without saying what was done', () => {
    expect(() => closeNcr(ncr, { day: '2025-03-20', by: 'x', action: ' ' })).toThrow(
      /says what was done/
    );
  });
});

describe('openNcrs and overdueNcrs', () => {
  const major = raiseNcr({ ...base, number: 2, severity: 'major' });
  const closed = closeNcr(raiseNcr({ ...base, number: 3 }), {
    day: '2025-03-18',
    by: 'x',
    action: 'done',
  });
  const reports = [ncr, major, closed];

  it('lists only the open ones', () => {
    expect(openNcrs(reports).map((r) => r.id)).toEqual(['NCR-001', 'NCR-002']);
  });

  it('finds nothing overdue before anything is due', () => {
    expect(overdueNcrs(reports, '2025-03-15')).toEqual([]);
  });

  it('finds the major one first once both are late', () => {
    const late = overdueNcrs(reports, '2025-04-30');
    expect(late[0].severity).toBe('major');
    expect(late).toHaveLength(2);
  });

  it('counts how many days over', () => {
    const late = overdueNcrs(reports, '2025-04-30');
    expect(late[0].daysOverdue).toBeGreaterThan(0);
  });

  it('never counts a closed report as overdue', () => {
    const late = overdueNcrs(reports, '2026-01-01');
    expect(late.map((r) => r.id)).not.toContain('NCR-003');
  });
});

describe('aging', () => {
  it('buckets the open reports by age', () => {
    const counts = aging([ncr], '2025-03-16');
    expect(counts['under 7']).toBe(1);
    expect(counts['under 30']).toBe(0);
  });

  it('moves a report along as it ages', () => {
    expect(aging([ncr], '2025-04-01')['under 30']).toBe(1);
    expect(aging([ncr], '2025-09-01')['over 90']).toBe(1);
  });

  it('counts nothing when everything is closed', () => {
    const closed = closeNcr(ncr, { day: '2025-03-20', by: 'x', action: 'done' });
    const counts = aging([closed], '2025-09-01');
    expect(Object.values(counts).every((count) => count === 0)).toBe(true);
  });

  it('takes different buckets', () => {
    const counts = aging([ncr], '2025-03-16', [3, 14]);
    expect(Object.keys(counts)).toEqual(['under 3', 'under 14', 'over 14']);
  });
});

describe('fromDeviation', () => {
  it('turns a contract deviation into a report without retyping it', () => {
    const raised = fromDeviation({
      deviation: { field: 'code', agreed: 'API1104', actual: 'AWSD1.1' },
      job: 'C-4471',
      number: 4,
      raisedOn: '2025-03-14',
      raisedBy: 'K. Osei',
    });
    expect(raised.detail).toBe('code was AWSD1.1 where API1104 was agreed');
    expect(raised.kind).toBe('procedure');
  });

  it('treats a wrong code as major and everything else as minor', () => {
    const code = fromDeviation({
      deviation: { field: 'code', agreed: 'a', actual: 'b' },
      number: 1,
      raisedOn: '2025-03-14',
      raisedBy: 'x',
    });
    const density = fromDeviation({
      deviation: { field: 'density', agreed: 2, actual: 1.8 },
      number: 2,
      raisedOn: '2025-03-14',
      raisedBy: 'x',
    });
    expect(code.severity).toBe('major');
    expect(density.severity).toBe('minor');
  });
});

describe('renderNcrs', () => {
  it('prints the open reports', () => {
    const text = renderNcrs([ncr], { day: '2025-03-16', job: 'C-4471' });
    expect(text).toContain('OPEN NONCONFORMANCES');
    expect(text).toContain('NCR-001');
    expect(text).toContain('procedure');
  });

  it('says so plainly when nothing is open', () => {
    const closed = closeNcr(ncr, { day: '2025-03-20', by: 'x', action: 'done' });
    expect(renderNcrs([closed], { day: '2025-03-21' })).toContain('Nothing open.');
  });

  it('keeps every line inside the paper width', () => {
    for (const line of renderNcrs([ncr], { day: '2025-03-16' }).split('\n')) {
      expect(line.length).toBeLessThanOrEqual(78);
    }
  });
});
