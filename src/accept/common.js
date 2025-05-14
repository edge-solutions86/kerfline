import { round } from '../util/num.js';
import { requireArray, requireAbove } from '../util/guard.js';
import { governingSizeIn } from '../indication/measure.js';
import { makeVerdict, accept, combine } from './verdict.js';

/**
 * The parts every acceptance code turned out to share.
 *
 * Four codes were written before this file existed and all four had grown the
 * same three shapes: reject anything planar, compare a size against a cap, and
 * roll a list of verdicts up into one answer with an acceptance standing in
 * when the list is empty. The wording of the reasons had already started to
 * drift between them, which is the point at which shared code stops being a
 * tidiness question.
 *
 * What is deliberately NOT here is any of the numbers. Every cap, fraction,
 * window and exception stays in the code module it belongs to, because that is
 * the part that differs and the part somebody has to be able to check against
 * a printed standard.
 */

/** Planar means a crack or an unfused face, and no code tolerates one. */
export function rejectPlanar({ code, clause, indication }) {
  return makeVerdict({
    disposition: 'reject',
    code,
    clause,
    reason: `${indication.label} is not permitted at any size`,
    indication,
    measured: governingSizeIn(indication),
  });
}

/**
 * Compare a size against a cap. Inclusive at the cap, because every code in
 * this package writes its limits as "shall not exceed".
 */
export function capVerdict({ code, clause, indication, sizeIn, capIn, what, units = 'in' }) {
  if (sizeIn <= capIn) return accept(code, clause);
  return makeVerdict({
    disposition: 'repair',
    code,
    clause,
    reason: `${what} ${round(sizeIn, 4)} ${units}, cap is ${round(capIn, 4)} ${units}`,
    indication: indication ?? null,
    measured: round(sizeIn, 4),
  });
}

/**
 * The heaviest window of a given length. Returns the total and where it
 * started, so a report can say which foot of weld was the problem rather than
 * only that the joint failed.
 */
export function windowTotal(indications, { windowIn, sizeOf = governingSizeIn }) {
  requireArray(indications, 'indications');
  requireAbove(windowIn, 0, 'window length');
  const placed = indications.filter((indication) => typeof indication.at === 'number');
  if (placed.length === 0) return { totalIn: 0, startIn: 0, members: [] };

  let best = { totalIn: 0, startIn: placed[0].at, members: [] };
  for (const anchor of placed) {
    const members = placed.filter(
      (other) => other.at >= anchor.at && other.at < anchor.at + windowIn
    );
    const totalIn = round(
      members.reduce((sum, other) => sum + sizeOf(other), 0),
      4
    );
    if (totalIn > best.totalIn) best = { totalIn, startIn: round(anchor.at, 4), members };
  }
  return best;
}

/**
 * Roll a set of verdicts into the joint answer. An empty set means nothing
 * was found rather than nothing was checked, so it settles as an acceptance
 * against the clause that did the checking.
 */
export function settle(verdicts, { code, clause }) {
  requireArray(verdicts, 'verdicts');
  const all = verdicts.length === 0 ? [accept(code, clause)] : verdicts;
  return combine(all, { code });
}
