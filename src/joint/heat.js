import { BadInput, NotFound } from '../errors.js';
import { requireArray, requireLabel, requireOneOf } from '../util/guard.js';
import { requireDay } from '../util/dates.js';
import { compareIds } from '../util/ids.js';
import { material, materialIds, governingMaterial, equivalentSteelIn } from './material.js';

/**
 * Heat numbers and where they went.
 *
 * Every length of pipe carries a heat number stamped on it, and the mill test
 * report for that heat is what says the metal is what the drawing asked for.
 * A joint welded between two heats belongs to both, and if either mill report
 * is missing then the joint is not traceable however good the radiograph is.
 *
 * The reason this sits next to the radiography rather than off in a
 * procurement system is that the heat decides the material, and the material
 * decides the steel thickness the exposure chart is read at. A joint recorded
 * against the wrong heat gets an exposure worked out for the wrong metal.
 */

export function makeHeat({
  number,
  materialId,
  receivedOn,
  millReport = null,
  supplier = null,
}) {
  requireLabel(number, 'a heat is identified by its number');
  requireOneOf(materialId, materialIds(), 'material');
  requireDay(receivedOn, 'received date');
  return {
    number: number.trim(),
    materialId,
    material: material(materialId).label,
    receivedOn,
    millReport,
    supplier,
    traceable: millReport !== null,
  };
}

export function heatOf(heats, number) {
  requireArray(heats, 'heats');
  const found = heats.find((heat) => heat.number === number);
  if (!found) {
    throw new NotFound(`no heat ${number} on file`, {
      asked: number,
      known: heats.map((heat) => heat.number),
    });
  }
  return found;
}

/** The heats a joint was welded between. A butt joint has one on each side. */
export function assignHeats(joint, { upstream, downstream = null }) {
  requireLabel(upstream, 'a joint names the heat on the upstream side');
  return {
    ...joint,
    heats: downstream === null ? [upstream.trim()] : [upstream.trim(), downstream.trim()],
  };
}

/**
 * Trace a joint back to its metal. Reports the governing material, which is
 * the harder of the two when a joint joins dissimilar heats, because that is
 * what the exposure has to reach through.
 */
export function traceJoint(joint, heats) {
  requireArray(heats, 'heats');
  const numbers = joint.heats ?? [];
  if (numbers.length === 0) {
    return {
      jointId: joint.id,
      traceable: false,
      reason: 'no heat recorded against the joint',
    };
  }

  const found = numbers.map((number) => heatOf(heats, number));
  const missing = found.filter((heat) => !heat.traceable);
  const governing =
    found.length === 1
      ? material(found[0].materialId)
      : governingMaterial(found[0].materialId, found[1].materialId);

  return {
    jointId: joint.id,
    heats: found.map((heat) => heat.number),
    materials: [...new Set(found.map((heat) => heat.materialId))],
    dissimilar: new Set(found.map((heat) => heat.materialId)).size > 1,
    governingMaterial: governing.id,
    equivalentSteelIn: equivalentSteelIn(governing.id, joint.wallIn),
    traceable: missing.length === 0,
    reason:
      missing.length === 0
        ? null
        : `no mill report for heat ${missing.map((heat) => heat.number).join(', ')}`,
  };
}

/** Heats received with no mill report, which is a hold on everything welded from them. */
export function untraceableHeats(heats) {
  requireArray(heats, 'heats');
  return heats.filter((heat) => !heat.traceable).sort((a, b) => compareIds(a.number, b.number));
}

/** Joints that cannot be traced, with the reason each one cannot. */
export function untraceableJoints(joints, heats) {
  requireArray(joints, 'joints');
  return joints
    .map((joint) => traceJoint(joint, heats))
    .filter((trace) => !trace.traceable)
    .sort((a, b) => compareIds(a.jointId, b.jointId));
}

/** Every joint a heat went into, which is the question asked after a recall. */
export function jointsFromHeat(joints, number) {
  requireArray(joints, 'joints');
  if (typeof number !== 'string') {
    throw new BadInput('a heat is looked up by its number', { got: number });
  }
  return joints
    .filter((joint) => (joint.heats ?? []).includes(number))
    .map((joint) => joint.id)
    .sort(compareIds);
}
