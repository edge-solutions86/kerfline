import { describe, it, expect } from 'vitest';
import {
  levels,
  levelFraction,
  levelLabel,
  levelFor,
  jointsToShoot,
  coverageAchieved,
  coverageByWelder,
  weldersNotCovered,
} from '../../src/sampling/coverage.js';

describe('the levels', () => {
  it('runs from full down to none', () => {
    expect(levels()).toEqual(['full', 'ten', 'five', 'spot', 'none']);
  });

  it('carries a fraction and a label for each', () => {
    expect(levelFraction('full')).toBe(1);
    expect(levelFraction('ten')).toBeCloseTo(0.1, 4);
    expect(levelFraction('none')).toBe(0);
    expect(levelLabel('five')).toBe('five percent');
  });

  it('refuses a level nobody specified', () => {
    expect(() => levelFraction('most')).toThrow(/no radiography level called most/);
    expect(() => levelLabel('most')).toThrow(/no radiography level/);
  });
});

describe('levelFor', () => {
  it('sends severe cyclic service to full radiography', () => {
    expect(levelFor({ service: 'severe_cyclic' })).toBe('full');
  });

  it('leaves category D with none', () => {
    expect(levelFor({ service: 'category_d' })).toBe('none');
  });

  it('puts normal service at five percent', () => {
    expect(levelFor({ service: 'normal' })).toBe('five');
    expect(levelFor()).toBe('five');
  });

  it('lets a client override the lot', () => {
    expect(levelFor({ service: 'category_d', clientCalled: 'full' })).toBe('full');
  });

  it('refuses a client level that does not exist', () => {
    expect(() => levelFor({ clientCalled: 'most' })).toThrow(/no radiography level/);
  });
});

describe('jointsToShoot', () => {
  it('rounds up rather than down', () => {
    expect(jointsToShoot(7, 'ten')).toBe(1);
    expect(jointsToShoot(11, 'ten')).toBe(2);
    expect(jointsToShoot(100, 'ten')).toBe(10);
  });

  it('shoots everything at full radiography', () => {
    expect(jointsToShoot(37, 'full')).toBe(37);
  });

  it('shoots nothing when nothing is called for', () => {
    expect(jointsToShoot(37, 'none')).toBe(0);
  });

  it('never asks for more joints than the job has', () => {
    expect(jointsToShoot(1, 'full')).toBe(1);
    expect(jointsToShoot(0, 'ten')).toBe(0);
  });

  it('refuses a count that is not a whole number', () => {
    expect(() => jointsToShoot(1.5, 'ten')).toThrow(/whole number/);
    expect(() => jointsToShoot(-1, 'ten')).toThrow(/not be below 0/);
  });
});

describe('coverageAchieved', () => {
  it('clears the floor when enough joints were shot', () => {
    const coverage = coverageAchieved({ totalJoints: 100, shotJoints: 10, level: 'ten' });
    expect(coverage.compliant).toBe(true);
    expect(coverage.shortBy).toBe(0);
  });

  it('is short when one joint is missing', () => {
    const coverage = coverageAchieved({ totalJoints: 100, shotJoints: 9, level: 'ten' });
    expect(coverage.compliant).toBe(false);
    expect(coverage.shortBy).toBe(1);
  });

  it('reports the achieved fraction next to the required one', () => {
    const coverage = coverageAchieved({ totalJoints: 7, shotJoints: 1, level: 'ten' });
    expect(coverage.requiredFraction).toBeCloseTo(0.1, 4);
    expect(coverage.achievedFraction).toBeCloseTo(0.1429, 3);
    expect(coverage.compliant).toBe(true);
  });

  it('is content with shooting more than called for', () => {
    const coverage = coverageAchieved({ totalJoints: 100, shotJoints: 20, level: 'ten' });
    expect(coverage.compliant).toBe(true);
  });

  it('refuses more joints shot than exist', () => {
    expect(() => coverageAchieved({ totalJoints: 10, shotJoints: 11, level: 'ten' })).toThrow(
      /more joints were shot/
    );
  });
});

describe('coverageByWelder', () => {
  const joints = [
    { welder: 'A17', shot: true },
    { welder: 'A17', shot: false },
    { welder: 'B04', shot: false },
    { welder: 'B04', shot: false },
  ];

  it('splits the coverage by stencil', () => {
    const rows = coverageByWelder(joints, 'full');
    expect(rows).toHaveLength(2);
    expect(rows[0].welder).toBe('A17');
  });

  it('shows a welder short even when another is covered', () => {
    const rows = coverageByWelder(joints, 'five');
    const b04 = rows.find((row) => row.welder === 'B04');
    expect(b04.compliant).toBe(false);
  });

  it('gathers joints with no welder under one heading', () => {
    const rows = coverageByWelder([{ shot: true }], 'five');
    expect(rows[0].welder).toBe('unknown');
  });
});

describe('weldersNotCovered', () => {
  it('names welders who have never been shot', () => {
    const joints = [
      { welder: 'A17', shot: true },
      { welder: 'B04', shot: false },
      { welder: 'C09', shot: false },
    ];
    expect(weldersNotCovered(joints)).toEqual(['B04', 'C09']);
  });

  it('is empty when everybody has been covered once', () => {
    const joints = [
      { welder: 'A17', shot: true },
      { welder: 'B04', shot: true },
    ];
    expect(weldersNotCovered(joints)).toEqual([]);
  });

  it('counts a welder as covered on any one shot joint', () => {
    const joints = [
      { welder: 'A17', shot: false },
      { welder: 'A17', shot: true },
    ];
    expect(weldersNotCovered(joints)).toEqual([]);
  });

  it('catches the case the overall percentage hides', () => {
    // Ninety joints by one welder all shot, ten by another none shot. The job
    // is at ninety percent coverage and one welder has never been checked.
    const joints = [
      ...Array.from({ length: 90 }, () => ({ welder: 'A17', shot: true })),
      ...Array.from({ length: 10 }, () => ({ welder: 'B04', shot: false })),
    ];
    const overall = coverageAchieved({ totalJoints: 100, shotJoints: 90, level: 'ten' });
    expect(overall.compliant).toBe(true);
    expect(weldersNotCovered(joints)).toEqual(['B04']);
  });
});
