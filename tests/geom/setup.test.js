import { describe, it, expect } from 'vitest';
import {
  standoffIn,
  distanceForUnsharpness,
  shortestWorkingDistance,
  resolveSetup,
} from '../../src/geom/setup.js';

const joint = { odIn: 6.625, wallIn: 0.28, sourceSizeIn: 0.12 };

describe('standoffIn', () => {
  it('is the whole diameter on a double wall shot', () => {
    expect(standoffIn({ ...joint, technique: 'DWSI' })).toBeCloseTo(6.625, 3);
  });

  it('is the wall when the source is inside', () => {
    expect(standoffIn({ ...joint, technique: 'SWSI_PANORAMIC' })).toBeCloseTo(0.28, 3);
  });

  it('refuses a diameter or wall at zero', () => {
    expect(() => standoffIn({ odIn: 0, wallIn: 0.28, technique: 'DWSI' })).toThrow(
      /outside diameter/
    );
  });
});

describe('distanceForUnsharpness', () => {
  it('reads the limit off the penetrated thickness, not the wall', () => {
    const check = distanceForUnsharpness(joint);
    expect(check.technique).toBe('DWSI');
    expect(check.limitIn).toBe(0.02);
    expect(check.objectToFilmIn).toBeCloseTo(6.625, 3);
  });

  it('wants the source cranked well out on a double wall shot', () => {
    expect(distanceForUnsharpness(joint).minimumSourceToObjectIn).toBeGreaterThan(35);
  });

  it('wants far less once the source goes inside', () => {
    const inside = distanceForUnsharpness({ ...joint, sourceCanEnter: true });
    expect(inside.minimumSourceToObjectIn).toBeLessThan(
      distanceForUnsharpness(joint).minimumSourceToObjectIn
    );
  });

  it('wants less with a smaller source', () => {
    const small = distanceForUnsharpness({ ...joint, sourceSizeIn: 0.06 });
    expect(small.minimumSourceToObjectIn).toBeCloseTo(
      distanceForUnsharpness(joint).minimumSourceToObjectIn / 2,
      1
    );
  });
});

describe('shortestWorkingDistance', () => {
  it('takes whichever constraint reaches further out', () => {
    const answer = shortestWorkingDistance({ ...joint, maxShots: 6 });
    expect(answer.sourceToObjectIn).toBeGreaterThanOrEqual(answer.minimumSourceToObjectIn);
    expect(answer.sourceToObjectIn).toBeGreaterThanOrEqual(answer.requiredForShotsIn);
  });

  it('names which one governed', () => {
    const answer = shortestWorkingDistance({ ...joint, maxShots: 6 });
    expect(['unsharpness', 'coverage']).toContain(answer.governedBy);
  });

  it('is governed by coverage when few shots are allowed', () => {
    const few = shortestWorkingDistance({ ...joint, maxShots: 6 });
    const many = shortestWorkingDistance({ ...joint, maxShots: 20 });
    expect(few.sourceToObjectIn).toBeGreaterThanOrEqual(many.sourceToObjectIn);
  });

  it('says plainly when the shot count cannot be met at any distance', () => {
    const answer = shortestWorkingDistance({ ...joint, maxShots: 2 });
    expect(answer.shotsReachable).toBe(false);
    expect(answer.sourceToObjectIn).toBe(null);
    expect(answer.reason).toMatch(/cannot cover this joint at any distance/);
  });

  it('does not trade unsharpness away for a shot count', () => {
    const answer = shortestWorkingDistance({ ...joint, maxShots: 20 });
    expect(answer.sourceToObjectIn).toBeGreaterThanOrEqual(answer.minimumSourceToObjectIn);
  });

  it('refuses a shot count that is not a whole number above zero', () => {
    expect(() => shortestWorkingDistance({ ...joint, maxShots: 0 })).toThrow(/shot count/);
    expect(() => shortestWorkingDistance({ ...joint, maxShots: 1.5 })).toThrow(/whole number/);
  });
});

describe('resolveSetup', () => {
  it('fills the standoff in from the viewing technique', () => {
    const setup = resolveSetup({ ...joint, sourceToObjectIn: 48 });
    expect(setup.distances.objectToFilmIn).toBeCloseTo(6.625, 3);
    expect(setup.distances.sourceToFilmIn).toBeCloseTo(54.625, 3);
  });

  it('takes an explicit standoff over the derived one', () => {
    const setup = resolveSetup({ ...joint, sourceToObjectIn: 48, objectToFilmIn: 2 });
    expect(setup.distances.objectToFilmIn).toBe(2);
  });

  it('checks the unsharpness against what it resolved to', () => {
    const setup = resolveSetup({ ...joint, sourceToObjectIn: 48 });
    expect(setup.sharpness.pass).toBe(true);
    expect(setup.workable).toBe(true);
  });

  it('fails a setup cranked in too close', () => {
    const setup = resolveSetup({ ...joint, sourceToObjectIn: 12 });
    expect(setup.sharpness.pass).toBe(false);
    expect(setup.workable).toBe(false);
    expect(setup.sharpness.shortfallIn).toBeGreaterThan(0);
  });

  it('plans the shots at the distance it resolved to', () => {
    const setup = resolveSetup({ ...joint, sourceToObjectIn: 48 });
    expect(setup.plan).toHaveLength(setup.shots);
    expect(setup.plan[0].clock).toBe('12:00');
  });

  it('resolves from source to film instead of source to object', () => {
    const setup = resolveSetup({ ...joint, sourceToFilmIn: 54.625 });
    expect(setup.distances.sourceToObjectIn).toBeCloseTo(48, 2);
  });

  it('refuses when it has too few distances to work with', () => {
    expect(() => resolveSetup(joint)).toThrow(/two of the three/);
  });

  it('refuses something that is not a setup', () => {
    expect(() => resolveSetup(null)).toThrow(/resolved from an object/);
  });

  it('agrees with the shortest working distance it recommends', () => {
    const answer = shortestWorkingDistance({ ...joint, maxShots: 8 });
    const setup = resolveSetup({ ...joint, sourceToObjectIn: answer.sourceToObjectIn });
    expect(setup.workable).toBe(true);
    expect(setup.shots).toBeLessThanOrEqual(8);
  });
});
