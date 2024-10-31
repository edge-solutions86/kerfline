import { BadInput } from '../errors.js';
import { isFiniteNumber, round } from '../util/num.js';
import { requireOneOf } from '../util/guard.js';
import { requireStencil } from '../util/ids.js';
import { requireDay } from '../util/dates.js';
import { pipeSpec, outsideDiameterIn } from './schedule.js';

/**
 * A joint, as the register holds it.
 *
 * The geometry can arrive two ways: as a nominal size and schedule off the
 * drawing, or as measured outside diameter and wall off the pipe. Both are
 * accepted and both normalise to the same shape, because half the joints on a
 * job come off an isometric and the other half come off a caliper, and
 * everything downstream should not have to care which.
 */

const POSITIONS = ['1G', '2G', '5G', '6G', '1F', '2F'];
const PROCESSES = ['SMAW', 'GTAW', 'GMAW', 'FCAW', 'SAW'];
const TYPES = ['butt', 'fillet', 'branch', 'socket'];

export function weldPositions() {
  return [...POSITIONS];
}

export function weldProcesses() {
  return [...PROCESSES];
}

export function jointTypes() {
  return [...TYPES];
}

/**
 * A position is fixed when the pipe cannot be turned under the torch, which
 * is every 5G and 6G joint and no 1G one. It matters here because a fixed
 * joint is shot all the way round and a rolled one often is not.
 */
export function isFixedPosition(position) {
  requireOneOf(position, POSITIONS, 'weld position');
  return position === '5G' || position === '6G' || position === '2G';
}

export function makeJoint(input) {
  if (typeof input !== 'object' || input === null) {
    throw new BadInput('a joint must be described by an object', { got: input });
  }
  const {
    id,
    spool,
    welder,
    weldedOn,
    position = '5G',
    process = 'SMAW',
    type = 'butt',
  } = input;

  if (typeof id !== 'string' || id.trim() === '') {
    throw new BadInput('every joint needs an id', { got: id });
  }
  requireOneOf(position, POSITIONS, 'weld position');
  requireOneOf(process, PROCESSES, 'welding process');
  requireOneOf(type, TYPES, 'joint type');

  const geometry = resolveGeometry(input);

  return {
    id: id.trim(),
    spool: spool === undefined || spool === null ? null : String(spool).trim(),
    welder: welder === undefined || welder === null ? null : requireStencil(welder),
    weldedOn:
      weldedOn === undefined || weldedOn === null ? null : requireDay(weldedOn, 'weld date'),
    position,
    process,
    type,
    fixedPosition: isFixedPosition(position),
    ...geometry,
  };
}

function resolveGeometry(input) {
  const hasNominal = input.nominalSize !== undefined && input.schedule !== undefined;
  const hasMeasured = isFiniteNumber(input.odIn) && isFiniteNumber(input.wallIn);

  if (hasNominal) {
    const spec = pipeSpec(input.nominalSize, input.schedule);
    if (hasMeasured && Math.abs(spec.odIn - input.odIn) > 0.01) {
      throw new BadInput('the measured diameter does not match the size given', {
        nominalSize: input.nominalSize,
        fromTable: spec.odIn,
        measured: input.odIn,
      });
    }
    return {
      nominalSize: spec.nominalSize,
      schedule: spec.schedule,
      odIn: spec.odIn,
      wallIn: hasMeasured ? round(input.wallIn, 4) : spec.wallIn,
      fromTable: true,
    };
  }

  if (!hasMeasured) {
    throw new BadInput('a joint needs either a size and schedule or a diameter and wall', {
      got: { nominalSize: input.nominalSize, schedule: input.schedule, odIn: input.odIn },
    });
  }
  if (input.odIn <= 0 || input.wallIn <= 0) {
    throw new BadInput('measured diameter and wall must both be positive', {
      odIn: input.odIn,
      wallIn: input.wallIn,
    });
  }
  if (input.wallIn * 2 >= input.odIn) {
    throw new BadInput('two walls do not fit inside the measured diameter', {
      odIn: input.odIn,
      wallIn: input.wallIn,
    });
  }
  return {
    nominalSize: null,
    schedule: null,
    odIn: round(input.odIn, 4),
    wallIn: round(input.wallIn, 4),
    fromTable: false,
  };
}

/** The weld length round the joint, which is what coverage is measured against. */
export function weldLengthIn(joint) {
  return round(Math.PI * joint.odIn, 3);
}

/** True when the joint is small enough that the ellipse is on the table. */
export function isSmallBoreJoint(joint) {
  return joint.odIn <= 3.5;
}

/** A one line description for a register row. */
export function describeJoint(joint) {
  const size = joint.nominalSize
    ? `${joint.nominalSize} in sch ${joint.schedule}`
    : `${joint.odIn} in OD x ${joint.wallIn} wall`;
  const welder = joint.welder ? ` welder ${joint.welder}` : '';
  return `${joint.id} ${size} ${joint.position} ${joint.process}${welder}`;
}

/** Look a nominal size up without building a whole joint. */
export function diameterForSize(size) {
  return outsideDiameterIn(size);
}
