import { describe, it, expect } from 'vitest';
import {
  buildCoverage,
  shortfalls,
  renderCoverage,
  requiredFraction,
} from '../../src/report/coverage.js';

const joint = (id, welder, spool, shot) => ({ id, welder, spool, shot });

// Ninety joints by one welder all shot, ten by another none shot. Ninety
// percent overall, and one welder never checked.
const joints = [
  ...Array.from({ length: 90 }, (_u, i) => joint(`A-${i}`, 'A17', 'SP-1', true)),
  ...Array.from({ length: 10 }, (_u, i) => joint(`B-${i}`, 'B04', 'SP-2', false)),
];

describe('buildCoverage', () => {
  const report = buildCoverage({ job: 'C-4471', joints, level: 'ten' });

  it('clears the contract on the overall figure', () => {
    expect(report.overall.compliant).toBe(true);
    expect(report.overall.achievedFraction).toBeCloseTo(0.9, 3);
  });

  it('breaks it apart by welder and finds the gap', () => {
    const b04 = report.byWelder.find((row) => row.key === 'B04');
    expect(b04.compliant).toBe(false);
    expect(b04.shot).toBe(0);
  });

  it('names the welder with nothing shot at all', () => {
    expect(report.uncoveredWelders).toEqual(['B04']);
  });

  it('names the spool with nothing shot at all', () => {
    expect(report.uncoveredSpools).toEqual(['SP-2']);
  });

  it('puts the worst covered group first', () => {
    expect(report.byWelder[0].key).toBe('B04');
  });

  it('gathers joints with no welder or spool under one heading', () => {
    const anon = buildCoverage({ job: 'C-1', joints: [joint('J-1', null, null, true)] });
    expect(anon.byWelder[0].key).toBe('unknown');
    expect(anon.bySpool[0].key).toBe('unassigned');
  });

  it('handles a job that has not started', () => {
    const fresh = buildCoverage({ job: 'C-1', joints: [] });
    expect(fresh.overall.totalJoints).toBe(0);
    expect(fresh.byWelder).toEqual([]);
  });

  it('refuses a report with no job named', () => {
    expect(() => buildCoverage({ job: ' ', joints })).toThrow(/names the job/);
    expect(() => buildCoverage({ job: 'C-1', joints: null })).toThrow(/must be an array/);
  });
});

describe('shortfalls', () => {
  it('finds the group short even when the job as a whole is not', () => {
    const report = buildCoverage({ job: 'C-4471', joints, level: 'ten' });
    const found = shortfalls(report);
    expect(found.some((entry) => entry.what === 'the job')).toBe(false);
    expect(found.some((entry) => entry.what === 'welder B04')).toBe(true);
  });

  it('counts how many joints short each one is', () => {
    const report = buildCoverage({ job: 'C-4471', joints, level: 'ten' });
    const b04 = shortfalls(report).find((entry) => entry.what === 'welder B04');
    expect(b04.shortBy).toBe(1);
  });

  it('finds nothing on a fully covered job', () => {
    const covered = joints.map((entry) => ({ ...entry, shot: true }));
    const report = buildCoverage({ job: 'C-1', joints: covered, level: 'ten' });
    expect(shortfalls(report)).toEqual([]);
  });

  it('finds the job itself when the overall figure is short', () => {
    const barely = joints.map((entry, i) => ({ ...entry, shot: i < 3 }));
    const report = buildCoverage({ job: 'C-1', joints: barely, level: 'ten' });
    expect(shortfalls(report).some((entry) => entry.what === 'the job')).toBe(true);
  });
});

describe('renderCoverage', () => {
  const text = renderCoverage(buildCoverage({ job: 'C-4471', joints, level: 'ten' }));

  it('leads with the overall figure', () => {
    expect(text).toContain('RADIOGRAPHY COVERAGE');
    expect(text).toContain('OVERALL');
    expect(text).toContain('90 percent');
  });

  it('breaks it apart underneath', () => {
    expect(text).toContain('BY WELDER');
    expect(text).toContain('BY SPOOL');
    expect(text).toContain('A17');
  });

  it('calls out what was never covered', () => {
    expect(text).toContain('NOT COVERED AT ALL');
    expect(text).toContain('B04');
    expect(text).toContain('SP-2');
  });

  it('leaves that block out when everything is covered', () => {
    const covered = joints.map((entry) => ({ ...entry, shot: true }));
    const other = renderCoverage(buildCoverage({ job: 'C-1', joints: covered, level: 'ten' }));
    expect(other).not.toContain('NOT COVERED AT ALL');
  });

  it('says so plainly when there is nothing to break down', () => {
    const fresh = renderCoverage(buildCoverage({ job: 'C-1', joints: [] }));
    expect(fresh).toContain('Nothing to break down.');
  });

  it('keeps every line inside the paper width', () => {
    for (const line of text.split('\n')) {
      expect(line.length).toBeLessThanOrEqual(78);
    }
  });
});

describe('requiredFraction', () => {
  it('reads the level fraction', () => {
    expect(requiredFraction('ten')).toBeCloseTo(0.1, 4);
    expect(requiredFraction('full')).toBe(1);
  });

  it('refuses a level nobody specified', () => {
    expect(() => requiredFraction('most')).toThrow(/no radiography level/);
  });
});
