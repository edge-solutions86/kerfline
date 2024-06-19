import { NotFound, BadInput } from '../errors.js';

/**
 * The gamma sources this shop is licensed for, plus the X-ray head.
 *
 * halfLifeDays, the principal gamma energies and the gamma ray constant are
 * physical constants and are quoted from the source certificates. The steel
 * range is not: it is the practical band this shop works the source in, taken
 * off the technique procedure, and it is deliberately narrower than what the
 * source can physically penetrate. A technique outside that band is not wrong,
 * it just needs a supervisor to sign it.
 */

const SOURCES = [
  {
    id: 'IR192',
    label: 'Ir-192',
    element: 'iridium',
    massNumber: 192,
    kind: 'gamma',
    halfLifeDays: 73.83,
    gammaFactor: 0.48, // R/h at one metre per curie
    energiesMeV: [0.296, 0.308, 0.316, 0.468, 0.604],
    meanEnergyMeV: 0.38,
    steelRangeIn: { min: 0.25, max: 2.5 },
  },
  {
    id: 'SE75',
    label: 'Se-75',
    element: 'selenium',
    massNumber: 75,
    kind: 'gamma',
    halfLifeDays: 119.78,
    gammaFactor: 0.203,
    energiesMeV: [0.121, 0.136, 0.265, 0.28, 0.401],
    meanEnergyMeV: 0.217,
    steelRangeIn: { min: 0.2, max: 1.1 },
  },
  {
    id: 'CO60',
    label: 'Co-60',
    element: 'cobalt',
    massNumber: 60,
    kind: 'gamma',
    halfLifeDays: 1925.3,
    gammaFactor: 1.32,
    energiesMeV: [1.173, 1.332],
    meanEnergyMeV: 1.25,
    steelRangeIn: { min: 1.5, max: 9 },
  },
  {
    id: 'YB169',
    label: 'Yb-169',
    element: 'ytterbium',
    massNumber: 169,
    kind: 'gamma',
    halfLifeDays: 32.02,
    gammaFactor: 0.125,
    energiesMeV: [0.063, 0.093, 0.109, 0.131, 0.198],
    meanEnergyMeV: 0.093,
    steelRangeIn: { min: 0.05, max: 0.5 },
  },
  {
    id: 'TM170',
    label: 'Tm-170',
    element: 'thulium',
    massNumber: 170,
    kind: 'gamma',
    halfLifeDays: 128.6,
    gammaFactor: 0.0025,
    energiesMeV: [0.084],
    meanEnergyMeV: 0.084,
    steelRangeIn: { min: 0.01, max: 0.25 },
  },
];

const BY_ID = new Map(SOURCES.map((s) => [s.id, Object.freeze(deepFreeze(s))]));

function deepFreeze(source) {
  Object.freeze(source.energiesMeV);
  Object.freeze(source.steelRangeIn);
  return source;
}

/** Every gamma source, in the order the licence lists them. */
export function isotopes() {
  return [...BY_ID.values()];
}

export function isotopeIds() {
  return [...BY_ID.keys()];
}

/**
 * Look a source up by id or by the label a technician writes on the form.
 * "ir-192", "IR192" and "Ir 192" all reach the same record, because all three
 * turn up on paperwork.
 */
export function isotope(idOrLabel) {
  if (typeof idOrLabel !== 'string') {
    throw new BadInput('isotope must be named by a string', { got: idOrLabel });
  }
  const key = normaliseId(idOrLabel);
  const found = BY_ID.get(key);
  if (!found) {
    throw new NotFound(`no isotope ${idOrLabel} in the catalog`, {
      asked: idOrLabel,
      known: isotopeIds(),
    });
  }
  return found;
}

export function normaliseId(text) {
  return String(text)
    .toUpperCase()
    .replace(/[\s\-_]/g, '');
}

export function hasIsotope(idOrLabel) {
  if (typeof idOrLabel !== 'string') return false;
  return BY_ID.has(normaliseId(idOrLabel));
}

/** True when the wall sits inside the shop band for that source. */
export function withinSteelRange(idOrLabel, thicknessIn) {
  const source = isotope(idOrLabel);
  if (typeof thicknessIn !== 'number' || !Number.isFinite(thicknessIn)) {
    throw new BadInput('thickness must be a finite number', { got: thicknessIn });
  }
  return thicknessIn >= source.steelRangeIn.min && thicknessIn <= source.steelRangeIn.max;
}

/**
 * The sources that will cover a wall, hardest first. Hardness is the mean
 * energy, so a thick wall lands on cobalt and a thin one on ytterbium.
 */
export function sourcesForThickness(thicknessIn) {
  return isotopes()
    .filter((s) => withinSteelRange(s.id, thicknessIn))
    .sort((a, b) => b.meanEnergyMeV - a.meanEnergyMeV);
}
