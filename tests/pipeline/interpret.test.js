import { describe, it, expect } from 'vitest';
import {
  qualityChecks,
  gradeFilm,
  interpretJoint,
  recordInterpretation,
  interpret,
} from '../../src/pipeline/interpret.js';
import { makeJoint } from '../../src/joint/spec.js';
import { makeIndication } from '../../src/indication/measure.js';
import { makeJournal, verify } from '../../src/store/journal.js';
import { makeCertification } from '../../src/personnel/cert.js';

const joint = makeJoint({ id: 'J-118', nominalSize: 6, schedule: 40 });

const goodFilm = () => ({
  film: '1',
  identification: { job: 'C-4471', day: '2025-03-14', jointId: 'J-118', position: '1' },
  sourceKind: 'gamma',
  indicatorDensity: 2.4,
  areaDensities: [2.3, 2.5, 2.4],
  indicator: { identity: 20, hole: '2T' },
  penetratedThicknessIn: 0.56,
});

describe('gradeFilm', () => {
  it('names the checks it runs', () => {
    expect(qualityChecks()).toEqual([
      'identification',
      'density',
      'variation',
      'sensitivity',
      'backscatter',
    ]);
  });

  it('passes a good film', () => {
    const graded = gradeFilm(goodFilm());
    expect(graded.acceptable).toBe(true);
    expect(graded.failures).toEqual([]);
  });

  it('fails a film that is too light', () => {
    const graded = gradeFilm({ ...goodFilm(), indicatorDensity: 1.6, areaDensities: [1.6] });
    expect(graded.acceptable).toBe(false);
    expect(graded.failures.some((f) => f.check === 'density')).toBe(true);
  });

  it('accepts the same density under an X-ray source', () => {
    const graded = gradeFilm({
      ...goodFilm(),
      sourceKind: 'xray',
      indicatorDensity: 1.9,
      areaDensities: [1.9],
    });
    expect(graded.acceptable).toBe(true);
  });

  it('fails a film whose area of interest wanders outside the band', () => {
    const graded = gradeFilm({ ...goodFilm(), areaDensities: [2.4, 1.5] });
    expect(graded.failures.some((f) => f.check === 'variation')).toBe(true);
  });

  it('fails a film with no indicator on it', () => {
    const graded = gradeFilm({ ...goodFilm(), indicator: null });
    expect(graded.failures.some((f) => f.check === 'sensitivity')).toBe(true);
  });

  it('fails a film showing a thicker plaque than the table calls for', () => {
    const graded = gradeFilm({ ...goodFilm(), indicator: { identity: 60, hole: '2T' } });
    expect(graded.failures.some((f) => f.check === 'sensitivity')).toBe(true);
  });

  it('fails a film showing only the 4T hole', () => {
    const graded = gradeFilm({ ...goodFilm(), indicator: { identity: 20, hole: '4T' } });
    expect(graded.failures.some((f) => f.check === 'sensitivity')).toBe(true);
  });

  it('passes a film showing a thinner plaque, which is the harder job', () => {
    const graded = gradeFilm({ ...goodFilm(), indicator: { identity: 15, hole: '2T' } });
    expect(graded.acceptable).toBe(true);
  });

  it('passes a film showing the 1T hole on the plaque called for', () => {
    const graded = gradeFilm({ ...goodFilm(), indicator: { identity: 20, hole: '1T' } });
    expect(graded.acceptable).toBe(true);
  });

  it('carries the equivalent percentage for the record without judging on it', () => {
    const graded = gradeFilm(goodFilm());
    expect(graded.sensitivity.equivalentPercent).toBeGreaterThan(0);
    expect(graded.sensitivity.required).toEqual({ identity: 20, hole: '2T' });
  });

  it('fails a film missing its identification', () => {
    const graded = gradeFilm({ ...goodFilm(), identification: { job: 'C-4471' } });
    expect(graded.failures.some((f) => f.check === 'identification')).toBe(true);
  });

  it('fails a film on a light backscatter letter', () => {
    const graded = gradeFilm({
      ...goodFilm(),
      backscatter: { letterDensity: 1.8, backgroundDensity: 2.4 },
    });
    expect(graded.failures.some((f) => f.check === 'backscatter')).toBe(true);
  });

  it('passes a film on a dark backscatter letter', () => {
    const graded = gradeFilm({
      ...goodFilm(),
      backscatter: { letterDensity: 3, backgroundDensity: 2.4 },
    });
    expect(graded.acceptable).toBe(true);
  });

  it('runs every check rather than stopping at the first failure', () => {
    const graded = gradeFilm({
      ...goodFilm(),
      identification: {},
      indicatorDensity: 1.2,
      indicator: null,
    });
    const checks = new Set(graded.failures.map((f) => f.check));
    expect(checks.size).toBeGreaterThanOrEqual(3);
  });

  it('refuses something that is not an object', () => {
    expect(() => gradeFilm('film 1')).toThrow(/graded from an object/);
  });
});

describe('interpretJoint', () => {
  const base = { joint, day: '2025-03-14', reader: 'R. Ibarra', code: 'API1104' };

  it('judges the weld once every film is good', () => {
    const result = interpretJoint({
      ...base,
      films: [goodFilm()],
      indications: [makeIndication({ type: 'SLAG', measuredIn: 0.5, at: 3 })],
    });
    expect(result.outcome).toBe('accept');
    expect(result.verdict).not.toBe(null);
  });

  it('calls a repair when the weld fails the code', () => {
    const result = interpretJoint({
      ...base,
      films: [goodFilm()],
      indications: [makeIndication({ type: 'SLAG', measuredIn: 3, at: 3 })],
    });
    expect(result.outcome).toBe('repair');
  });

  it('calls a reshoot and gives no verdict when a film fails quality', () => {
    const result = interpretJoint({
      ...base,
      films: [{ ...goodFilm(), indicatorDensity: 1.5, areaDensities: [1.5] }],
      indications: [makeIndication({ type: 'CRACK', measuredIn: 0.5, at: 3 })],
    });
    expect(result.outcome).toBe('reshoot');
    expect(result.verdict).toBe(null);
  });

  it('does not judge the weld at all on a bad film, even a clean one', () => {
    const result = interpretJoint({
      ...base,
      films: [{ ...goodFilm(), indicator: null }],
      indications: [],
    });
    expect(result.outcome).toBe('reshoot');
    expect(result.verdict).toBe(null);
  });

  it('says which film failed and why', () => {
    const result = interpretJoint({
      ...base,
      films: [
        goodFilm(),
        { ...goodFilm(), film: '2', indicatorDensity: 1.5, areaDensities: [1.5] },
      ],
      indications: [],
    });
    expect(result.reasons.join(' ')).toMatch(/^2:|\s2:/);
  });

  it('calls a reshoot when no films were presented at all', () => {
    const result = interpretJoint({ ...base, films: [], indications: [] });
    expect(result.outcome).toBe('reshoot');
    expect(result.reasons).toEqual(['no films were presented']);
  });

  it('refuses a reader whose certification has lapsed', () => {
    const lapsed = makeCertification({
      person: 'R. Ibarra',
      level: 'II',
      certifiedOn: '2018-05-10',
      examination: { general: 85, specific: 88, practical: 90 },
      trainingHours: 80,
      experienceHours: 900,
      visionOn: '2018-05-10',
    });
    expect(() =>
      interpretJoint({ ...base, certification: lapsed, films: [goodFilm()], indications: [] })
    ).toThrow(/may not interpret film/);
  });

  it('allows a reader whose certification is current', () => {
    const current = makeCertification({
      person: 'R. Ibarra',
      level: 'II',
      certifiedOn: '2023-05-10',
      examination: { general: 85, specific: 88, practical: 90 },
      trainingHours: 80,
      experienceHours: 900,
      visionOn: '2025-01-06',
    });
    const result = interpretJoint({
      ...base,
      certification: current,
      films: [goodFilm()],
      indications: [],
    });
    expect(result.outcome).toBe('accept');
  });

  it('refuses an interpretation with no joint', () => {
    expect(() => interpretJoint({ ...base, joint: null, films: [] })).toThrow(/needs a joint/);
  });
});

describe('recordInterpretation', () => {
  const base = { joint, day: '2025-03-14', reader: 'R. Ibarra', code: 'API1104' };

  it('writes an interpretation and a verdict when the weld was judged', () => {
    const result = interpretJoint({ ...base, films: [goodFilm()], indications: [] });
    const journal = recordInterpretation(makeJournal('C-4471'), result);
    expect(journal.entries.map((e) => e.kind)).toEqual(['interpretation', 'verdict']);
    expect(verify(journal).intact).toBe(true);
  });

  it('writes only the interpretation on a reshoot', () => {
    const result = interpretJoint({ ...base, films: [], indications: [] });
    const journal = recordInterpretation(makeJournal('C-4471'), result);
    expect(journal.entries.map((e) => e.kind)).toEqual(['interpretation']);
  });

  it('records how many films were turned back', () => {
    const result = interpretJoint({
      ...base,
      films: [goodFilm(), { ...goodFilm(), film: '2', indicator: null }],
      indications: [],
    });
    const journal = recordInterpretation(makeJournal('C-4471'), result);
    expect(journal.entries[0].payload.filmsRejected).toBe(1);
  });

  it('refuses a result with no joint on it', () => {
    expect(() => recordInterpretation(makeJournal('C-4471'), {})).toThrow(/needs a joint/);
  });
});

describe('interpret', () => {
  it('interprets and records in one step', () => {
    const { journal, result } = interpret(makeJournal('C-4471'), {
      joint,
      day: '2025-03-14',
      reader: 'R. Ibarra',
      films: [goodFilm()],
      indications: [],
    });
    expect(result.outcome).toBe('accept');
    expect(journal.entries).toHaveLength(2);
    expect(verify(journal).intact).toBe(true);
  });
});
