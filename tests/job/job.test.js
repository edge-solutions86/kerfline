import { describe, it, expect } from 'vitest';
import {
  makeJob,
  contractFor,
  judgeUnderContract,
  deviations,
  describeJob,
} from '../../src/job/job.js';
import { makeJoint } from '../../src/joint/spec.js';
import { makeIndication } from '../../src/indication/measure.js';

const job = makeJob({
  number: 'C-4471',
  client: 'Sabine Midstream',
  site: 'Station 6',
  code: 'API1104',
  coverage: 'ten',
  startedOn: '2025-03-01',
});

const joint = makeJoint({ id: 'J-118', nominalSize: 6, schedule: 40 });

describe('makeJob', () => {
  it('keeps the decisions somebody made', () => {
    expect(job.number).toBe('C-4471');
    expect(job.client).toBe('Sabine Midstream');
    expect(job.code).toBe('API1104');
    expect(job.coverage).toBe('ten');
  });

  it('works the coverage fraction out from the level', () => {
    expect(job.coverageFraction).toBeCloseTo(0.1, 4);
  });

  it('takes the shop defaults for the rest', () => {
    expect(job.reinforcementMaxIn).toBeCloseTo(0.0625, 4);
    expect(job.densityMinimum).toBe(2);
    expect(job.calledForSensitivity).toBe(2);
  });

  it('refuses a job with no number or no client', () => {
    expect(() => makeJob({ client: 'x', startedOn: '2025-03-01' })).toThrow(
      /identified by its number/
    );
    expect(() => makeJob({ number: 'C-1', startedOn: '2025-03-01' })).toThrow(
      /names the client/
    );
  });

  it('refuses a code nobody implemented', () => {
    expect(() =>
      makeJob({ number: 'C-1', client: 'x', code: 'BS2910', startedOn: '2025-03-01' })
    ).toThrow(/acceptance code must be one of/);
  });

  it('refuses a coverage level nobody specified', () => {
    expect(() =>
      makeJob({ number: 'C-1', client: 'x', coverage: 'most', startedOn: '2025-03-01' })
    ).toThrow(/radiography level/);
  });

  it('only checks the fluid service when the code cares about it', () => {
    expect(() =>
      makeJob({
        number: 'C-1',
        client: 'x',
        code: 'B31.3',
        service: 'lethal',
        startedOn: '2025-03-01',
      })
    ).toThrow(/fluid service/);
    expect(() =>
      makeJob({
        number: 'C-1',
        client: 'x',
        code: 'API1104',
        service: 'lethal',
        startedOn: '2025-03-01',
      })
    ).not.toThrow();
  });

  it('refuses a start date that is not a real day', () => {
    expect(() => makeJob({ number: 'C-1', client: 'x', startedOn: '2025-02-30' })).toThrow(
      /not a real calendar day/
    );
  });
});

describe('contractFor', () => {
  it('gathers what every judgement should use', () => {
    expect(contractFor(job)).toEqual({
      code: 'API1104',
      service: 'normal',
      maximumReinforcementIn: 0.0625,
      densityMinimum: 2,
      calledForSensitivity: 2,
    });
  });
});

describe('judgeUnderContract', () => {
  it('judges against the code the contract names', () => {
    const result = judgeUnderContract(job, { joint, indications: [] });
    expect(result.code).toBe('API1104');
    expect(result.disposition).toBe('accept');
  });

  it('gives a different answer under a different contract', () => {
    const plant = makeJob({
      number: 'C-9',
      client: 'x',
      code: 'B31.3',
      startedOn: '2025-03-01',
    });
    const indications = [makeIndication({ type: 'IP', measuredIn: 0.5, at: 2 })];
    expect(judgeUnderContract(job, { joint, indications }).disposition).toBe('accept');
    expect(judgeUnderContract(plant, { joint, indications }).disposition).toBe('reject');
  });

  it('carries the fluid service through to the code that reads it', () => {
    const light = makeJob({
      number: 'C-9',
      client: 'x',
      code: 'B31.3',
      service: 'category_d',
      startedOn: '2025-03-01',
    });
    const indications = [makeIndication({ type: 'IP', measuredIn: 0.5, at: 2 })];
    expect(judgeUnderContract(light, { joint, indications }).disposition).toBe('accept');
  });

  it('refuses indications that are not a list', () => {
    expect(() => judgeUnderContract(job, { joint, indications: null })).toThrow(
      /must be an array/
    );
  });
});

describe('deviations', () => {
  it('conforms when everything matches', () => {
    const check = deviations(job, { code: 'API1104', densityMinimum: 2 });
    expect(check.conforms).toBe(true);
    expect(check.deviations).toEqual([]);
  });

  it('catches a joint judged on the wrong code', () => {
    const check = deviations(job, { code: 'AWSD1.1' });
    expect(check.conforms).toBe(false);
    expect(check.deviations[0]).toEqual({
      field: 'code',
      agreed: 'API1104',
      actual: 'AWSD1.1',
    });
  });

  it('catches a film read below the agreed density', () => {
    expect(deviations(job, { densityMinimum: 1.8 }).deviations[0].field).toBe('density');
  });

  it('does not complain about a film read above it', () => {
    expect(deviations(job, { densityMinimum: 2.5 }).conforms).toBe(true);
  });

  it('catches a crown higher than the contract allows', () => {
    expect(deviations(job, { reinforcementIn: 0.125 }).deviations[0].field).toBe(
      'reinforcement'
    );
  });

  it('catches coverage short of what was agreed', () => {
    expect(deviations(job, { coverageFraction: 0.05 }).deviations[0].field).toBe('coverage');
  });

  it('ignores the fluid service on a code that does not read it', () => {
    expect(deviations(job, { service: 'severe_cyclic' }).conforms).toBe(true);
  });

  it('catches it on a code that does', () => {
    const plant = makeJob({
      number: 'C-9',
      client: 'x',
      code: 'B31.3',
      startedOn: '2025-03-01',
    });
    expect(deviations(plant, { service: 'severe_cyclic' }).deviations[0].field).toBe('service');
  });

  it('reports every deviation at once', () => {
    const check = deviations(job, {
      code: 'AWSD1.1',
      densityMinimum: 1.5,
      reinforcementIn: 0.2,
      coverageFraction: 0.02,
    });
    expect(check.deviations).toHaveLength(4);
  });

  it('refuses a check with nothing to compare against', () => {
    expect(() => deviations(job, null)).toThrow(/what actually happened/);
  });
});

describe('describeJob', () => {
  it('reads as one line for a report header', () => {
    expect(describeJob(job)).toBe(
      'C-4471 for Sabine Midstream at Station 6, API1104 at ten coverage'
    );
  });

  it('leaves the site out when there is none', () => {
    const noSite = makeJob({ number: 'C-1', client: 'x', startedOn: '2025-03-01' });
    expect(describeJob(noSite)).toBe('C-1 for x, API1104 at five coverage');
  });
});
