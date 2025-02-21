import { describe, it, expect } from 'vitest';
import {
  buildTechnique,
  renderTechnique,
  summariseTechnique,
} from '../../src/report/technique.js';
import { makeJoint } from '../../src/joint/spec.js';

const joint = makeJoint({ id: 'J-118', nominalSize: 6, schedule: 40 });

const base = {
  job: 'C-4471',
  joint,
  isotopeId: 'IR192',
  assayCi: 40,
  // A double wall shot stands the film off by the whole diameter, so the
  // source has to be cranked past 40 in to hold the unsharpness limit.
  sourceToObjectIn: 48,
  objectToFilmIn: 6.625,
};

describe('buildTechnique', () => {
  const sheet = buildTechnique(base);

  it('picks the viewing technique from the joint', () => {
    expect(sheet.viewing.technique).toBe('DWSI');
    expect(sheet.viewing.penetratedThicknessIn).toBeCloseTo(0.56, 3);
  });

  it('works the exposure out on the penetrated thickness', () => {
    expect(sheet.exposure).not.toBe(null);
    expect(sheet.exposure.isotope).toBe('IR192');
    expect(sheet.exposure.minutes).toBeGreaterThan(0);
  });

  it('chooses an indicator', () => {
    expect(sheet.indicator.holeIdentity).toBeGreaterThan(0);
    expect(sheet.indicator.essentialHole).toBe('2T');
  });

  it('checks the geometry', () => {
    expect(sheet.geometry.pass).toBe(true);
    expect(sheet.geometry.limitIn).toBe(0.02);
  });

  it('fails the geometry when the source is cranked in too close', () => {
    const close = buildTechnique({ ...base, sourceToObjectIn: 24 });
    expect(close.geometry.pass).toBe(false);
    expect(close.geometry.shortfallIn).toBeGreaterThan(0);
    expect(close.geometry.minimumSourceToObjectIn).toBeCloseTo(39.75, 1);
  });

  it('plans the shots round the joint', () => {
    expect(sheet.coverage.length).toBeGreaterThan(1);
    expect(sheet.coverage[0].clock).toBe('12:00');
  });

  it('works out the barricade', () => {
    expect(sheet.barricadeFt).toBeGreaterThan(200);
  });

  it('is usable when everything resolved', () => {
    expect(sheet.usable).toBe(true);
    expect(sheet.problems).toEqual([]);
  });

  it('refuses a sheet with no joint on it', () => {
    expect(() => buildTechnique({ ...base, joint: null })).toThrow(/needs a joint/);
    expect(() => buildTechnique('J-118')).toThrow(/built from an object/);
  });
});

describe('buildTechnique when something will not resolve', () => {
  it('records the problem rather than throwing', () => {
    const thick = makeJoint({ id: 'J-9', odIn: 24, wallIn: 3 });
    const sheet = buildTechnique({ ...base, joint: thick });
    expect(sheet.usable).toBe(false);
    expect(sheet.problems.length).toBeGreaterThan(0);
    expect(sheet.exposure).toBe(null);
  });

  it('still resolves the parts that do work', () => {
    const thick = makeJoint({ id: 'J-9', odIn: 24, wallIn: 3 });
    const sheet = buildTechnique({ ...base, joint: thick });
    expect(sheet.viewing.technique).toBe('DWSI');
    expect(sheet.coverage).not.toBe(null);
  });

  it('names which part failed', () => {
    const thick = makeJoint({ id: 'J-9', odIn: 24, wallIn: 3 });
    const sheet = buildTechnique({ ...base, joint: thick });
    expect(sheet.problems.join(' ')).toMatch(/exposure/);
  });
});

describe('renderTechnique', () => {
  const text = renderTechnique(buildTechnique(base));

  it('prints the heading and the joint', () => {
    expect(text).toContain('RADIOGRAPHIC TECHNIQUE');
    expect(text).toContain('J-118');
    expect(text).toContain('6.625 in OD x 0.280 wall');
  });

  it('prints the exposure block with a timer setting', () => {
    expect(text).toContain('SOURCE AND EXPOSURE');
    expect(text).toContain('Ci min');
    expect(text).toMatch(/set \d+ s/);
  });

  it('prints the indicator and the unsharpness together', () => {
    expect(text).toContain('IMAGE QUALITY');
    expect(text).toContain('hole type, 2T');
    expect(text).toContain('Unsharpness');
  });

  it('prints the shot table', () => {
    expect(text).toContain('SHOTS');
    expect(text).toContain('centre');
    expect(text).toContain('12:00');
  });

  it('prints the barricade', () => {
    expect(text).toContain('SAFETY');
    expect(text).toContain('Barricade');
  });

  it('does not mark a good sheet as not ready', () => {
    expect(text).not.toContain('NOT READY');
  });

  it('keeps every line inside the paper width', () => {
    for (const line of text.split('\n')) {
      expect(line.length).toBeLessThanOrEqual(78);
    }
  });
});

describe('renderTechnique when something will not resolve', () => {
  const thick = makeJoint({ id: 'J-9', odIn: 24, wallIn: 3 });
  const text = renderTechnique(buildTechnique({ ...base, joint: thick }));

  it('marks the sheet not ready', () => {
    expect(text).toContain('NOT READY');
  });

  it('says what was not established rather than leaving a blank', () => {
    expect(text).toContain('not established');
  });

  it('lists the notes at the foot', () => {
    expect(text).toContain('NOTES');
    expect(text).toMatch(/exposure:/);
  });
});

describe('summariseTechnique', () => {
  it('gives a folder index row', () => {
    const summary = summariseTechnique(buildTechnique(base));
    expect(summary.jointId).toBe('J-118');
    expect(summary.technique).toBe('DWSI');
    expect(summary.isotope).toBe('IR192');
    expect(summary.usable).toBe(true);
    expect(summary.shots).toBeGreaterThan(0);
  });

  it('leaves the unresolved parts null', () => {
    const thick = makeJoint({ id: 'J-9', odIn: 24, wallIn: 3 });
    const summary = summariseTechnique(buildTechnique({ ...base, joint: thick }));
    expect(summary.isotope).toBe(null);
    expect(summary.minutes).toBe(null);
    expect(summary.usable).toBe(false);
  });
});
