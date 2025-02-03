import { describe, it, expect } from 'vitest';
import * as kerfline from '../src/index.js';

describe('the public surface', () => {
  it('exports the error namespace', () => {
    expect(typeof kerfline.errors.KerflineError).toBe('function');
    expect(kerfline.errors.errorCodes()).toContain('BAD_INPUT');
  });

  it('exports a function for every name it lists', () => {
    for (const [name, value] of Object.entries(kerfline)) {
      if (name === 'errors') continue;
      expect(typeof value, `${name} should be callable`).toBe('function');
    }
  });

  it('exports no name twice under a different binding', () => {
    const names = Object.keys(kerfline);
    expect(new Set(names).size).toBe(names.length);
  });

  it('reaches every stage of the job', () => {
    for (const name of [
      'planExposure',
      'selectForJoint',
      'makeIndication',
      'judge',
      'makeJoint',
      'doseRateAt',
      'makeCertification',
    ]) {
      expect(kerfline[name], `${name} should be exported`).toBeTypeOf('function');
    }
  });
});

describe('a joint goes through the surface end to end', () => {
  it('plans a shot, reads it, and judges the weld without reaching inside', () => {
    const joint = kerfline.makeJoint({ id: 'J-118', nominalSize: 6, schedule: 40 });
    expect(joint.wallIn).toBeCloseTo(0.28, 4);

    const viewing = kerfline.viewingPlan({ odIn: joint.odIn, wallIn: joint.wallIn });
    expect(viewing.technique).toBe('DWSI');

    const indicator = kerfline.selectForJoint({
      wallIn: viewing.penetratedThicknessIn,
      reinforcementIn: 0.0625,
    });
    expect(indicator.holeIdentity).toBeGreaterThan(0);

    const exposure = kerfline.planExposure({
      isotopeId: 'IR192',
      thicknessIn: viewing.penetratedThicknessIn,
      distanceIn: 24,
      assayCi: 40,
    });
    expect(exposure.minutes).toBeGreaterThan(0);

    const indications = [
      kerfline.makeIndication({ type: 'SLAG', measuredIn: 0.15, at: 3 }),
      kerfline.makeIndication({ type: 'POROSITY', measuredIn: 0.04, at: 8 }),
    ];
    const verdict = kerfline.judge({ code: 'API1104', indications, joint });
    expect(kerfline.isAcceptable(verdict)).toBe(true);
  });

  it('takes the same joint out when a crack is found', () => {
    const joint = kerfline.makeJoint({ id: 'J-119', nominalSize: 6, schedule: 40 });
    const indications = [kerfline.makeIndication({ type: 'CRACK', measuredIn: 0.2, at: 3 })];
    const verdict = kerfline.judge({ code: 'API1104', indications, joint });
    expect(kerfline.requiresRemoval(verdict)).toBe(true);
  });

  it('works the barricade out for the source that shot it', () => {
    const distance = kerfline.barricadeDistanceFt({ isotopeId: 'IR192', activityCi: 40 });
    expect(distance).toBeGreaterThan(200);
  });
});
