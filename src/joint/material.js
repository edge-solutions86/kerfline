import { BadInput, NotFound } from '../errors.js';
import { round } from '../util/num.js';
import { requireAbove } from '../util/guard.js';

/**
 * What the pipe is made of, and the two places it changes an answer.
 *
 * The first is the exposure. Every chart in this package is a steel chart, so
 * anything that is not steel has to be turned into the steel thickness that
 * absorbs the same amount. An inch of aluminium behaves like about a fifth of
 * an inch of steel, and reading an aluminium job straight off the steel chart
 * gives an exposure five times longer than it needs and a film like a sheet
 * of coal.
 *
 * The second is the indicator. An indicator has to be radiographically
 * similar to what it is standing in for, so a steel plaque on an aluminium
 * weld shows its hole far too easily and reports a sensitivity that weld
 * never had. The group numbers below are what "similar" means in practice.
 */

const MATERIALS = {
  CARBON_STEEL: {
    label: 'carbon steel',
    pNumber: 'P1',
    iqiGroup: '02',
    equivalence: 1,
  },
  LOW_ALLOY: {
    label: 'low alloy steel',
    pNumber: 'P4',
    iqiGroup: '02',
    equivalence: 1,
  },
  STAINLESS: {
    label: 'austenitic stainless',
    pNumber: 'P8',
    iqiGroup: '02',
    equivalence: 1.12,
  },
  INCONEL: {
    label: 'nickel alloy',
    pNumber: 'P43',
    iqiGroup: '03',
    equivalence: 1.3,
  },
  COPPER: {
    label: 'copper',
    pNumber: 'P31',
    iqiGroup: '03',
    equivalence: 1.5,
  },
  ALUMINIUM: {
    label: 'aluminium',
    pNumber: 'P21',
    iqiGroup: '01',
    equivalence: 0.18,
  },
  TITANIUM: {
    label: 'titanium',
    pNumber: 'P51',
    iqiGroup: '01',
    equivalence: 0.63,
  },
};

// Which indicator groups may stand in for which material groups. A group may
// always use its own, and the harder groups accept nothing softer.
const ACCEPTS = {
  '01': ['01'],
  '02': ['02'],
  '03': ['03', '02'],
};

export function materialIds() {
  return Object.keys(MATERIALS);
}

export function material(id) {
  if (typeof id !== 'string') {
    throw new BadInput('a material must be named by a string', { got: id });
  }
  const key = id.trim().toUpperCase().replace(/[\s-]/g, '_');
  const found = MATERIALS[key];
  if (!found) {
    throw new NotFound(`no material ${id} on the list`, { asked: id, known: materialIds() });
  }
  return { id: key, ...found };
}

export function iqiGroups() {
  return Object.keys(ACCEPTS).sort();
}

/** The steel thickness that absorbs the same as this section. */
export function equivalentSteelIn(materialId, thicknessIn) {
  requireAbove(thicknessIn, 0, 'thickness');
  return round(thicknessIn * material(materialId).equivalence, 4);
}

/** The other way: what thickness of a material a steel reading stands for. */
export function fromEquivalentSteelIn(materialId, steelIn) {
  requireAbove(steelIn, 0, 'steel thickness');
  return round(steelIn / material(materialId).equivalence, 4);
}

/**
 * Whether an indicator of one material may be used on another. Same group
 * always; a harder group may stand in for a softer one on a job where the
 * right plaque is not in the van, but never the other way round, because a
 * softer plaque shows too easily and flatters the film.
 */
export function indicatorAcceptable({ weldMaterial, indicatorMaterial }) {
  const weld = material(weldMaterial);
  const indicator = material(indicatorMaterial);
  const accepted = ACCEPTS[weld.iqiGroup] ?? [];
  return {
    weldGroup: weld.iqiGroup,
    indicatorGroup: indicator.iqiGroup,
    acceptable: accepted.includes(indicator.iqiGroup),
    sameGroup: weld.iqiGroup === indicator.iqiGroup,
  };
}

/** The materials whose indicators will serve for a weld. */
export function indicatorsFor(weldMaterial) {
  const weld = material(weldMaterial);
  const accepted = ACCEPTS[weld.iqiGroup] ?? [];
  return materialIds().filter((id) => accepted.includes(MATERIALS[id].iqiGroup));
}

/**
 * Two materials welded together. The exposure has to reach through the harder
 * of the two, and the indicator is chosen on that one as well.
 */
export function governingMaterial(a, b) {
  const first = material(a);
  const second = material(b);
  return first.equivalence >= second.equivalence ? first : second;
}
