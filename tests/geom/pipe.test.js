import { describe, it, expect } from 'vitest';
import {
  outsideRadius,
  insideDiameter,
  circumference,
  arcLength,
  degreesForArc,
  clockFromDegrees,
  degreesFromClock,
  arcFromTop,
  clockSpanDegrees,
  clockwiseSpanDegrees,
  isSmallBore,
  pipeSummary,
} from '../../src/geom/pipe.js';

describe('the basic dimensions', () => {
  it('halves the outside diameter', () => {
    expect(outsideRadius(12.75)).toBeCloseTo(6.375, 9);
  });

  it('takes two walls off for the bore', () => {
    expect(insideDiameter(12.75, 0.375)).toBeCloseTo(12, 9);
  });

  it('refuses a wall that swallows the bore', () => {
    expect(() => insideDiameter(1, 0.5)).toThrow(/two walls do not fit/);
    expect(() => insideDiameter(1, 0.6)).toThrow(/two walls do not fit/);
  });

  it('measures the circumference', () => {
    expect(circumference(12.75)).toBeCloseTo(40.055, 3);
  });

  it('refuses a diameter at or below zero', () => {
    expect(() => circumference(0)).toThrow(/greater than 0/);
    expect(() => outsideRadius(-1)).toThrow(/greater than 0/);
    expect(() => circumference('12.75')).toThrow(/finite number/);
  });
});

describe('arc length', () => {
  it('is the whole circumference at a full turn', () => {
    expect(arcLength(12.75, 360)).toBeCloseTo(circumference(12.75), 9);
  });

  it('is a quarter at ninety degrees', () => {
    expect(arcLength(12.75, 90)).toBeCloseTo(circumference(12.75) / 4, 9);
  });

  it('inverts', () => {
    expect(degreesForArc(12.75, arcLength(12.75, 137))).toBeCloseTo(137, 9);
  });
});

describe('the clock face', () => {
  it('puts the top of the pipe at twelve', () => {
    expect(clockFromDegrees(0)).toBe('12:00');
    expect(degreesFromClock('12:00')).toBe(0);
  });

  it('walks the quarters clockwise', () => {
    expect(clockFromDegrees(90)).toBe('3:00');
    expect(clockFromDegrees(180)).toBe('6:00');
    expect(clockFromDegrees(270)).toBe('9:00');
  });

  it('reads a minute hand', () => {
    expect(clockFromDegrees(212)).toBe('7:04');
    expect(degreesFromClock('7:04')).toBeCloseTo(212, 0);
  });

  it('wraps a full turn back to twelve', () => {
    expect(clockFromDegrees(360)).toBe('12:00');
    expect(clockFromDegrees(720)).toBe('12:00');
  });

  it('wraps a negative angle round the other way', () => {
    expect(clockFromDegrees(-90)).toBe('9:00');
  });

  it('round trips every whole minute on the face', () => {
    for (let minute = 0; minute < 720; minute += 1) {
      const degrees = (minute / 720) * 360;
      expect(degreesFromClock(clockFromDegrees(degrees))).toBeCloseTo(degrees, 9);
    }
  });

  it('refuses a clock position it cannot read', () => {
    expect(() => degreesFromClock('quarter past')).toThrow(/cannot read/);
    expect(() => degreesFromClock('7:60')).toThrow(/cannot read/);
    expect(() => degreesFromClock('13:00')).toThrow(/runs from 1 to 12/);
    expect(() => degreesFromClock('0:30')).toThrow(/runs from 1 to 12/);
    expect(() => degreesFromClock(700)).toThrow(/must be a string/);
  });

  it('measures the arc from the top to a clock position', () => {
    expect(arcFromTop(12.75, '3:00')).toBeCloseTo(circumference(12.75) / 4, 9);
  });
});

describe('spans across the clock face', () => {
  it('takes the short way round', () => {
    expect(clockSpanDegrees('11:50', '12:10')).toBeCloseTo(10, 6);
    expect(clockSpanDegrees('12:10', '11:50')).toBeCloseTo(10, 6);
  });

  it('is zero between a position and itself', () => {
    expect(clockSpanDegrees('7:04', '7:04')).toBe(0);
  });

  it('caps at half a turn', () => {
    expect(clockSpanDegrees('12:00', '6:00')).toBeCloseTo(180, 9);
  });

  it('walks the long way when going clockwise', () => {
    expect(clockwiseSpanDegrees('12:10', '11:50')).toBeCloseTo(350, 6);
    expect(clockwiseSpanDegrees('11:50', '12:10')).toBeCloseTo(10, 6);
  });
});

describe('isSmallBore', () => {
  it('calls three and a half inch and under small bore', () => {
    expect(isSmallBore(2.375)).toBe(true);
    expect(isSmallBore(3.5)).toBe(true);
    expect(isSmallBore(4.5)).toBe(false);
  });
});

describe('pipeSummary', () => {
  it('fills the technique sheet header', () => {
    expect(pipeSummary(12.75, 0.375)).toEqual({
      odIn: 12.75,
      wallIn: 0.375,
      idIn: 12,
      circumferenceIn: 40.055,
      smallBore: false,
    });
  });

  it('flags a small bore joint', () => {
    expect(pipeSummary(2.375, 0.218).smallBore).toBe(true);
  });
});
