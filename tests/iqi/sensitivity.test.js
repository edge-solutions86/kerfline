import { describe, it, expect } from 'vitest';
import {
  calledForSensitivityPercent,
  holeSensitivityPercent,
  holeSensitivityFor,
  wireSensitivityPercent,
  wireSensitivityFor,
  judgeHoleSensitivity,
  judgeWireSensitivity,
  sensitivityLabel,
  thicknessHeadroomIn,
} from '../../src/iqi/sensitivity.js';

describe('the called for sensitivity', () => {
  it('is two percent unless a job says otherwise', () => {
    expect(calledForSensitivityPercent()).toBe(2);
  });
});

describe('holeSensitivityPercent', () => {
  it('follows the square root formula', () => {
    const value = holeSensitivityPercent({
      iqiThicknessIn: 0.025,
      holeDiameterIn: 0.05,
      thicknessIn: 1,
    });
    expect(value).toBeCloseTo(100 * Math.sqrt((0.025 * 0.05) / 2), 9);
    expect(value).toBeCloseTo(2.5, 3);
  });

  it('improves as the shot goes through less metal', () => {
    const thin = holeSensitivityPercent({
      iqiThicknessIn: 0.025,
      holeDiameterIn: 0.05,
      thicknessIn: 0.5,
    });
    const thick = holeSensitivityPercent({
      iqiThicknessIn: 0.025,
      holeDiameterIn: 0.05,
      thicknessIn: 2,
    });
    expect(thin).toBeGreaterThan(thick);
  });

  it('halves when the thickness doubles', () => {
    const one = holeSensitivityPercent({
      iqiThicknessIn: 0.025,
      holeDiameterIn: 0.05,
      thicknessIn: 1,
    });
    const two = holeSensitivityPercent({
      iqiThicknessIn: 0.025,
      holeDiameterIn: 0.05,
      thicknessIn: 2,
    });
    expect(two).toBeCloseTo(one / 2, 9);
  });

  it('refuses a thickness or a hole at zero', () => {
    expect(() =>
      holeSensitivityPercent({ iqiThicknessIn: 0.025, holeDiameterIn: 0.05, thicknessIn: 0 })
    ).toThrow(/greater than 0/);
    expect(() =>
      holeSensitivityPercent({ iqiThicknessIn: 0.025, holeDiameterIn: 0, thicknessIn: 1 })
    ).toThrow(/hole diameter/);
  });
});

describe('holeSensitivityFor', () => {
  it('uses the drilled hole and not the nominal multiple', () => {
    // The 5 plaque 1T hole is opened up to 0.010 in, well past 0.005 in.
    const drilled = holeSensitivityFor({ identity: 5, hole: '1T', thicknessIn: 0.1 });
    const nominal = holeSensitivityPercent({
      iqiThicknessIn: 0.005,
      holeDiameterIn: 0.005,
      thicknessIn: 0.1,
    });
    expect(drilled).toBeGreaterThan(nominal);
  });

  it('agrees with the formula on a plaque with no floor effect', () => {
    const looked = holeSensitivityFor({ identity: 25, hole: '2T', thicknessIn: 1 });
    const direct = holeSensitivityPercent({
      iqiThicknessIn: 0.025,
      holeDiameterIn: 0.05,
      thicknessIn: 1,
    });
    expect(looked).toBeCloseTo(direct, 12);
  });

  it('gets better through the bigger holes on the same plaque', () => {
    const one = holeSensitivityFor({ identity: 25, hole: '1T', thicknessIn: 1 });
    const four = holeSensitivityFor({ identity: 25, hole: '4T', thicknessIn: 1 });
    expect(one).toBeLessThan(four);
  });
});

describe('wireSensitivityPercent', () => {
  it('is the wire diameter as a percentage of the thickness', () => {
    expect(wireSensitivityPercent({ wireDiameterIn: 0.01, thicknessIn: 0.5 })).toBeCloseTo(
      2,
      9
    );
  });

  it('looks the wire up by number', () => {
    expect(wireSensitivityFor({ wireNumber: 6, thicknessIn: 0.5 })).toBeCloseTo(2, 9);
  });

  it('improves with a finer wire', () => {
    const fine = wireSensitivityFor({ wireNumber: 4, thicknessIn: 0.5 });
    const coarse = wireSensitivityFor({ wireNumber: 8, thicknessIn: 0.5 });
    expect(fine).toBeLessThan(coarse);
  });

  it('refuses a wire that does not exist', () => {
    expect(() => wireSensitivityFor({ wireNumber: 99, thicknessIn: 0.5 })).toThrow(
      /no wire numbered 99/
    );
  });
});

describe('judgeHoleSensitivity', () => {
  it('passes a shot that beats the called for figure', () => {
    const verdict = judgeHoleSensitivity({ identity: 25, hole: '2T', thicknessIn: 2 });
    expect(verdict.achievedPercent).toBeCloseTo(1.25, 2);
    expect(verdict.calledForPercent).toBe(2);
    expect(verdict.pass).toBe(true);
    expect(verdict.marginPercent).toBeGreaterThan(0);
  });

  it('fails a shot that does not reach it', () => {
    const verdict = judgeHoleSensitivity({ identity: 25, hole: '2T', thicknessIn: 0.5 });
    expect(verdict.pass).toBe(false);
    expect(verdict.marginPercent).toBeLessThan(0);
  });

  it('takes a job specific figure', () => {
    const strict = judgeHoleSensitivity({
      identity: 25,
      hole: '2T',
      thicknessIn: 2,
      calledFor: 1,
    });
    expect(strict.calledForPercent).toBe(1);
    expect(strict.pass).toBe(false);
  });

  it('judges a wire the same way', () => {
    const verdict = judgeWireSensitivity({ wireNumber: 6, thicknessIn: 1 });
    expect(verdict.achievedPercent).toBeCloseTo(1, 3);
    expect(verdict.pass).toBe(true);
  });

  it('refuses a called for figure at zero', () => {
    expect(() => judgeWireSensitivity({ wireNumber: 6, thicknessIn: 1, calledFor: 0 })).toThrow(
      /greater than 0/
    );
  });
});

describe('sensitivityLabel', () => {
  it('writes the plaque as a percentage next to the hole', () => {
    expect(sensitivityLabel({ identity: 25, hole: '2T', thicknessIn: 1.25 })).toBe('2-2T');
  });

  it('moves the first number with the thickness', () => {
    expect(sensitivityLabel({ identity: 25, hole: '2T', thicknessIn: 2.5 })).toBe('1-2T');
  });

  it('names the hole that was seen', () => {
    expect(sensitivityLabel({ identity: 25, hole: '1T', thicknessIn: 1.25 })).toBe('2-1T');
  });
});

describe('thicknessHeadroomIn', () => {
  it('is positive while the shot is still passing', () => {
    expect(thicknessHeadroomIn({ identity: 25, hole: '2T', thicknessIn: 2 })).toBeGreaterThan(
      0
    );
  });

  it('is negative once the shot has stopped passing', () => {
    expect(thicknessHeadroomIn({ identity: 25, hole: '2T', thicknessIn: 0.5 })).toBeLessThan(0);
  });

  it('is nothing at the thickness where the verdict flips', () => {
    const headroom = thicknessHeadroomIn({ identity: 25, hole: '2T', thicknessIn: 1.25 });
    expect(headroom).toBeCloseTo(0, 3);
    const verdict = judgeHoleSensitivity({ identity: 25, hole: '2T', thicknessIn: 1.25 });
    expect(verdict.achievedPercent).toBeCloseTo(2, 2);
  });
});
