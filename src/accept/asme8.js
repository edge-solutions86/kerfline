import { round } from '../util/num.js';
import { bandTable, bandValue } from '../util/band.js';
import { requireArray, requireAbove } from '../util/guard.js';
import { governingSizeIn } from '../indication/measure.js';
import { makeVerdict, accept } from './verdict.js';
import { rejectPlanar, settle } from './common.js';

/**
 * ASME Section VIII Division 1, UW-51, for fully radiographed pressure welds.
 *
 * Two things here are worth reading twice.
 *
 * The first is that the length limit for an elongated indication is banded on
 * thickness and the middle band is a fraction of the thickness rather than a
 * fixed number. So the limit is 1/4 in on thin work, then climbs with the
 * plate through the middle band, then stops climbing at 3/4 in. A limit that
 * is constant, then proportional, then constant again catches people out at
 * both corners.
 *
 * The second is the aligned group rule. A run of small indications in a line
 * is judged as one, with the total capped at t in any 12t of weld, but only
 * while they are close together. Once the gap between two of them is more
 * than six times the longest one in the group, the group is not a group any
 * more and the run is split there. That exception is what stops a code
 * written for aligned porosity from condemning a weld with two unrelated
 * specks at opposite ends.
 */

const CODE = 'ASME8';
const CLAUSE = 'UW-51(b)';

const LENGTH_LIMIT = bandTable(
  [
    { upTo: 0.75, value: 'quarter' },
    { upTo: 2.25, value: 'third' },
    { upTo: Infinity, value: 'threequarter' },
  ],
  { name: 'UW-51 length limit' }
);

const GROUP_WINDOW_MULTIPLE = 12;
const GAP_MULTIPLE = 6;

export function codeName() {
  return CODE;
}

/** The cap on a single elongated indication, banded on the weld thickness. */
export function elongatedLimitIn(thicknessIn) {
  requireAbove(thicknessIn, 0, 'weld thickness');
  const band = bandValue(LENGTH_LIMIT, thicknessIn);
  if (band === 'quarter') return 0.25;
  if (band === 'third') return round(thicknessIn / 3, 4);
  return 0.75;
}

export function limitBands() {
  return LENGTH_LIMIT.rows.map((row) => ({ over: row.over, upTo: row.upTo, rule: row.value }));
}

/**
 * Split indications into aligned groups. Successive ones stay in the same
 * group while the gap between them is no more than six times the longest
 * indication in the group so far.
 */
export function alignedGroups(indications) {
  requireArray(indications, 'indications');
  const placed = indications
    .filter((indication) => typeof indication.at === 'number')
    .slice()
    .sort((a, b) => a.at - b.at);
  if (placed.length === 0) return [];

  const groups = [[placed[0]]];
  for (let i = 1; i < placed.length; i += 1) {
    const current = groups[groups.length - 1];
    const previous = current[current.length - 1];
    const longest = Math.max(...current.map(governingSizeIn));
    const gap = placed[i].at - (previous.at + governingSizeIn(previous));
    if (gap > GAP_MULTIPLE * longest) {
      groups.push([placed[i]]);
    } else {
      current.push(placed[i]);
    }
  }
  return groups;
}

/** Any crack, lack of fusion or lack of penetration takes the weld out. */
export function judgePlanar(indication) {
  if (!indication.planar) return accept(CODE, CLAUSE);
  return rejectPlanar({ code: CODE, clause: `${CLAUSE}(1)`, indication });
}

export function judgeElongated(indication, thicknessIn) {
  if (indication.planar) return judgePlanar(indication);
  if (indication.measure !== 'length') return accept(CODE, CLAUSE);
  const limit = elongatedLimitIn(thicknessIn);
  const size = governingSizeIn(indication);
  if (size <= limit) return accept(CODE, `${CLAUSE}(2)`);
  return makeVerdict({
    disposition: 'repair',
    code: CODE,
    clause: `${CLAUSE}(2)`,
    reason: `${indication.label} ${round(size, 3)} in long, cap is ${limit} in`,
    indication,
    measured: size,
  });
}

/**
 * The aligned group rule. Each group is capped at the weld thickness in any
 * twelve thicknesses of weld.
 */
export function judgeGroups(indications, thicknessIn) {
  requireAbove(thicknessIn, 0, 'weld thickness');
  const window = GROUP_WINDOW_MULTIPLE * thicknessIn;
  return alignedGroups(indications)
    .map((group) => {
      const total = round(
        group.reduce((sum, indication) => sum + governingSizeIn(indication), 0),
        4
      );
      const span =
        group[group.length - 1].at + governingSizeIn(group[group.length - 1]) - group[0].at;
      if (span > window || total <= thicknessIn) return null;
      return makeVerdict({
        disposition: 'repair',
        code: CODE,
        clause: `${CLAUSE}(3)`,
        reason: `aligned group totals ${total} in in ${round(window, 3)} in of weld, cap is ${round(thicknessIn, 3)} in`,
        measured: total,
      });
    })
    .filter((verdict) => verdict !== null);
}

export function judgeJoint({ indications, thicknessIn }) {
  requireArray(indications, 'indications');
  requireAbove(thicknessIn, 0, 'weld thickness');
  const single = indications.map((indication) => judgeElongated(indication, thicknessIn));
  const grouped = judgeGroups(
    indications.filter((indication) => !indication.planar),
    thicknessIn
  );
  return settle([...single, ...grouped], { code: CODE, clause: CLAUSE });
}
