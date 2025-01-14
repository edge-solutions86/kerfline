import { describe, it, expect } from 'vitest';
import {
  surveyKinds,
  makeReading,
  makeSurvey,
  judgeRetraction,
  judgeBoundary,
  boundaryFromReadings,
  highRadiationReadings,
  missingSurveys,
} from '../../src/safety/survey.js';

const survey = (kind, readings) =>
  makeSurvey({ kind, day: '2025-03-14', instrument: 'Ludlum 3', readings });

describe('makeReading', () => {
  it('rounds to what a meter can be read to', () => {
    const reading = makeReading({ atFt: 25.04, rateMrPerHour: 1.9994 });
    expect(reading.atFt).toBe(25);
    expect(reading.rateMrPerHour).toBe(1.999);
  });

  it('allows a reading of nothing at all', () => {
    expect(makeReading({ atFt: 300, rateMrPerHour: 0 }).rateMrPerHour).toBe(0);
  });

  it('refuses a distance at zero or a negative rate', () => {
    expect(() => makeReading({ atFt: 0, rateMrPerHour: 1 })).toThrow(/greater than 0/);
    expect(() => makeReading({ atFt: 10, rateMrPerHour: -1 })).toThrow(/not be below 0/);
  });
});

describe('makeSurvey', () => {
  it('names the three kinds', () => {
    expect(surveyKinds()).toEqual(['boundary', 'perimeter', 'retraction']);
  });

  it('builds a survey', () => {
    const record = survey('boundary', [{ atFt: 300, rateMrPerHour: 1.5 }]);
    expect(record.kind).toBe('boundary');
    expect(record.instrument).toBe('Ludlum 3');
    expect(record.readings).toHaveLength(1);
  });

  it('refuses a survey with no readings on it', () => {
    expect(() => survey('retraction', [])).toThrow(/no readings is not a survey/);
  });

  it('refuses a survey with no meter named', () => {
    expect(() =>
      makeSurvey({ kind: 'boundary', day: '2025-03-14', instrument: ' ', readings: [] })
    ).toThrow(/names the meter/);
  });

  it('refuses a kind it does not know', () => {
    expect(() =>
      makeSurvey({
        kind: 'walkaround',
        day: '2025-03-14',
        instrument: 'Ludlum 3',
        readings: [{ atFt: 1, rateMrPerHour: 1 }],
      })
    ).toThrow(/a survey is one of/);
  });

  it('refuses a date that is not a real day', () => {
    expect(() =>
      makeSurvey({
        kind: 'boundary',
        day: '2025-02-30',
        instrument: 'Ludlum 3',
        readings: [{ atFt: 1, rateMrPerHour: 1 }],
      })
    ).toThrow(/not a real calendar day/);
  });
});

describe('judgeRetraction', () => {
  it('says the source is home when the camera reads at background', () => {
    const record = survey('retraction', [{ atFt: 1, rateMrPerHour: 0.03 }]);
    const verdict = judgeRetraction(record);
    expect(verdict.sourceIsHome).toBe(true);
    expect(verdict.atBackground).toBe(true);
    expect(verdict.action).toMatch(/approach permitted/);
  });

  it('refuses to let anybody approach a camera still reading hot', () => {
    const record = survey('retraction', [{ atFt: 1, rateMrPerHour: 450 }]);
    const verdict = judgeRetraction(record);
    expect(verdict.sourceIsHome).toBe(false);
    expect(verdict.action).toMatch(/do not approach/);
  });

  it('takes the highest reading and not the last one', () => {
    const record = survey('retraction', [
      { atFt: 1, rateMrPerHour: 450 },
      { atFt: 1, rateMrPerHour: 0.03 },
    ]);
    expect(judgeRetraction(record).sourceIsHome).toBe(false);
  });

  it('flags a reading above background but under the threshold', () => {
    const record = survey('retraction', [{ atFt: 1, rateMrPerHour: 1.5 }]);
    const verdict = judgeRetraction(record);
    expect(verdict.sourceIsHome).toBe(true);
    expect(verdict.atBackground).toBe(false);
  });

  it('refuses to judge a survey of another kind', () => {
    const record = survey('boundary', [{ atFt: 300, rateMrPerHour: 1 }]);
    expect(() => judgeRetraction(record)).toThrow(/not a retraction survey/);
  });
});

describe('judgeBoundary', () => {
  it('passes when the readings at the rope are under the limit', () => {
    const record = survey('boundary', [{ atFt: 300, rateMrPerHour: 1.5 }]);
    expect(judgeBoundary(record, { boundaryFt: 300 }).pass).toBe(true);
  });

  it('fails when a reading outside the rope is over the limit', () => {
    const record = survey('boundary', [{ atFt: 300, rateMrPerHour: 4 }]);
    const verdict = judgeBoundary(record, { boundaryFt: 300 });
    expect(verdict.pass).toBe(false);
    expect(verdict.breaches).toHaveLength(1);
    expect(verdict.reason).toMatch(/inside the two milliroentgen line/);
  });

  it('fails when no reading was taken at the rope at all', () => {
    const record = survey('boundary', [{ atFt: 50, rateMrPerHour: 40 }]);
    const verdict = judgeBoundary(record, { boundaryFt: 300 });
    expect(verdict.pass).toBe(false);
    expect(verdict.reason).toMatch(/no reading was taken/);
  });

  it('ignores hot readings taken inside the rope', () => {
    const record = survey('boundary', [
      { atFt: 20, rateMrPerHour: 900 },
      { atFt: 300, rateMrPerHour: 1.5 },
    ]);
    expect(judgeBoundary(record, { boundaryFt: 300 }).pass).toBe(true);
  });

  it('refuses to judge a survey of another kind', () => {
    const record = survey('retraction', [{ atFt: 1, rateMrPerHour: 0.03 }]);
    expect(() => judgeBoundary(record, { boundaryFt: 300 })).toThrow(/not a boundary survey/);
  });
});

describe('boundaryFromReadings', () => {
  it('moves the rope out to where the limit actually falls', () => {
    const record = survey('boundary', [{ atFt: 100, rateMrPerHour: 8 }]);
    expect(boundaryFromReadings(record)).toBeCloseTo(200, 0);
  });

  it('takes the worst reading rather than the furthest', () => {
    // 0.5 mR/h at 300 ft implies a weaker source than 8 mR/h at 100 ft, so
    // the near reading governs and the rope goes to 200 ft.
    const record = survey('boundary', [
      { atFt: 100, rateMrPerHour: 8 },
      { atFt: 300, rateMrPerHour: 0.5 },
    ]);
    expect(boundaryFromReadings(record)).toBeCloseTo(200, 0);
  });

  it('lets a distant reading govern when it implies the hotter source', () => {
    // 1 mR/h still showing at 300 ft implies more than 8 mR/h at 100 ft does,
    // which is exactly the scatter the calculation did not know about.
    const record = survey('boundary', [
      { atFt: 100, rateMrPerHour: 8 },
      { atFt: 300, rateMrPerHour: 1 },
    ]);
    expect(boundaryFromReadings(record)).toBeCloseTo(212, 0);
  });

  it('is nothing when every reading is clean', () => {
    const record = survey('boundary', [{ atFt: 300, rateMrPerHour: 0 }]);
    expect(boundaryFromReadings(record)).toBe(0);
  });
});

describe('highRadiationReadings', () => {
  it('picks out readings over the high radiation limit', () => {
    const record = survey('perimeter', [
      { atFt: 10, rateMrPerHour: 400 },
      { atFt: 200, rateMrPerHour: 5 },
    ]);
    expect(highRadiationReadings(record)).toHaveLength(1);
  });

  it('finds nothing on a clean survey', () => {
    const record = survey('perimeter', [{ atFt: 200, rateMrPerHour: 5 }]);
    expect(highRadiationReadings(record)).toEqual([]);
  });
});

describe('missingSurveys', () => {
  it('names what has not been done', () => {
    const done = [survey('boundary', [{ atFt: 300, rateMrPerHour: 1 }])];
    expect(missingSurveys(done)).toEqual(['perimeter', 'retraction']);
  });

  it('is empty once all three are done', () => {
    const done = surveyKinds().map((kind) => survey(kind, [{ atFt: 1, rateMrPerHour: 1 }]));
    expect(missingSurveys(done)).toEqual([]);
  });

  it('names all three when nothing has been done', () => {
    expect(missingSurveys([])).toEqual(['boundary', 'perimeter', 'retraction']);
  });
});
