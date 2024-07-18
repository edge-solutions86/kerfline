import { describe, it, expect } from 'vitest';
import {
  films,
  filmIds,
  film,
  filmSpeed,
  filmGrade,
  grades,
  meetsGrade,
  filmsMeeting,
  convertExposure,
  fastestMeeting,
} from '../../src/exposure/filmclass.js';

describe('the qualified film list', () => {
  it('carries the six films the procedure names', () => {
    expect(filmIds()).toEqual(['D2', 'D3', 'D4', 'D5', 'D7', 'D8']);
  });

  it('gets faster as the grain gets coarser', () => {
    const speeds = films().map((f) => f.speed);
    expect(speeds).toEqual([...speeds].sort((a, b) => a - b));
  });

  it('takes the medium film as the unit of speed', () => {
    expect(filmSpeed('D7')).toBe(1);
  });

  it('doubles from the medium film to the fast one', () => {
    expect(filmSpeed('D8')).toBeCloseTo(filmSpeed('D7') * 2, 9);
    expect(filmSpeed('D5')).toBeCloseTo(filmSpeed('D7') / 2, 9);
  });

  it('finds a film however the technician wrote it', () => {
    expect(film('d5').id).toBe('D5');
    expect(film(' D5 ').id).toBe('D5');
  });

  it('names the qualified list when it cannot find one', () => {
    expect(() => film('AA400')).toThrow(/no film AA400/);
    expect(() => film(5)).toThrow(/named by a string/);
  });

  it('freezes a record', () => {
    expect(() => {
      film('D7').speed = 99;
    }).toThrow();
  });
});

describe('the classes', () => {
  it('runs from special down to three', () => {
    expect(grades()).toEqual(['SPECIAL', 'I', 'II', 'III']);
  });

  it('places each film in its class', () => {
    expect(filmGrade('D2')).toBe('SPECIAL');
    expect(filmGrade('D4')).toBe('I');
    expect(filmGrade('D7')).toBe('II');
    expect(filmGrade('D8')).toBe('III');
  });

  it('lets a finer film stand in for a coarser call', () => {
    expect(meetsGrade('D4', 'II')).toBe(true);
    expect(meetsGrade('D2', 'III')).toBe(true);
  });

  it('does not let a coarser film answer a finer call', () => {
    expect(meetsGrade('D8', 'II')).toBe(false);
    expect(meetsGrade('D7', 'I')).toBe(false);
  });

  it('accepts a film at exactly its own class', () => {
    expect(meetsGrade('D7', 'II')).toBe(true);
  });

  it('lists what a call admits', () => {
    expect(filmsMeeting('I').map((f) => f.id)).toEqual(['D2', 'D3', 'D4', 'D5']);
    expect(filmsMeeting('SPECIAL').map((f) => f.id)).toEqual(['D2', 'D3']);
  });

  it('refuses a class that does not exist', () => {
    expect(() => meetsGrade('D7', 'IV')).toThrow(/no such film class IV/);
  });
});

describe('convertExposure', () => {
  it('leaves an exposure alone on the same film', () => {
    expect(convertExposure(171, 'D7', 'D7')).toBeCloseTo(171, 9);
  });

  it('doubles going to a film half the speed', () => {
    expect(convertExposure(171, 'D7', 'D5')).toBeCloseTo(342, 9);
  });

  it('halves going to a film twice the speed', () => {
    expect(convertExposure(171, 'D7', 'D8')).toBeCloseTo(85.5, 9);
  });

  it('round trips', () => {
    const there = convertExposure(171, 'D7', 'D4');
    expect(convertExposure(there, 'D4', 'D7')).toBeCloseTo(171, 9);
  });

  it('refuses a negative exposure', () => {
    expect(() => convertExposure(-1, 'D7', 'D5')).toThrow(/not be negative/);
  });
});

describe('fastestMeeting', () => {
  it('picks the fastest film a class still admits', () => {
    expect(fastestMeeting('III').id).toBe('D8');
    expect(fastestMeeting('II').id).toBe('D7');
    expect(fastestMeeting('I').id).toBe('D5');
    expect(fastestMeeting('SPECIAL').id).toBe('D3');
  });

  it('refuses a class it does not know', () => {
    expect(() => fastestMeeting('IV')).toThrow(/no such film class/);
  });
});
