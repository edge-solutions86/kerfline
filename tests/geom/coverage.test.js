import { describe, it, expect } from 'vitest';
import {
  incidenceAngleDegrees,
  thicknessRatio,
  tangentOffsetDegrees,
  maxHalfAngleDegrees,
  exposuresForCircumference,
  shotPlan,
  requiredDistanceForShots,
} from '../../src/geom/coverage.js';

const joint = { odIn: 12.75, sourceToObjectIn: 24 };

describe('incidenceAngleDegrees', () => {
  it('is square on under the source', () => {
    expect(incidenceAngleDegrees({ ...joint, offsetDegrees: 0 })).toBeCloseTo(0, 9);
  });

  it('grows as the offset grows', () => {
    const near = incidenceAngleDegrees({ ...joint, offsetDegrees: 10 });
    const far = incidenceAngleDegrees({ ...joint, offsetDegrees: 25 });
    expect(far).toBeGreaterThan(near);
    expect(near).toBeGreaterThan(0);
  });

  it('does not care which way round the offset runs', () => {
    const left = incidenceAngleDegrees({ ...joint, offsetDegrees: -20 });
    const right = incidenceAngleDegrees({ ...joint, offsetDegrees: 20 });
    expect(left).toBeCloseTo(right, 12);
  });

  it('falls as the source is cranked further out', () => {
    const near = incidenceAngleDegrees({
      odIn: 12.75,
      sourceToObjectIn: 12,
      offsetDegrees: 20,
    });
    const far = incidenceAngleDegrees({ odIn: 12.75, sourceToObjectIn: 96, offsetDegrees: 20 });
    expect(far).toBeLessThan(near);
  });

  it('refuses an offset past the tangent point', () => {
    const tangent = tangentOffsetDegrees(joint.odIn, joint.sourceToObjectIn);
    expect(() => incidenceAngleDegrees({ ...joint, offsetDegrees: tangent + 1 })).toThrow(
      /past the tangent point/
    );
  });
});

describe('tangentOffsetDegrees', () => {
  it('opens up as the source is cranked out', () => {
    const near = tangentOffsetDegrees(12.75, 6);
    const far = tangentOffsetDegrees(12.75, 96);
    expect(far).toBeGreaterThan(near);
    expect(far).toBeLessThan(90);
  });

  it('closes towards nothing as the source comes onto the pipe', () => {
    expect(tangentOffsetDegrees(12.75, 0.001)).toBeLessThan(2);
  });
});

describe('thicknessRatio', () => {
  it('is one under the source', () => {
    expect(thicknessRatio({ ...joint, offsetDegrees: 0 })).toBeCloseTo(1, 9);
  });

  it('rises with the offset', () => {
    expect(thicknessRatio({ ...joint, offsetDegrees: 20 })).toBeGreaterThan(1);
    expect(thicknessRatio({ ...joint, offsetDegrees: 25 })).toBeGreaterThan(
      thicknessRatio({ ...joint, offsetDegrees: 20 })
    );
  });
});

describe('maxHalfAngleDegrees', () => {
  it('lands where the ratio meets the cap', () => {
    const half = maxHalfAngleDegrees({ ...joint, maxThicknessRatio: 1.2 });
    const at = thicknessRatio({ ...joint, offsetDegrees: half });
    expect(at).toBeLessThanOrEqual(1.2 + 1e-6);
    expect(at).toBeGreaterThan(1.19);
  });

  it('opens up when the cap is loosened', () => {
    const tight = maxHalfAngleDegrees({ ...joint, maxThicknessRatio: 1.1 });
    const loose = maxHalfAngleDegrees({ ...joint, maxThicknessRatio: 1.3 });
    expect(loose).toBeGreaterThan(tight);
  });

  it('never runs past the tangent point', () => {
    const tangent = tangentOffsetDegrees(joint.odIn, joint.sourceToObjectIn);
    const half = maxHalfAngleDegrees({ ...joint, maxThicknessRatio: 100 });
    expect(half).toBeLessThanOrEqual(tangent);
  });

  it('refuses a cap at or below one', () => {
    expect(() => maxHalfAngleDegrees({ ...joint, maxThicknessRatio: 1 })).toThrow(
      /greater than 1/
    );
  });
});

describe('exposuresForCircumference', () => {
  it('needs more shots on a tighter cap', () => {
    const tight = exposuresForCircumference({ ...joint, maxThicknessRatio: 1.1 });
    const loose = exposuresForCircumference({ ...joint, maxThicknessRatio: 1.3 });
    expect(tight).toBeGreaterThanOrEqual(loose);
  });

  it('needs fewer shots with the source cranked further out', () => {
    const near = exposuresForCircumference({
      odIn: 12.75,
      sourceToObjectIn: 12,
      maxThicknessRatio: 1.2,
    });
    const far = exposuresForCircumference({
      odIn: 12.75,
      sourceToObjectIn: 96,
      maxThicknessRatio: 1.2,
    });
    expect(far).toBeLessThanOrEqual(near);
  });

  it('needs more shots on a bigger pipe at the same standoff', () => {
    const small = exposuresForCircumference({
      odIn: 6.625,
      sourceToObjectIn: 24,
      maxThicknessRatio: 1.2,
    });
    const large = exposuresForCircumference({
      odIn: 24,
      sourceToObjectIn: 24,
      maxThicknessRatio: 1.2,
    });
    expect(large).toBeGreaterThan(small);
  });

  it('is always at least one', () => {
    const count = exposuresForCircumference({
      odIn: 2.375,
      sourceToObjectIn: 240,
      maxThicknessRatio: 1.5,
    });
    expect(count).toBeGreaterThanOrEqual(1);
  });
});

describe('shotPlan', () => {
  const plan = shotPlan({ ...joint, maxThicknessRatio: 1.2 });

  it('makes one entry per shot', () => {
    expect(plan).toHaveLength(exposuresForCircumference({ ...joint, maxThicknessRatio: 1.2 }));
  });

  it('numbers the shots from one', () => {
    expect(plan.map((s) => s.shot)).toEqual(plan.map((_s, i) => i + 1));
  });

  it('spreads the centres evenly round the joint', () => {
    const step = 360 / plan.length;
    plan.forEach((shot, index) => {
      expect(shot.centreDegrees).toBeCloseTo((index * step) % 360, 3);
    });
  });

  it('starts at the top of the pipe by default', () => {
    expect(plan[0].clock).toBe('12:00');
    expect(plan[0].centreDegrees).toBe(0);
  });

  it('takes a start position and still wraps inside a turn', () => {
    const offset = shotPlan({ ...joint, maxThicknessRatio: 1.2, startDegrees: 90 });
    expect(offset[0].clock).toBe('3:00');
    offset.forEach((shot) => {
      expect(shot.centreDegrees).toBeGreaterThanOrEqual(0);
      expect(shot.centreDegrees).toBeLessThan(360);
    });
  });

  it('reports the overlap that covers the seams between films', () => {
    plan.forEach((shot) => {
      expect(shot.overlapDegrees).toBeGreaterThanOrEqual(0);
      expect(shot.coversDegrees).toBeGreaterThan(0);
    });
  });
});

describe('requiredDistanceForShots', () => {
  it('finds a distance that delivers the asked for shot count', () => {
    const sod = requiredDistanceForShots({ odIn: 12.75, shots: 6, maxThicknessRatio: 1.2 });
    const got = exposuresForCircumference({
      odIn: 12.75,
      sourceToObjectIn: sod,
      maxThicknessRatio: 1.2,
    });
    expect(got).toBeLessThanOrEqual(6);
  });

  it('wants a longer crank for fewer shots', () => {
    const six = requiredDistanceForShots({ odIn: 12.75, shots: 6, maxThicknessRatio: 1.2 });
    const eight = requiredDistanceForShots({ odIn: 12.75, shots: 8, maxThicknessRatio: 1.2 });
    expect(six).toBeGreaterThan(eight);
  });

  it('cannot beat the parallel beam limit however far the source is cranked', () => {
    // As the source goes to infinity the beam arrives parallel, so the
    // incidence angle equals the angular position and the half angle can
    // never exceed acos(1 / ratio). A 1.2 cap therefore never covers more
    // than about 67 degrees a shot, whatever the distance.
    const ceiling = (Math.acos(1 / 1.2) * 180) / Math.PI;
    const half = maxHalfAngleDegrees({
      odIn: 12.75,
      sourceToObjectIn: 100000,
      maxThicknessRatio: 1.2,
    });
    expect(half).toBeLessThanOrEqual(ceiling + 1e-3);
    expect(half).toBeGreaterThan(ceiling - 0.5);
    expect(() =>
      requiredDistanceForShots({ odIn: 12.75, shots: 4, maxThicknessRatio: 1.2 })
    ).toThrow(/cannot cover the joint at any distance/);
  });

  it('refuses a shot count no distance can reach', () => {
    expect(() =>
      requiredDistanceForShots({ odIn: 12.75, shots: 1, maxThicknessRatio: 1.05 })
    ).toThrow(/cannot cover the joint at any distance/);
  });

  it('refuses a shot count that is not a whole number', () => {
    expect(() =>
      requiredDistanceForShots({ odIn: 12.75, shots: 0, maxThicknessRatio: 1.2 })
    ).toThrow(/whole number of shots/);
  });
});
