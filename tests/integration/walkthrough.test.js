import { describe, it, expect } from 'vitest';
import { makeJob, judgeUnderContract, deviations } from '../../src/job/job.js';
import { makeRegister, addJoints, getJoint, registerTotals } from '../../src/joint/register.js';
import { makeProcedure, chooseProcedure } from '../../src/joint/wps.js';
import { makeSource, makeLeakTest } from '../../src/safety/sourcelog.js';
import { makeJournal, verify } from '../../src/store/journal.js';
import { shoot } from '../../src/pipeline/shoot.js';
import { interpret } from '../../src/pipeline/interpret.js';
import { jointState, recordRepair, openJoints } from '../../src/pipeline/close.js';
import { planReshoot, recordReshoot } from '../../src/pipeline/reshoot.js';
import { gradeFilm } from '../../src/pipeline/interpret.js';
import { makeIndication } from '../../src/indication/measure.js';
import { makeCertification } from '../../src/personnel/cert.js';
import { makePerson, checkCrew, signatory } from '../../src/personnel/roster.js';
import { buildRegisterReport, renderRegisterReport } from '../../src/report/register.js';
import { buildSummary } from '../../src/report/summary.js';
import { stateAt, reopenings } from '../../src/store/snapshot.js';
import { exportRegister, verifyChecksum } from '../../src/report/export.js';

/**
 * One job, walked from the contract to the register report.
 *
 * The unit tests each hold one module honest. This one holds the seams
 * between them honest, which is where the interesting failures live: a
 * thickness that means one thing to the chart and another to the code, a
 * verdict that reaches the journal in a shape the report cannot read, a
 * repair chain rebuilt from entries that were written by three different
 * functions.
 */

const job = makeJob({
  number: 'C-4471',
  client: 'Sabine Midstream',
  site: 'Station 6',
  code: 'API1104',
  coverage: 'ten',
  startedOn: '2025-03-01',
});

const register = addJoints(makeRegister(job.number), [
  { id: 'J-101', spool: 'SP-1', welder: 'A17', nominalSize: 6, schedule: 40 },
  { id: 'J-102', spool: 'SP-1', welder: 'A17', nominalSize: 6, schedule: 40 },
  { id: 'J-103', spool: 'SP-2', welder: 'B04', nominalSize: 6, schedule: 40 },
]);

const source = makeSource({
  serial: 'IR-7741',
  isotopeId: 'IR192',
  assayCi: 80,
  assayDay: '2025-03-01',
});
const leakTests = [makeLeakTest({ serial: 'IR-7741', day: '2025-03-01', microCuries: 0.0001 })];

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

const setup = {
  job: job.number,
  source,
  leakTests,
  sourceToObjectIn: 48,
  objectToFilmIn: 6.625,
};

const goodFilm = (jointId, position) => ({
  film: position,
  identification: {
    job: job.number,
    day: '2025-03-14',
    jointId,
    position,
  },
  sourceKind: 'gamma',
  indicatorDensity: 2.4,
  areaDensities: [
    { at: 'A', density: 2.3 },
    { at: 'B', density: 2.5 },
  ],
  indicator: { identity: 20, hole: '2T' },
  penetratedThicknessIn: 0.56,
});

describe('before anything is shot', () => {
  it('the crew may work', () => {
    const check = checkCrew(crew, '2025-03-14');
    expect(check.mayWork).toBe(true);
    expect(signatory(crew, '2025-03-14').name).toBe('R. Ibarra');
  });

  it('the register knows what the job amounts to', () => {
    const totals = registerTotals(register);
    expect(totals.joints).toBe(3);
    expect(totals.welders).toBe(2);
    expect(totals.weldLengthIn).toBeGreaterThan(60);
  });

  it('a qualified procedure covers every joint on it', () => {
    const procedures = [
      makeProcedure({
        id: 'WPS-14',
        process: 'SMAW',
        couponThicknessIn: 0.375,
        couponOdIn: 6.625,
        testPosition: '6G',
      }),
    ];
    for (const id of ['J-101', 'J-102', 'J-103']) {
      expect(chooseProcedure(procedures, getJoint(register, id)).id).toBe('WPS-14');
    }
  });
});

describe('a joint that passes first time', () => {
  const joint = getJoint(register, 'J-101');
  const shotResult = shoot(makeJournal(job.number), { ...setup, joint, day: '2025-03-14' });

  it('plans and records a shot the source was fit to make', () => {
    expect(shotResult.plan.ready).toBe(true);
    expect(shotResult.plan.source.activityCi).toBeLessThan(80);
  });

  it('reads clean and lands an acceptance in the journal', () => {
    const read = interpret(shotResult.journal, {
      joint,
      day: '2025-03-14',
      reader: 'R. Ibarra',
      certification,
      code: job.code,
      films: [goodFilm('J-101', '1')],
      indications: [makeIndication({ type: 'POROSITY', measuredIn: 0.04, at: 3 })],
    });
    expect(read.result.outcome).toBe('accept');
    expect(jointState(read.journal, 'J-101').status).toBe('accepted');
    expect(verify(read.journal).intact).toBe(true);
  });
});

describe('a joint that has to be repaired', () => {
  const joint = getJoint(register, 'J-102');

  it('walks shot, repair, reshoot and acceptance', () => {
    let journal = makeJournal(job.number);
    journal = shoot(journal, { ...setup, joint, day: '2025-03-14' }).journal;

    const first = interpret(journal, {
      joint,
      day: '2025-03-14',
      reader: 'R. Ibarra',
      certification,
      code: job.code,
      films: [goodFilm('J-102', '1')],
      indications: [makeIndication({ type: 'SLAG', measuredIn: 3, at: 2 })],
    });
    expect(first.result.outcome).toBe('repair');
    journal = first.journal;
    expect(jointState(journal, 'J-102').status).toBe('awaiting repair');

    journal = recordRepair(journal, {
      jointId: 'J-102',
      day: '2025-03-17',
      at: '2:00',
      welder: 'A17',
    });

    journal = shoot(journal, { ...setup, joint, day: '2025-03-18' }).journal;
    const second = interpret(journal, {
      joint,
      day: '2025-03-18',
      reader: 'R. Ibarra',
      certification,
      code: job.code,
      films: [
        {
          ...goodFilm('J-102', '1'),
          identification: { ...goodFilm('J-102', '1').identification, day: '2025-03-18' },
        },
      ],
      indications: [],
    });

    expect(second.result.outcome).toBe('accept');
    expect(jointState(second.journal, 'J-102').status).toBe('accepted');
    expect(jointState(second.journal, 'J-102').repairs).toBe(1);
    expect(verify(second.journal).intact).toBe(true);
  });
});

describe('a film that has to be shot again', () => {
  const joint = getJoint(register, 'J-103');

  it('gives no weld verdict and comes back with a longer exposure', () => {
    let journal = makeJournal(job.number);
    const shotResult = shoot(journal, { ...setup, joint, day: '2025-03-14' });
    journal = shotResult.journal;

    const light = { ...goodFilm('J-103', '1'), indicatorDensity: 1.6, areaDensities: [] };
    const read = interpret(journal, {
      joint,
      day: '2025-03-14',
      reader: 'R. Ibarra',
      certification,
      code: job.code,
      films: [light],
      indications: [makeIndication({ type: 'CRACK', measuredIn: 0.5, at: 2 })],
    });

    // The crack is on the film. It is not judged, because the film is not
    // readable, and reporting a rejection off it would be reporting a
    // conclusion the film cannot support.
    expect(read.result.outcome).toBe('reshoot');
    expect(read.result.verdict).toBe(null);
    journal = read.journal;

    const plan = planReshoot({
      grade: gradeFilm(light),
      minutes: shotResult.plan.technique.exposure.minutes,
    });
    expect(plan.needed).toBe(true);
    expect(plan.minutes).toBeGreaterThan(shotResult.plan.technique.exposure.minutes);

    journal = recordReshoot(journal, { jointId: 'J-103', day: '2025-03-15', plan, film: '1' });
    expect(verify(journal).intact).toBe(true);
    expect(jointState(journal, 'J-103').open).toBe(true);
  });
});

describe('the job as a whole', () => {
  function wholeJob() {
    let journal = makeJournal(job.number);
    for (const id of ['J-101', 'J-102', 'J-103']) {
      journal = shoot(journal, {
        ...setup,
        joint: getJoint(register, id),
        day: '2025-03-14',
      }).journal;
    }
    for (const [id, indications] of [
      ['J-101', []],
      ['J-102', [makeIndication({ type: 'SLAG', measuredIn: 3, at: 2 })]],
      ['J-103', []],
    ]) {
      journal = interpret(journal, {
        joint: getJoint(register, id),
        day: '2025-03-14',
        reader: 'R. Ibarra',
        certification,
        code: job.code,
        films: [goodFilm(id, '1')],
        indications,
      }).journal;
    }
    return journal;
  }

  const journal = wholeJob();

  it('keeps the chain intact across every writer', () => {
    expect(verify(journal).intact).toBe(true);
  });

  it('leaves exactly the repaired joint open', () => {
    expect(openJoints(journal).map((state) => state.jointId)).toEqual(['J-102']);
  });

  it('reports the register with what happened against each joint', () => {
    const report = buildRegisterReport({ register, journal });
    expect(report.rows).toHaveLength(3);
    expect(report.strays).toEqual([]);
    const text = renderRegisterReport(report);
    expect(text).toContain('J-101');
    expect(text).toContain('repair');
  });

  it('summarises the job with the denominators showing', () => {
    const joints = ['J-101', 'J-102', 'J-103'].map((id) => {
      const state = jointState(journal, id);
      return {
        id,
        welder: getJoint(register, id).welder,
        shot: state.everShot,
        disposition: state.status === 'accepted' ? 'accept' : 'repair',
      };
    });
    const summary = buildSummary({ job: job.number, joints, level: job.coverage });
    expect(summary.counts.shot).toBe(3);
    expect(summary.rate.shot).toBe(3);
    expect(summary.coverage.compliant).toBe(true);
    expect(summary.uncovered).toEqual([]);
  });

  it('exports a register a client can check the checksum of', () => {
    const report = buildRegisterReport({ register, journal });
    const csv = exportRegister(
      report.rows.map((row) => ({ ...row, nominalSize: row.size, schedule: '' }))
    );
    expect(verifyChecksum(csv).intact).toBe(true);
    expect(csv).toContain('J-101');
  });

  it('answers what the record said on the day', () => {
    expect(stateAt(journal, '2025-03-13')).toEqual({});
    expect(Object.keys(stateAt(journal, '2025-03-14'))).toHaveLength(3);
  });

  it('finds no joint that was accepted and later reopened', () => {
    expect(reopenings(journal)).toEqual([]);
  });

  it('conforms to the contract it was judged under', () => {
    const check = deviations(job, { code: job.code, densityMinimum: 2.4, coverageFraction: 1 });
    expect(check.conforms).toBe(true);
  });

  it('judges the same joint differently under a different contract', () => {
    const joint = getJoint(register, 'J-102');
    const indications = [makeIndication({ type: 'SLAG', measuredIn: 0.15, at: 2 })];
    const pipeline = judgeUnderContract(job, { joint, indications });
    const plant = judgeUnderContract(
      makeJob({ number: 'C-9', client: 'x', code: 'B31.3', startedOn: '2025-03-01' }),
      { joint, indications }
    );
    expect(pipeline.disposition).toBe('accept');
    expect(plant.disposition).toBe('repair');
  });
});
