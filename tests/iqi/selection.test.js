import { describe, it, expect } from 'vitest';
import {
  sides,
  essentialHole,
  selectionTable,
  basisThicknessIn,
  selectIqi,
  selectForJoint,
  selectionCovers,
} from '../../src/iqi/selection.js';

describe('the selection table', () => {
  it('names the two sides and the essential hole', () => {
    expect(sides()).toEqual(['source', 'film']);
    expect(essentialHole()).toBe('2T');
  });

  it('climbs without a gap', () => {
    const rows = selectionTable();
    rows.forEach((row, index) => {
      if (index > 0) expect(row.over).toBe(rows[index - 1].upTo);
      expect(row.upTo).toBeGreaterThan(row.over);
    });
  });

  it('always asks less of the film side than the source side', () => {
    for (const row of selectionTable()) {
      expect(row.film.hole).toBeLessThan(row.source.hole);
      expect(row.film.wire).toBeLessThan(row.source.wire);
    }
  });

  it('never steps backwards as the wall thickens', () => {
    const rows = selectionTable();
    for (let i = 1; i < rows.length; i += 1) {
      expect(rows[i].source.hole).toBeGreaterThan(rows[i - 1].source.hole);
      expect(rows[i].source.wire).toBeGreaterThanOrEqual(rows[i - 1].source.wire);
    }
  });
});

describe('basisThicknessIn', () => {
  it('is the bare wall when there is no crown', () => {
    expect(basisThicknessIn({ wallIn: 0.375 })).toBe(0.375);
  });

  it('adds the weld reinforcement', () => {
    expect(basisThicknessIn({ wallIn: 0.375, reinforcementIn: 0.0625 })).toBeCloseTo(0.4375, 6);
  });

  it('caps the reinforcement at what the job allows', () => {
    const basis = basisThicknessIn({
      wallIn: 0.375,
      reinforcementIn: 0.25,
      maximumReinforcementIn: 0.0625,
    });
    expect(basis).toBeCloseTo(0.4375, 6);
  });

  it('leaves the backing out of it entirely', () => {
    const withBacking = basisThicknessIn({ wallIn: 0.375, backingIn: 0.125 });
    const without = basisThicknessIn({ wallIn: 0.375 });
    expect(withBacking).toBe(without);
  });

  it('refuses a wall at or below zero', () => {
    expect(() => basisThicknessIn({ wallIn: 0 })).toThrow(/greater than 0/);
  });

  it('refuses a negative crown or backing', () => {
    expect(() => basisThicknessIn({ wallIn: 0.375, reinforcementIn: -0.1 })).toThrow(
      /not be below 0/
    );
    expect(() => basisThicknessIn({ wallIn: 0.375, backingIn: -0.1 })).toThrow(
      /not be below 0/
    );
  });
});

describe('selectIqi', () => {
  it('reads the source side row', () => {
    const chosen = selectIqi(0.375, 'source');
    expect(chosen.holeIdentity).toBe(15);
    expect(chosen.wireNumber).toBe(6);
    expect(chosen.essentialHole).toBe('2T');
  });

  it('reads the film side row at the same thickness', () => {
    const chosen = selectIqi(0.375, 'film');
    expect(chosen.holeIdentity).toBe(12);
    expect(chosen.wireNumber).toBe(5);
  });

  it('defaults to the source side', () => {
    expect(selectIqi(0.375).side).toBe('source');
  });

  it('puts each boundary in the row below it', () => {
    expect(selectIqi(0.25).holeIdentity).toBe(12);
    expect(selectIqi(0.2501).holeIdentity).toBe(15);
    expect(selectIqi(0.5).holeIdentity).toBe(17);
    expect(selectIqi(0.5001).holeIdentity).toBe(20);
  });

  it('carries the drilled hole size through from the plaque', () => {
    const chosen = selectIqi(1, 'source');
    expect(chosen.holeIdentity).toBe(25);
    expect(chosen.plaqueThicknessIn).toBeCloseTo(0.025, 6);
    expect(chosen.essentialHoleIn).toBeCloseTo(0.05, 6);
  });

  it('names the wire set the crew has to draw', () => {
    expect(selectIqi(0.375, 'source').wireSet).toBe('A');
    expect(selectIqi(2, 'source').wireSet).toBe('B');
  });

  it('refuses a side that is not one of the two', () => {
    expect(() => selectIqi(0.375, 'back')).toThrow(/source or film/);
  });

  it('refuses a thickness past the end of the table', () => {
    expect(() => selectIqi(24)).toThrow(/above the top of/);
  });

  it('refuses a thickness at or below zero', () => {
    expect(() => selectIqi(0)).toThrow(/greater than 0/);
  });
});

describe('selectForJoint', () => {
  it('runs the basis rule and then the table', () => {
    const chosen = selectForJoint({ wallIn: 0.25, reinforcementIn: 0.0625 });
    expect(chosen.basisThicknessIn).toBeCloseTo(0.3125, 6);
    expect(chosen.holeIdentity).toBe(15);
  });

  it('moves the answer a whole row when the crown is counted', () => {
    const bare = selectForJoint({ wallIn: 0.25 });
    const crowned = selectForJoint({ wallIn: 0.25, reinforcementIn: 0.0625 });
    expect(bare.holeIdentity).toBe(12);
    expect(crowned.holeIdentity).toBe(15);
  });

  it('does not move the answer when a backing ring is fitted', () => {
    const plain = selectForJoint({ wallIn: 0.25 });
    const backed = selectForJoint({ wallIn: 0.25, backingIn: 0.125 });
    expect(backed.holeIdentity).toBe(plain.holeIdentity);
    expect(backed.backingIgnoredIn).toBe(0.125);
  });

  it('takes the side from the joint', () => {
    expect(selectForJoint({ wallIn: 0.375, side: 'film' }).holeIdentity).toBe(12);
  });
});

describe('selectionCovers', () => {
  it('answers without throwing', () => {
    expect(selectionCovers(0.375)).toBe(true);
    expect(selectionCovers(20)).toBe(true);
    expect(selectionCovers(21)).toBe(false);
    expect(selectionCovers(0)).toBe(false);
    expect(selectionCovers('0.375')).toBe(false);
  });
});
