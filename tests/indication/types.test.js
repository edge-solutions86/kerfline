import { describe, it, expect } from 'vitest';
import {
  indicationTypes,
  indicationTypeIds,
  indicationType,
  isIndicationType,
  isPlanar,
  planarTypes,
  measuredBy,
  rootOnlyTypes,
  isRootOnly,
} from '../../src/indication/types.js';

describe('the taxonomy', () => {
  it('carries the eleven types the shop reports', () => {
    expect(indicationTypeIds()).toHaveLength(11);
    expect(indicationTypeIds()).toContain('CRACK');
    expect(indicationTypeIds()).toContain('HOLLOW_BEAD');
  });

  it('gives every type a measure and a surface', () => {
    for (const type of indicationTypes()) {
      expect(['length', 'diameter']).toContain(type.measure);
      expect(['either', 'root', 'internal']).toContain(type.surface);
      expect(typeof type.planar).toBe('boolean');
    }
  });

  it('finds a type however it was written', () => {
    expect(indicationType('crack').id).toBe('CRACK');
    expect(indicationType('hollow bead').id).toBe('HOLLOW_BEAD');
    expect(indicationType('hollow-bead').id).toBe('HOLLOW_BEAD');
    expect(indicationType(' POROSITY ').id).toBe('POROSITY');
  });

  it('names what it knows when it cannot find one', () => {
    expect(() => indicationType('LAMINATION')).toThrow(/no indication type LAMINATION/);
    expect(() => indicationType(null)).toThrow(/named by a string/);
  });

  it('answers isIndicationType without throwing', () => {
    expect(isIndicationType('crack')).toBe(true);
    expect(isIndicationType('lamination')).toBe(false);
  });

  it('freezes a record', () => {
    expect(() => {
      indicationType('CRACK').planar = false;
    }).toThrow();
  });
});

describe('the planar flag', () => {
  it('marks the three that no code tolerates', () => {
    expect(isPlanar('CRACK')).toBe(true);
    expect(isPlanar('IF')).toBe(true);
    expect(isPlanar('IP')).toBe(true);
  });

  it('leaves the volumetric ones alone', () => {
    expect(isPlanar('POROSITY')).toBe(false);
    expect(isPlanar('SLAG')).toBe(false);
    expect(isPlanar('UNDERCUT')).toBe(false);
  });

  it('lists exactly the planar types', () => {
    expect(planarTypes().map((t) => t.id)).toEqual(['CRACK', 'IF', 'IP']);
  });
});

describe('measuredBy', () => {
  it('judges round things on their diameter', () => {
    expect(measuredBy('POROSITY')).toBe('diameter');
    expect(measuredBy('TUNGSTEN')).toBe('diameter');
    expect(measuredBy('CLUSTER')).toBe('diameter');
  });

  it('judges everything else on its length', () => {
    expect(measuredBy('SLAG')).toBe('length');
    expect(measuredBy('CRACK')).toBe('length');
    expect(measuredBy('UNDERCUT')).toBe('length');
  });

  it('gives every planar type a length', () => {
    for (const type of planarTypes()) {
      expect(type.measure).toBe('length');
    }
  });
});

describe('the root only types', () => {
  it('lists the defects that can only be in the root', () => {
    const ids = rootOnlyTypes().map((t) => t.id);
    expect(ids).toContain('IP');
    expect(ids).toContain('HOLLOW_BEAD');
    expect(ids).toContain('BURN_THROUGH');
    expect(ids).toContain('ROOT_CONCAVITY');
  });

  it('does not put a crack in the root only list', () => {
    expect(isRootOnly('CRACK')).toBe(false);
    expect(isRootOnly('POROSITY')).toBe(false);
  });

  it('answers for a single type', () => {
    expect(isRootOnly('IP')).toBe(true);
    expect(isRootOnly('SLAG')).toBe(false);
  });
});
