import { round, maxOf } from '../util/num.js';
import { requireArray, requireAbove } from '../util/guard.js';
import { governingSizeIn } from './measure.js';

/**
 * Grouping rounded indications.
 *
 * Scattered porosity and cluster porosity are different findings with
 * different limits, and the only thing separating them is how close the pores
 * are to each other. So the grouping has to happen before anything is judged,
 * and it has to be done the same way every time, which is why it is here and
 * not in the reader's head.
 *
 * The grouping is single linkage: two pores are in the same cluster if they
 * are within the gap of each other, and a chain of pores each within the gap
 * of the next is one cluster however long the chain gets. That is the right
 * behaviour, because a run of pores along a weld is one defect even when the
 * ends of it are inches apart.
 */

const DEFAULT_GAP_IN = 0.125;

export function defaultGapIn() {
  return DEFAULT_GAP_IN;
}

function positionOf(indication) {
  return { at: indication.at ?? 0, across: indication.across ?? 0 };
}

/** Edge to edge distance between two indications, floored at zero. */
export function separationIn(a, b) {
  const first = positionOf(a);
  const second = positionOf(b);
  const along = second.at - first.at;
  const across = second.across - first.across;
  const centres = Math.sqrt(along * along + across * across);
  const radii = (governingSizeIn(a) + governingSizeIn(b)) / 2;
  return round(Math.max(0, centres - radii), 4);
}

/**
 * Group by single linkage. Indications with no position are left out: a pore
 * nobody located cannot be grouped with anything, and guessing it into a
 * cluster would invent a defect.
 */
export function clusterOf(indications, gapIn = DEFAULT_GAP_IN) {
  requireArray(indications, 'indications');
  requireAbove(gapIn, 0, 'gap');
  const placed = indications.filter((indication) => typeof indication.at === 'number');
  const groups = [];
  const taken = new Set();

  for (let i = 0; i < placed.length; i += 1) {
    if (taken.has(i)) continue;
    const group = [i];
    taken.add(i);
    // Walk the chain outward until nothing new comes within the gap.
    for (let cursor = 0; cursor < group.length; cursor += 1) {
      for (let j = 0; j < placed.length; j += 1) {
        if (taken.has(j)) continue;
        if (separationIn(placed[group[cursor]], placed[j]) <= gapIn) {
          group.push(j);
          taken.add(j);
        }
      }
    }
    groups.push(group.map((index) => placed[index]).sort((a, b) => a.at - b.at));
  }
  return groups.sort((a, b) => a[0].at - b[0].at);
}

/** The extent of a group, along the weld and across it. */
export function clusterExtent(group) {
  requireArray(group, 'group');
  if (group.length === 0) {
    return { alongIn: 0, acrossIn: 0, diameterIn: 0, count: 0 };
  }
  const positions = group.map((indication) => positionOf(indication));
  const sizes = group.map((indication) => governingSizeIn(indication) / 2);
  const lows = positions.map((position, i) => position.at - sizes[i]);
  const highs = positions.map((position, i) => position.at + sizes[i]);
  const nears = positions.map((position, i) => position.across - sizes[i]);
  const fars = positions.map((position, i) => position.across + sizes[i]);
  const alongIn = round(maxOf(highs) - Math.min(...lows), 4);
  const acrossIn = round(maxOf(fars) - Math.min(...nears), 4);
  return {
    alongIn,
    acrossIn,
    diameterIn: round(Math.sqrt(alongIn * alongIn + acrossIn * acrossIn), 4),
    count: group.length,
  };
}

/** A group of two or more is a cluster; anything else stays scattered. */
export function clusters(indications, gapIn = DEFAULT_GAP_IN) {
  return clusterOf(indications, gapIn).filter((group) => group.length > 1);
}

export function scattered(indications, gapIn = DEFAULT_GAP_IN) {
  return clusterOf(indications, gapIn)
    .filter((group) => group.length === 1)
    .map((group) => group[0]);
}

/**
 * Turn a group into the single indication a code paragraph will judge. The
 * members are kept on it, because a reader asked to justify a cluster call
 * has to be able to point at the pores it was made from.
 */
export function asCluster(group) {
  requireArray(group, 'group');
  if (group.length === 0) {
    return null;
  }
  const extent = clusterExtent(group);
  const first = group[0];
  return {
    type: 'CLUSTER',
    label: 'cluster porosity',
    planar: false,
    measure: 'diameter',
    measuredIn: extent.diameterIn,
    magnification: first.magnification ?? 1,
    sizeIn: extent.diameterIn,
    widthIn: extent.acrossIn,
    at: first.at ?? null,
    film: first.film ?? null,
    note: `${group.length} pores`,
    members: group,
  };
}

/**
 * Reduce a set of indications to what gets reported: clusters as single
 * cluster indications, everything else as it stands.
 */
export function reportable(indications, gapIn = DEFAULT_GAP_IN) {
  const grouped = clusterOf(indications, gapIn);
  const unplaced = indications.filter((indication) => typeof indication.at !== 'number');
  return [
    ...grouped.map((group) => (group.length > 1 ? asCluster(group) : group[0])),
    ...unplaced,
  ];
}
