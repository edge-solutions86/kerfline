import { describe, it, expect } from 'vitest';
import {
  makeSource,
  makeLeakTest,
  checkOut,
  makeUse,
  whoHadIt,
} from '../../src/safety/sourcelog.js';
import { doseRateAt, doseForExposure, minutesToDose } from '../../src/safety/doserate.js';
import { barricadeDistanceFt, fitsInSpace } from '../../src/safety/boundary.js';
import { smallestFitting, collimatedBoundaryFt } from '../../src/safety/collimator.js';
import {
  makeSurvey,
  judgeRetraction,
  judgeBoundary,
  missingSurveys,
} from '../../src/safety/survey.js';
import { makeBadgeRecord, judgeAnnual } from '../../src/safety/dosimetry.js';
import { makeIncident, classify, markReported } from '../../src/safety/incident.js';
import { makePerson, checkCrew } from '../../src/personnel/roster.js';
import { makeCertification } from '../../src/personnel/cert.js';
import { makeTraining, currentToWork } from '../../src/personnel/training.js';

/**
 * A day of radiography, from the store to the dose record.
 *
 * The safety modules are the ones where a seam failing quietly matters most,
 * because every one of them answers a question whose wrong answer looks
 * exactly like the right one. This walks the day in the order it happens and
 * checks the handoffs.
 */

const day = '2025-03-14';

const source = makeSource({
  serial: 'IR-7741',
  isotopeId: 'IR192',
  assayCi: 80,
  assayDay: '2025-03-01',
});

const currentTest = makeLeakTest({ serial: 'IR-7741', day: '2025-03-01', microCuries: 0.0001 });

const certification = makeCertification({
  person: 'R. Ibarra',
  level: 'II',
  certifiedOn: '2023-05-10',
  examination: { general: 85, specific: 88, practical: 90 },
  trainingHours: 400,
  experienceHours: 2000,
  visionOn: '2025-01-06',
});

const crew = [
  makePerson({ name: 'R. Ibarra', role: 'radiographer', certification, monitored: true }),
  makePerson({ name: 'D. Whyte', role: 'assistant', monitored: true }),
];

const training = [
  makeTraining({
    person: 'R. Ibarra',
    method: 'SAFETY',
    course: 'radiation safety refresher',
    hours: 8,
    day: '2025-01-06',
  }),
];

describe('before the source leaves the store', () => {
  it('the crew is allowed to work', () => {
    expect(checkCrew(crew, day).mayWork).toBe(true);
  });

  it('the radiographer is current on safety training', () => {
    expect(currentToWork(training, day).current).toBe(true);
  });

  it('the source books out with the activity it has today', () => {
    const out = checkOut({ source, tests: [currentTest], day });
    expect(out.activityCi).toBeLessThan(80);
    expect(out.activityCi).toBeGreaterThan(70);
  });

  it('a source whose leak test has run out does not leave at all', () => {
    expect(() => checkOut({ source, tests: [currentTest], day: '2025-10-01' })).toThrow(
      /run out/
    );
  });
});

describe('setting the boundary', () => {
  const activityCi = checkOut({ source, tests: [currentTest], day }).activityCi;
  const camera = { isotopeId: 'IR192', activityCi };

  it('the rope goes a long way out on a bare source', () => {
    expect(barricadeDistanceFt(camera)).toBeGreaterThan(250);
  });

  it('the job does not fit in a tight bay bare', () => {
    expect(fitsInSpace({ ...camera, availableFt: 100 }).fits).toBe(false);
  });

  it('a collimator brings the boundary in behind the camera but not in front', () => {
    const bounds = collimatedBoundaryFt({ ...camera, collimator: 'DIRECTIONAL' });
    expect(bounds.outsideBeamFt).toBeLessThan(bounds.inBeamFt);
    expect(bounds.inBeamFt).toBeCloseTo(barricadeDistanceFt(camera), 1);
  });

  it('the tool picks the lightest sleeve that fits the bay', () => {
    const fitted = smallestFitting({ ...camera, availableFt: 100 });
    expect(fitted).not.toBe(null);
    expect(fitted.outsideBeamFt).toBeLessThanOrEqual(100);
  });

  it('a survey at the rope backs the calculation up', () => {
    const boundaryFt = barricadeDistanceFt(camera);
    const survey = makeSurvey({
      kind: 'boundary',
      day,
      instrument: 'Ludlum 3',
      readings: [
        { atFt: boundaryFt, rateMrPerHour: doseRateAt({ ...camera, distanceFt: boundaryFt }) },
      ],
    });
    expect(judgeBoundary(survey, { boundaryFt }).pass).toBe(true);
  });
});

describe('after the shot', () => {
  it('a camera reading at background lets the crew walk back in', () => {
    const survey = makeSurvey({
      kind: 'retraction',
      day,
      instrument: 'Ludlum 3',
      readings: [{ atFt: 1, rateMrPerHour: 0.03 }],
    });
    expect(judgeRetraction(survey).sourceIsHome).toBe(true);
  });

  it('a camera still reading hot stops everybody where they are', () => {
    const survey = makeSurvey({
      kind: 'retraction',
      day,
      instrument: 'Ludlum 3',
      readings: [{ atFt: 1, rateMrPerHour: 480 }],
    });
    const verdict = judgeRetraction(survey);
    expect(verdict.sourceIsHome).toBe(false);
    expect(verdict.action).toMatch(/do not approach/);
  });

  it('the day is not finished until all three surveys are on file', () => {
    const boundary = makeSurvey({
      kind: 'boundary',
      day,
      instrument: 'Ludlum 3',
      readings: [{ atFt: 300, rateMrPerHour: 1 }],
    });
    expect(missingSurveys([boundary])).toEqual(['perimeter', 'retraction']);
  });
});

describe('the dose that came of it', () => {
  const activityCi = checkOut({ source, tests: [currentTest], day }).activityCi;
  const camera = { isotopeId: 'IR192', activityCi };

  it('standing at the rope for an hour is a very small dose', () => {
    const atRope = doseForExposure({
      ...camera,
      distanceFt: barricadeDistanceFt(camera),
      minutes: 60,
    });
    expect(atRope).toBeLessThan(3);
  });

  it('standing close is a very short time before a limit', () => {
    const close = minutesToDose({ ...camera, distanceFt: 5, limitMr: 100 });
    const far = minutesToDose({ ...camera, distanceFt: 300, limitMr: 100 });
    expect(close).toBeLessThan(1);
    expect(far).toBeGreaterThan(close * 100);
  });

  it('a quiet quarter leaves the annual record well inside the limits', () => {
    const badge = makeBadgeRecord({
      person: 'R. Ibarra',
      from: '2025-01-01',
      to: '2025-03-31',
      mrem: 120,
    });
    const standing = judgeAnnual([badge], '2025-06-30');
    expect(standing.standing).toBe('within limits');
    expect(standing.complete).toBe(true);
  });

  it('a badge that never came back stops the record reading as safe', () => {
    const missing = makeBadgeRecord({
      person: 'R. Ibarra',
      from: '2025-04-01',
      to: '2025-06-30',
      mrem: null,
      returned: false,
    });
    expect(judgeAnnual([missing], '2025-06-30').standing).toBe('incomplete record');
  });
});

describe('when something goes wrong', () => {
  it('a source that failed to retract is reportable inside a day, exposed or not', () => {
    const clock = classify({ kind: 'disconnect', doseMrem: 0 });
    expect(clock.window).toBe('oneDay');
  });

  it('the incident carries the date it has to be reported by', () => {
    const incident = makeIncident({
      reference: 'INC-1',
      kind: 'disconnect',
      day,
      detail: 'source hung up in the guide tube, recovered with the crank',
    });
    expect(incident.reportBy).toBe('2025-03-15');
    const reported = markReported(incident, '2025-03-15');
    expect(reported.onTime).toBe(true);
  });

  it('the log can say who had the source that day', () => {
    const uses = [
      makeUse({
        serial: 'IR-7741',
        day,
        user: 'R. Ibarra',
        location: 'Station 6',
        minutes: 45,
        exposures: 12,
      }),
    ];
    const holders = whoHadIt(uses, { serial: 'IR-7741', day });
    expect(holders).toHaveLength(1);
    expect(holders[0].user).toBe('R. Ibarra');
  });
});
