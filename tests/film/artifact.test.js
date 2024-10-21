import { describe, it, expect } from 'vitest';
import {
  artifacts,
  artifactIds,
  artifact,
  isArtifact,
  artifactsMimicking,
  mimickedTypes,
  repeatsAcrossFilms,
} from '../../src/film/artifact.js';

describe('the catalog', () => {
  it('carries the eight artifacts the shop sees', () => {
    expect(artifactIds()).toHaveLength(8);
    expect(artifactIds()).toContain('CRIMP');
    expect(artifactIds()).toContain('STATIC');
  });

  it('gives every artifact an appearance, a cause and a test', () => {
    for (const record of artifacts()) {
      expect(record.appearance.length).toBeGreaterThan(10);
      expect(record.cause.length).toBeGreaterThan(10);
      expect(record.test.length).toBeGreaterThan(10);
      expect(Array.isArray(record.mimics)).toBe(true);
    }
  });

  it('finds one however it was written', () => {
    expect(artifact('crimp').id).toBe('CRIMP');
    expect(artifact(' Crimp ').id).toBe('CRIMP');
  });

  it('names what it carries when it cannot find one', () => {
    expect(() => artifact('COMET')).toThrow(/no artifact COMET/);
    expect(() => artifact(3)).toThrow(/named by a string/);
  });

  it('answers isArtifact without throwing', () => {
    expect(isArtifact('FOG')).toBe(true);
    expect(isArtifact('COMET')).toBe(false);
    expect(isArtifact(null)).toBe(false);
  });

  it('freezes a record so a reader cannot edit the catalog', () => {
    expect(() => {
      artifact('CRIMP').cause = 'something else';
    }).toThrow();
    expect(() => {
      artifact('CRIMP').mimics.push('porosity');
    }).toThrow();
  });
});

describe('artifactsMimicking', () => {
  it('finds the two things that look like a crack', () => {
    const ids = artifactsMimicking('crack').map((a) => a.id);
    expect(ids).toContain('CRIMP');
    expect(ids).toContain('STATIC');
  });

  it('finds what looks like porosity', () => {
    const ids = artifactsMimicking('porosity').map((a) => a.id);
    expect(ids).toContain('WATER');
    expect(ids).toContain('PRESSURE');
  });

  it('is not case sensitive', () => {
    expect(artifactsMimicking('CRACK')).toHaveLength(artifactsMimicking('crack').length);
  });

  it('finds nothing for an indication nothing mimics', () => {
    expect(artifactsMimicking('burn through')).toEqual([]);
  });

  it('refuses a type that is not a string', () => {
    expect(() => artifactsMimicking(null)).toThrow(/must be a string/);
  });
});

describe('mimickedTypes', () => {
  it('lists every indication type something in the catalog looks like', () => {
    const types = mimickedTypes();
    expect(types).toContain('crack');
    expect(types).toContain('porosity');
    expect(types).toEqual([...types].sort());
  });

  it('leaves out fog, which looks like nothing in a weld', () => {
    expect(artifact('FOG').mimics).toEqual([]);
  });
});

describe('repeatsAcrossFilms', () => {
  it('is true only for the cassette fault', () => {
    expect(repeatsAcrossFilms('DELAM')).toBe(true);
    expect(repeatsAcrossFilms('CRIMP')).toBe(false);
    expect(repeatsAcrossFilms('STATIC')).toBe(false);
  });

  it('refuses an artifact it does not carry', () => {
    expect(() => repeatsAcrossFilms('COMET')).toThrow(/no artifact COMET/);
  });
});
