import { describe, it, expect } from 'vitest';
import {
  holeIqiIdentities,
  holeNames,
  hasHoleIqi,
  holeIqiThicknessIn,
  holeDiameterIn,
  holeMultiple,
  holeIsAtFloor,
  minimumHoleDiameterIn,
  holeIqi,
  nextHoleIqi,
} from '../../src/iqi/holetype.js';

describe('the stocked plaques', () => {
  it('climbs from five to two hundred and eighty', () => {
    const ids = holeIqiIdentities();
    expect(ids[0]).toBe(5);
    expect(ids[ids.length - 1]).toBe(280);
    expect(ids).toEqual([...ids].sort((a, b) => a - b));
  });

  it('names the three holes', () => {
    expect(holeNames()).toEqual(['1T', '2T', '4T']);
  });

  it('answers hasHoleIqi without throwing', () => {
    expect(hasHoleIqi(25)).toBe(true);
    expect(hasHoleIqi(26)).toBe(false);
  });

  it('reads the identity as thousandths of an inch', () => {
    expect(holeIqiThicknessIn(25)).toBeCloseTo(0.025, 9);
    expect(holeIqiThicknessIn(200)).toBeCloseTo(0.2, 9);
  });

  it('refuses a plaque nobody stocks', () => {
    expect(() => holeIqiThicknessIn(26)).toThrow(/no hole type indicator numbered 26/);
    expect(() => holeIqiThicknessIn('25')).toThrow(/must be a number/);
  });
});

describe('holeMultiple', () => {
  it('reads the three multiples off the names', () => {
    expect(holeMultiple('1T')).toBe(1);
    expect(holeMultiple('2T')).toBe(2);
    expect(holeMultiple('4T')).toBe(4);
  });

  it('refuses a hole that is not drilled', () => {
    expect(() => holeMultiple('3T')).toThrow(/no such hole 3T/);
  });
});

describe('holeDiameterIn', () => {
  it('is the multiple of the plaque thickness on a thick plaque', () => {
    expect(holeDiameterIn(25, '1T')).toBeCloseTo(0.025, 6);
    expect(holeDiameterIn(25, '2T')).toBeCloseTo(0.05, 6);
    expect(holeDiameterIn(25, '4T')).toBeCloseTo(0.1, 6);
  });

  it('never drills below ten thousandths', () => {
    expect(holeDiameterIn(5, '1T')).toBeCloseTo(0.01, 6);
    expect(holeDiameterIn(7, '1T')).toBeCloseTo(0.01, 6);
    expect(minimumHoleDiameterIn()).toBe(0.01);
  });

  it('opens the 2T hole too on the very thinnest plaque', () => {
    expect(holeDiameterIn(5, '2T')).toBeCloseTo(0.01, 6);
    expect(holeDiameterIn(7, '2T')).toBeCloseTo(0.014, 6);
  });

  it('reaches the nominal multiple as soon as the arithmetic clears the floor', () => {
    expect(holeDiameterIn(10, '1T')).toBeCloseTo(0.01, 6);
    expect(holeDiameterIn(12, '1T')).toBeCloseTo(0.012, 6);
  });

  it('says which holes were opened up', () => {
    expect(holeIsAtFloor(5, '1T')).toBe(true);
    expect(holeIsAtFloor(5, '4T')).toBe(false);
    expect(holeIsAtFloor(25, '1T')).toBe(false);
  });

  it('does not count a hole that lands exactly on the floor as opened up', () => {
    // The 5 plaque is 0.005 in, so its 2T hole is 0.010 in on the nose. It is
    // drilled at its nominal size and the sensitivity arithmetic is honest.
    expect(holeDiameterIn(5, '2T')).toBeCloseTo(0.01, 6);
    expect(holeIsAtFloor(5, '2T')).toBe(false);
  });
});

describe('holeIqi', () => {
  it('gives the whole plaque for the sheet', () => {
    expect(holeIqi(25)).toEqual({
      identity: 25,
      thicknessIn: 0.025,
      holes: { '1T': 0.025, '2T': 0.05, '4T': 0.1 },
      atFloor: [],
    });
  });

  it('lists the opened holes on a thin plaque', () => {
    expect(holeIqi(5).atFloor).toEqual(['1T']);
  });

  it('agrees with holeDiameterIn on every stocked plaque', () => {
    for (const identity of holeIqiIdentities()) {
      const plaque = holeIqi(identity);
      for (const hole of holeNames()) {
        expect(plaque.holes[hole]).toBe(holeDiameterIn(identity, hole));
      }
    }
  });
});

describe('nextHoleIqi', () => {
  it('steps up and down the stocked set', () => {
    expect(nextHoleIqi(25)).toBe(30);
    expect(nextHoleIqi(25, -1)).toBe(20);
  });

  it('refuses to run off either end', () => {
    expect(() => nextHoleIqi(5, -1)).toThrow(/no plaque past 5/);
    expect(() => nextHoleIqi(280, 1)).toThrow(/no plaque past 280/);
  });

  it('refuses a direction that is not one step', () => {
    expect(() => nextHoleIqi(25, 2)).toThrow(/must be 1 or -1/);
  });
});
