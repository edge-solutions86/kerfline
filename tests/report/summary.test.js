import { describe, it, expect } from 'vitest';
import { buildSummary, outstandingJoints, renderSummary } from '../../src/report/summary.js';

const joint = (id, welder, shot, disposition = null) => ({ id, welder, shot, disposition });

const joints = [
  joint('J-1', 'A17', true, 'accept'),
  joint('J-2', 'A17', true, 'repair'),
  joint('J-3', 'A17', true, 'accept'),
  joint('J-10', 'B04', true, 'reject'),
  joint('J-11', 'B04', true, null),
  joint('J-12', 'C09', false),
  joint('J-13', 'C09', false),
];

describe('buildSummary', () => {
  const summary = buildSummary({ job: 'C-4471', joints, level: 'five' });

  it('counts the job', () => {
    expect(summary.counts.joints).toBe(7);
    expect(summary.counts.shot).toBe(5);
    expect(summary.counts.judged).toBe(4);
  });

  it('splits the dispositions', () => {
    expect(summary.counts.accepted).toBe(2);
    expect(summary.counts.repaired).toBe(1);
    expect(summary.counts.rejected).toBe(1);
  });

  it('counts what has been shot but not read', () => {
    expect(summary.outstanding).toBe(1);
  });

  it('reports the rate with the denominator it came from', () => {
    expect(summary.rate.shot).toBe(4);
    expect(summary.rate.repaired).toBe(2);
    expect(summary.rate.rate).toBeCloseTo(0.5, 4);
  });

  it('checks the coverage against what was called for', () => {
    expect(summary.coverage.requiredJoints).toBe(1);
    expect(summary.coverage.compliant).toBe(true);
  });

  it('names welders with nothing shot at all', () => {
    expect(summary.uncovered).toEqual(['C09']);
  });

  it('splits the rate by welder, worst first', () => {
    expect(summary.welders[0].welder).toBe('B04');
    expect(summary.welders[0].rate).toBeCloseTo(1, 4);
  });

  it('refuses a summary with no job on it', () => {
    expect(() => buildSummary({ job: '  ', joints })).toThrow(/names the job/);
  });

  it('refuses joints that are not a list', () => {
    expect(() => buildSummary({ job: 'C-4471', joints: null })).toThrow(/must be an array/);
  });

  it('handles a job that has not started', () => {
    const empty = buildSummary({ job: 'C-9999', joints: [] });
    expect(empty.counts.joints).toBe(0);
    expect(empty.rate.rate).toBe(0);
    expect(empty.welders).toEqual([]);
  });
});

describe('outstandingJoints', () => {
  it('lists everything not yet accepted', () => {
    const rows = outstandingJoints(joints);
    expect(rows.map((row) => row.id)).toEqual(['J-2', 'J-10', 'J-11', 'J-12', 'J-13']);
  });

  it('says what each one is waiting on', () => {
    const rows = outstandingJoints(joints);
    const byId = Object.fromEntries(rows.map((row) => [row.id, row.state]));
    expect(byId['J-12']).toBe('not shot');
    expect(byId['J-11']).toBe('awaiting interpretation');
    expect(byId['J-2']).toBe('repair');
    expect(byId['J-10']).toBe('reject');
  });

  it('sorts in register order rather than as strings', () => {
    const rows = outstandingJoints(joints);
    expect(rows.map((row) => row.id)).toEqual(['J-2', 'J-10', 'J-11', 'J-12', 'J-13']);
  });

  it('leaves accepted joints out', () => {
    expect(outstandingJoints(joints).map((row) => row.id)).not.toContain('J-1');
  });

  it('is empty on a finished job', () => {
    const done = [joint('J-1', 'A17', true, 'accept')];
    expect(outstandingJoints(done)).toEqual([]);
  });
});

describe('renderSummary', () => {
  const text = renderSummary(buildSummary({ job: 'C-4471', joints, level: 'five' }));

  it('prints the heading and the job', () => {
    expect(text).toContain('RADIOGRAPHY SUMMARY');
    expect(text).toContain('C-4471');
  });

  it('prints the progress with both numbers', () => {
    expect(text).toContain('PROGRESS');
    expect(text).toContain('5 of 7');
  });

  it('prints the rate next to the count it came from', () => {
    expect(text).toContain('Repair rate');
    expect(text).toContain('of 4 judged');
  });

  it('prints the welder table', () => {
    expect(text).toContain('BY WELDER');
    expect(text).toContain('stencil');
    expect(text).toContain('A17');
  });

  it('calls out welders who have never been shot', () => {
    expect(text).toContain('NOT COVERED');
    expect(text).toContain('C09');
  });

  it('leaves the not covered block out when everybody is covered', () => {
    const covered = [joint('J-1', 'A17', true, 'accept')];
    const other = renderSummary(buildSummary({ job: 'C-1', joints: covered }));
    expect(other).not.toContain('NOT COVERED');
  });

  it('says so plainly when nothing has been judged', () => {
    const fresh = renderSummary(
      buildSummary({ job: 'C-1', joints: [joint('J-1', 'A17', false)] })
    );
    expect(fresh).toContain('Nothing judged yet.');
  });

  it('keeps every line inside the paper width', () => {
    for (const line of text.split('\n')) {
      expect(line.length).toBeLessThanOrEqual(78);
    }
  });
});
