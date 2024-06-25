import { BadInput, NotFound } from '../errors.js';
import { isotope, normaliseId } from './catalog.js';
import { isFiniteNumber } from '../util/num.js';
import { convert } from '../util/units.js';

/**
 * How much of a beam a given material stops. Two jobs lean on this: working
 * out the barricade distance behind a shield, and sanity checking an exposure
 * against the wall it has to get through.
 *
 * Half value layers are quoted in millimetres because that is how the
 * published tables print them. The narrow beam values here are the ones to
 * use for a technique; a broad beam survey needs a build up factor on top and
 * this module deliberately does not pretend to supply one.
 */

const HVL_MM = {
  IR192: { lead: 4.8, steel: 12.7, concrete: 44 },
  SE75: { lead: 2.0, steel: 8.9, concrete: 33 },
  CO60: { lead: 12.5, steel: 21.6, concrete: 62 },
  YB169: { lead: 0.9, steel: 4.0, concrete: 20 },
  TM170: { lead: 0.2, steel: 2.0, concrete: 12 },
};

const LOG2_10 = Math.log2(10);

export function shieldMaterials() {
  return ['concrete', 'lead', 'steel'];
}

/** Half value layer in millimetres for a source and a shield material. */
export function halfValueLayerMm(idOrLabel, material) {
  const source = isotope(idOrLabel);
  const row = HVL_MM[normaliseId(source.id)];
  if (!row || !Object.prototype.hasOwnProperty.call(row, material)) {
    throw new NotFound(`no half value layer for ${source.label} in ${material}`, {
      isotope: source.id,
      material,
      known: shieldMaterials(),
    });
  }
  return row[material];
}

export function halfValueLayerIn(idOrLabel, material) {
  return convert(halfValueLayerMm(idOrLabel, material), 'mm', 'in');
}

/** Tenth value layer, which is the half value layer times log two of ten. */
export function tenthValueLayerMm(idOrLabel, material) {
  return halfValueLayerMm(idOrLabel, material) * LOG2_10;
}

/** The fraction of the beam that gets through a shield of that thickness. */
export function transmission(idOrLabel, material, thicknessMm) {
  requireFinite(thicknessMm, 'shield thickness');
  if (thicknessMm < 0) {
    throw new BadInput('shield thickness must not be negative', { thicknessMm });
  }
  const hvl = halfValueLayerMm(idOrLabel, material);
  return Math.pow(2, -thicknessMm / hvl);
}

/** The shield thickness that cuts the beam by the given factor. */
export function shieldForFactor(idOrLabel, material, factor) {
  requireFinite(factor, 'reduction factor');
  if (factor < 1) {
    throw new BadInput('a reduction factor below one is not a shield', { factor });
  }
  return halfValueLayerMm(idOrLabel, material) * Math.log2(factor);
}

/** The shield thickness that takes a reading down to a target reading. */
export function shieldForReading(idOrLabel, material, fromRate, toRate) {
  requireFinite(fromRate, 'unshielded rate');
  requireFinite(toRate, 'target rate');
  if (fromRate <= 0 || toRate <= 0) {
    throw new BadInput('both readings must be above zero', { fromRate, toRate });
  }
  if (toRate > fromRate) {
    throw new BadInput('the target reading is above the unshielded one', { fromRate, toRate });
  }
  return shieldForFactor(idOrLabel, material, fromRate / toRate);
}

/**
 * How many half value layers of steel the wall itself represents. A wall much
 * past about six is going to want a harder source or a very long exposure,
 * and the technique reviewer looks at this number first.
 */
export function wallInHalfValueLayers(idOrLabel, wallIn) {
  requireFinite(wallIn, 'wall thickness');
  if (wallIn <= 0) throw new BadInput('wall thickness must be above zero', { wallIn });
  return wallIn / halfValueLayerIn(idOrLabel, 'steel');
}

function requireFinite(value, what) {
  if (!isFiniteNumber(value)) {
    throw new BadInput(`${what} must be a finite number`, { got: value });
  }
  return value;
}
