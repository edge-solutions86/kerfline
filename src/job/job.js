import { BadInput } from '../errors.js';
import { requireArray, requireAtLeast, requireOneOf } from '../util/guard.js';
import { requireDay } from '../util/dates.js';
import { codes, judge } from '../accept/engine.js';
import { levels as coverageLevels, levelFraction } from '../sampling/coverage.js';
import { services } from '../accept/asme31_3.js';

/**
 * The contract, as a record everything else is judged against.
 *
 * A job is not only a list of joints. It is a set of decisions somebody made
 * before any of them were welded: which code governs, which fluid service the
 * line runs in, how much of it gets radiographed, how much crown is allowed,
 * what density the client will accept. Every one of those changes an answer
 * somewhere downstream.
 *
 * Holding them here rather than passing them around loose is what makes it
 * possible to ask the useful question, which is not "does this weld pass" but
 * "does this weld pass under what we agreed to". Those come apart more often
 * than anybody would like, and when they do the deviation is the finding.
 */

export function makeJob(input) {
  if (typeof input !== 'object' || input === null) {
    throw new BadInput('a job is described by an object', { got: input });
  }
  const {
    number,
    client,
    site = null,
    code = 'API1104',
    service = 'normal',
    coverage = 'five',
    startedOn,
    reinforcementMaxIn = 0.0625,
    densityMinimum = 2,
    calledForSensitivity = 2,
  } = input;

  if (typeof number !== 'string' || number.trim() === '') {
    throw new BadInput('a job is identified by its number', { got: number });
  }
  if (typeof client !== 'string' || client.trim() === '') {
    throw new BadInput('a job names the client it is for', { got: client });
  }
  requireOneOf(code, codes(), 'acceptance code');
  requireOneOf(coverage, coverageLevels(), 'radiography level');
  if (code === 'B31.3') requireOneOf(service, services(), 'fluid service');
  requireDay(startedOn, 'start date');
  requireAtLeast(reinforcementMaxIn, 0, 'maximum reinforcement');
  requireAtLeast(densityMinimum, 0, 'minimum density');

  return {
    number: number.trim(),
    client: client.trim(),
    site,
    code,
    service,
    coverage,
    startedOn,
    reinforcementMaxIn,
    densityMinimum,
    calledForSensitivity,
    coverageFraction: levelFraction(coverage),
  };
}

/** The parameters every judgement on this job should be made with. */
export function contractFor(job) {
  return {
    code: job.code,
    service: job.service,
    maximumReinforcementIn: job.reinforcementMaxIn,
    densityMinimum: job.densityMinimum,
    calledForSensitivity: job.calledForSensitivity,
  };
}

/** Judge a joint under the contract rather than under whatever was passed. */
export function judgeUnderContract(job, { joint, indications, ...extra }) {
  requireArray(indications, 'indications');
  return judge({
    code: job.code,
    service: job.service,
    indications,
    joint,
    ...extra,
  });
}

/**
 * Where a shot departed from what the contract called for. Each deviation
 * names the field, what was agreed and what happened, because a deviation
 * report that says only "does not conform" starts an argument rather than
 * ending one.
 */
export function deviations(job, actual) {
  if (typeof actual !== 'object' || actual === null) {
    throw new BadInput('a deviation check needs what actually happened', { got: actual });
  }
  const found = [];

  if (actual.code !== undefined && actual.code !== job.code) {
    found.push({ field: 'code', agreed: job.code, actual: actual.code });
  }
  if (actual.service !== undefined && job.code === 'B31.3' && actual.service !== job.service) {
    found.push({ field: 'service', agreed: job.service, actual: actual.service });
  }
  if (actual.densityMinimum !== undefined && actual.densityMinimum < job.densityMinimum) {
    found.push({
      field: 'density',
      agreed: job.densityMinimum,
      actual: actual.densityMinimum,
    });
  }
  if (actual.reinforcementIn !== undefined && actual.reinforcementIn > job.reinforcementMaxIn) {
    found.push({
      field: 'reinforcement',
      agreed: job.reinforcementMaxIn,
      actual: actual.reinforcementIn,
    });
  }
  if (actual.coverageFraction !== undefined && actual.coverageFraction < job.coverageFraction) {
    found.push({
      field: 'coverage',
      agreed: job.coverageFraction,
      actual: actual.coverageFraction,
    });
  }

  return { job: job.number, deviations: found, conforms: found.length === 0 };
}

/** A one line description for the head of every report on this job. */
export function describeJob(job) {
  const where = job.site ? ` at ${job.site}` : '';
  return `${job.number} for ${job.client}${where}, ${job.code} at ${job.coverage} coverage`;
}
