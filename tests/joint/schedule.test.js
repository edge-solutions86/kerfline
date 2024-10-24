import { describe, it, expect } from 'vitest';
import {
  nominalSizes,
  normaliseSize,
  outsideDiameterIn,
  schedulesFor,
  normaliseSchedule,
  hasSchedule,
  wallThicknessIn,
  schedulesForWall,
  pipeSpec,
  stdMatchesSchedule40,
} from '../../src/joint/schedule.js';

describe('the size table', () => {
  it('carries the sizes the shop shoots', () => {
    const sizes = nominalSizes();
    expect(sizes).toContain('2');
    expect(sizes).toContain('12');
    expect(sizes).toContain('1-1/2');
    expect(sizes.length).toBeGreaterThan(12);
  });

  it('knows that nominal size is not the outside diameter', () => {
    expect(outsideDiameterIn(2)).toBeCloseTo(2.375, 4);
    expect(outsideDiameterIn(6)).toBeCloseTo(6.625, 4);
    expect(outsideDiameterIn(12)).toBeCloseTo(12.75, 4);
  });

  it('knows where nominal size starts telling the truth', () => {
    expect(outsideDiameterIn(14)).toBe(14);
    expect(outsideDiameterIn(24)).toBe(24);
    expect(outsideDiameterIn(12)).not.toBe(12);
  });

  it('reads a size however it was written', () => {
    expect(normaliseSize('2 in')).toBe('2');
    expect(normaliseSize('2"')).toBe('2');
    expect(normaliseSize(2)).toBe('2');
    expect(normaliseSize('1-1/2')).toBe('1-1/2');
  });

  it('refuses a size it does not stock', () => {
    expect(() => outsideDiameterIn(5)).toThrow(/no pipe size 5/);
    expect(() => outsideDiameterIn('DN50')).toThrow(/no pipe size/);
  });
});

describe('wallThicknessIn', () => {
  it('reads the published walls', () => {
    expect(wallThicknessIn(6, 40)).toBeCloseTo(0.28, 4);
    expect(wallThicknessIn(6, 80)).toBeCloseTo(0.432, 4);
    expect(wallThicknessIn(2, 40)).toBeCloseTo(0.154, 4);
    expect(wallThicknessIn(12, 40)).toBeCloseTo(0.406, 4);
  });

  it('reads a schedule however it was written', () => {
    expect(normaliseSchedule('SCH 40')).toBe('40');
    expect(normaliseSchedule('sch40')).toBe('40');
    expect(normaliseSchedule('xs')).toBe('XS');
    expect(wallThicknessIn(6, 'SCH 40')).toBeCloseTo(0.28, 4);
  });

  it('refuses a schedule that size is not made in', () => {
    expect(() => wallThicknessIn(2, 10)).toThrow(/not made in schedule 10/);
  });

  it('answers hasSchedule without throwing', () => {
    expect(hasSchedule(6, 40)).toBe(true);
    expect(hasSchedule(2, 10)).toBe(false);
    expect(hasSchedule(5, 40)).toBe(false);
  });

  it('lists the schedules a size is made in', () => {
    expect(schedulesFor(2)).toContain('40');
    expect(schedulesFor(2)).toContain('XXS');
  });
});

describe('STD, XS and XXS', () => {
  it('has STD equal to schedule 40 on the small sizes', () => {
    expect(stdMatchesSchedule40(2)).toBe(true);
    expect(stdMatchesSchedule40(6)).toBe(true);
    expect(stdMatchesSchedule40(10)).toBe(true);
  });

  it('has STD part company with schedule 40 from twelve inch up', () => {
    expect(stdMatchesSchedule40(12)).toBe(false);
    expect(stdMatchesSchedule40(14)).toBe(false);
    expect(stdMatchesSchedule40(24)).toBe(false);
  });

  it('holds STD at three eighths on the big sizes whatever 40 does', () => {
    expect(wallThicknessIn(12, 'STD')).toBeCloseTo(0.375, 4);
    expect(wallThicknessIn(24, 'STD')).toBeCloseTo(0.375, 4);
    expect(wallThicknessIn(24, 40)).toBeCloseTo(0.688, 4);
  });

  it('holds XS at half an inch on the big sizes', () => {
    expect(wallThicknessIn(12, 'XS')).toBeCloseTo(0.5, 4);
    expect(wallThicknessIn(24, 'XS')).toBeCloseTo(0.5, 4);
    expect(wallThicknessIn(6, 'XS')).toBeCloseTo(0.432, 4);
  });
});

describe('schedulesForWall', () => {
  it('finds the schedules a measured wall matches', () => {
    expect(schedulesForWall(6, 0.28)).toContain('40');
    expect(schedulesForWall(6, 0.28)).toContain('STD');
  });

  it('finds nothing when the wall matches no schedule', () => {
    expect(schedulesForWall(6, 0.35)).toEqual([]);
  });

  it('takes a tolerance', () => {
    expect(schedulesForWall(6, 0.284, 0.005)).toContain('40');
    expect(schedulesForWall(6, 0.284, 0.001)).toEqual([]);
  });

  it('refuses a wall that is not positive', () => {
    expect(() => schedulesForWall(6, 0)).toThrow(/positive number/);
  });
});

describe('pipeSpec', () => {
  it('gives the whole geometry for a joint', () => {
    expect(pipeSpec(6, 40)).toEqual({
      nominalSize: '6',
      schedule: '40',
      odIn: 6.625,
      wallIn: 0.28,
      idIn: 6.065,
    });
  });

  it('takes the schedule as written on the drawing', () => {
    expect(pipeSpec(12, 'STD').wallIn).toBeCloseTo(0.375, 4);
    expect(pipeSpec(12, 'sch 40').wallIn).toBeCloseTo(0.406, 4);
  });

  it('keeps the bore consistent with the wall', () => {
    for (const size of ['2', '6', '12', '24']) {
      const spec = pipeSpec(size, 'STD');
      expect(spec.idIn).toBeCloseTo(spec.odIn - 2 * spec.wallIn, 6);
    }
  });
});
