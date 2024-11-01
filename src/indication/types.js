import { BadInput, NotFound } from '../errors.js';

/**
 * The indication taxonomy.
 *
 * Two things about this list carry real weight downstream. The first is the
 * planar flag: a planar indication is a crack or a lack of fusion, something
 * with two faces that have not joined, and every acceptance code in this
 * package rejects those outright regardless of size. Everything else is
 * rounded or elongated and gets judged on dimensions.
 *
 * The second is whether the indication is measured by its length or by its
 * diameter. Porosity is judged on the size of the pore and on how much of it
 * there is in a given run of weld; an elongated slag line is judged on how
 * long it is. Mixing those up produces a verdict that is not wrong so much as
 * about a different weld.
 */

const TYPES = [
  {
    id: 'CRACK',
    label: 'crack',
    planar: true,
    measure: 'length',
    surface: 'either',
    note: 'rejected by every code in this package whatever its size',
  },
  {
    id: 'IF',
    label: 'incomplete fusion',
    planar: true,
    measure: 'length',
    surface: 'either',
    note: 'the weld metal did not fuse to the side wall or to the previous pass',
  },
  {
    id: 'IP',
    label: 'incomplete penetration',
    planar: true,
    measure: 'length',
    surface: 'root',
    note: 'the root of the joint was not filled',
  },
  {
    id: 'SLAG',
    label: 'slag inclusion',
    planar: false,
    measure: 'length',
    surface: 'internal',
    note: 'trapped flux, elongated along the weld or isolated',
  },
  {
    id: 'TUNGSTEN',
    label: 'tungsten inclusion',
    planar: false,
    measure: 'diameter',
    surface: 'internal',
    note: 'shows lighter than the weld rather than darker, which is the giveaway',
  },
  {
    id: 'POROSITY',
    label: 'porosity',
    planar: false,
    measure: 'diameter',
    surface: 'internal',
    note: 'gas trapped in the solidifying weld, round and usually scattered',
  },
  {
    id: 'CLUSTER',
    label: 'cluster porosity',
    planar: false,
    measure: 'diameter',
    surface: 'internal',
    note: 'pores grouped closely enough to be judged as one indication',
  },
  {
    id: 'HOLLOW_BEAD',
    label: 'hollow bead',
    planar: false,
    measure: 'length',
    surface: 'root',
    note: 'elongated gas in the root pass, a pipeline defect above all',
  },
  {
    id: 'UNDERCUT',
    label: 'undercut',
    planar: false,
    measure: 'length',
    surface: 'either',
    note: 'the weld toe has eaten into the base metal',
  },
  {
    id: 'BURN_THROUGH',
    label: 'burn through',
    planar: false,
    measure: 'length',
    surface: 'root',
    note: 'the root was melted away and the weld metal dropped through',
  },
  {
    id: 'ROOT_CONCAVITY',
    label: 'root concavity',
    planar: false,
    measure: 'length',
    surface: 'root',
    note: 'the root is sound but sucked back below the pipe bore',
  },
];

const BY_ID = new Map(TYPES.map((t) => [t.id, Object.freeze(t)]));

export function indicationTypes() {
  return [...BY_ID.values()];
}

export function indicationTypeIds() {
  return [...BY_ID.keys()];
}

export function indicationType(id) {
  if (typeof id !== 'string') {
    throw new BadInput('an indication type must be named by a string', { got: id });
  }
  const key = id.trim().toUpperCase().replace(/[\s-]/g, '_');
  const found = BY_ID.get(key);
  if (!found) {
    throw new NotFound(`no indication type ${id}`, { asked: id, known: indicationTypeIds() });
  }
  return found;
}

export function isIndicationType(id) {
  try {
    indicationType(id);
    return true;
  } catch {
    return false;
  }
}

/** Planar indications are the ones no code tolerates at any size. */
export function isPlanar(id) {
  return indicationType(id).planar;
}

export function planarTypes() {
  return indicationTypes().filter((t) => t.planar);
}

/** Whether the code limit for this type is read against a length or a size. */
export function measuredBy(id) {
  return indicationType(id).measure;
}

/** Indications that can only occur at the root of the joint. */
export function rootOnlyTypes() {
  return indicationTypes().filter((t) => t.surface === 'root');
}

export function isRootOnly(id) {
  return indicationType(id).surface === 'root';
}
