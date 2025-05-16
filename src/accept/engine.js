import { BadInput, Unsupported } from '../errors.js';
import { round } from '../util/num.js';
import { requireArray } from '../util/guard.js';
import * as api1104 from './api1104.js';
import * as asme8 from './asme8.js';
import * as b313 from './asme31_3.js';
import * as aws from './awsd1_1.js';

/**
 * Dispatching a joint to the code that governs it.
 *
 * The three codes do not measure the same joint the same way, and this is the
 * module that has to know it. API 1104 wants the wall and the length of weld
 * all the way round, because it caps defects as a percentage of that length.
 * Section VIII wants the weld thickness with the reinforcement taken off,
 * because its length limit is banded on it. B31.3 wants the thinner of the
 * two components joined and the fluid service the line will run in.
 *
 * Handing any of those the wrong number produces a verdict that looks
 * perfectly well formed and is about a different weld, so the extraction from
 * a joint record happens here, once, rather than at every call site.
 */

const CODES = {
  API1104: {
    label: 'API 1104, pipeline girth welds',
    needs: ['wallIn', 'weldLengthIn'],
    judge: (indications, context) =>
      api1104.judgeJoint({
        indications,
        wallIn: context.wallIn,
        weldLengthIn: context.weldLengthIn,
      }),
  },
  ASME8: {
    label: 'ASME Section VIII Division 1, UW-51',
    needs: ['thicknessIn'],
    judge: (indications, context) =>
      asme8.judgeJoint({ indications, thicknessIn: context.thicknessIn }),
  },
  'B31.3': {
    label: 'ASME B31.3, process piping',
    needs: ['thinnerWallIn', 'service'],
    judge: (indications, context) =>
      b313.judgeJoint({
        indications,
        thinnerWallIn: context.thinnerWallIn,
        service: context.service,
      }),
  },
  'AWSD1.1': {
    label: 'AWS D1.1, structural welds',
    needs: ['thicknessIn', 'loading'],
    judge: (indications, context) =>
      aws.judgeJoint({
        indications,
        thicknessIn: context.thicknessIn,
        loading: context.loading,
      }),
  },
};

export function codes() {
  return Object.keys(CODES);
}

export function describeCode(name) {
  const found = CODES[name];
  if (!found) {
    throw new Unsupported(`no acceptance code ${name}`, { asked: name, known: codes() });
  }
  return { name, label: found.label, needs: [...found.needs] };
}

/**
 * Work out what a code needs from a joint. The joint carries one wall
 * thickness and each code turns it into its own measure, which is the whole
 * reason this function exists.
 */
export function contextFor(name, joint, options = {}) {
  describeCode(name);
  if (typeof joint !== 'object' || joint === null) {
    throw new BadInput('a joint record is needed to judge against a code', { got: joint });
  }
  const wallIn = joint.wallIn;
  if (typeof wallIn !== 'number' || !(wallIn > 0)) {
    throw new BadInput('the joint carries no usable wall thickness', { wallIn });
  }

  if (name === 'API1104') {
    const weldLengthIn = options.weldLengthIn ?? round(Math.PI * joint.odIn, 4);
    if (!(weldLengthIn > 0)) {
      throw new BadInput('API 1104 needs the length of weld round the joint', { weldLengthIn });
    }
    return { wallIn, weldLengthIn };
  }

  if (name === 'AWSD1.1') {
    // Structural work is judged on the weld itself and on whether the member
    // flexes in service, which is not something the joint record knows.
    return {
      thicknessIn: round(wallIn, 4),
      loading: options.loading ?? 'static',
    };
  }

  if (name === 'ASME8') {
    // The band is read on the weld thickness with the crown taken off.
    const reinforcementIn = options.reinforcementIn ?? 0;
    return { thicknessIn: round(wallIn, 4), reinforcementIgnoredIn: reinforcementIn };
  }

  const thinnerWallIn = options.matingWallIn ? Math.min(wallIn, options.matingWallIn) : wallIn;
  return { thinnerWallIn: round(thinnerWallIn, 4), service: options.service ?? 'normal' };
}

/** Judge a joint against one code. */
export function judge({ code, indications, joint, ...options }) {
  requireArray(indications, 'indications');
  const spec = CODES[code];
  if (!spec) {
    throw new Unsupported(`no acceptance code ${code}`, { asked: code, known: codes() });
  }
  const context = contextFor(code, joint, options);
  const result = spec.judge(indications, context);
  return { ...result, context, jointId: joint.id ?? null };
}

/**
 * Judge against every code that could govern, which is what a client asks for
 * when a line is being requalified and nobody is sure yet which code applies.
 * The answers differ and that is the point of running it.
 */
export function judgeAll({ indications, joint, ...options }) {
  return codes().map((code) => {
    try {
      return judge({ code, indications, joint, ...options });
    } catch (err) {
      return { code, error: err.code ?? 'BAD_INPUT', message: err.message, disposition: null };
    }
  });
}

/**
 * The code the shop applies by default for a service. Pipeline work is judged
 * on API 1104 and everything in a plant on B31.3 unless a client names
 * Section VIII, which they do for vessel nozzle welds.
 */
export function defaultCodeFor(service) {
  if (service === 'pipeline') return 'API1104';
  if (service === 'vessel') return 'ASME8';
  if (service === 'structural') return 'AWSD1.1';
  return 'B31.3';
}
