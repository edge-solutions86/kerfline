import { describe, it, expect } from 'vitest';
import {
  roles,
  minimumCrew,
  makePerson,
  canInterpret,
  whoCanInterpret,
  checkCrew,
  signatory,
  qualifiedLevels,
} from '../../src/personnel/roster.js';
import { makeCertification } from '../../src/personnel/cert.js';

const goodExam = { general: 85, specific: 88, practical: 90 };

const cert = (level, certifiedOn, visionOn) =>
  makeCertification({
    person: 'x',
    level,
    certifiedOn,
    examination: goodExam,
    trainingHours: 400,
    experienceHours: 2000,
    visionOn,
  });

const ibarra = makePerson({
  name: 'R. Ibarra',
  role: 'radiographer',
  certification: cert('II', '2023-05-10', '2025-01-06'),
  monitored: true,
});

const whyte = makePerson({ name: 'D. Whyte', role: 'assistant', monitored: true });

const osei = makePerson({
  name: 'K. Osei',
  role: 'radiographer',
  certification: cert('III', '2022-08-01', '2025-02-01'),
  monitored: true,
});

describe('makePerson', () => {
  it('names the roles', () => {
    expect(roles()).toEqual(['radiographer', 'assistant', 'rso']);
    expect(minimumCrew()).toBe(2);
  });

  it('builds a crew member', () => {
    expect(ibarra.name).toBe('R. Ibarra');
    expect(ibarra.monitored).toBe(true);
  });

  it('refuses somebody with no name', () => {
    expect(() => makePerson({ name: ' ', role: 'assistant' })).toThrow(/has a name/);
  });

  it('refuses a role it does not know', () => {
    expect(() => makePerson({ name: 'X', role: 'driver' })).toThrow(/a role is one of/);
  });

  it('treats monitoring as false unless it is said', () => {
    expect(makePerson({ name: 'X', role: 'assistant' }).monitored).toBe(false);
  });
});

describe('canInterpret', () => {
  it('lets a current level two read', () => {
    expect(canInterpret(ibarra, '2025-03-14').allowed).toBe(true);
  });

  it('refuses somebody with no certification at all', () => {
    const check = canInterpret(whyte, '2025-03-14');
    expect(check.allowed).toBe(false);
    expect(check.reason).toMatch(/holds no certification/);
  });

  it('refuses once the vision test has lapsed', () => {
    const check = canInterpret(ibarra, '2026-06-01');
    expect(check.allowed).toBe(false);
    expect(check.reason).toMatch(/vision test/);
  });

  it('names the person in the reason', () => {
    expect(canInterpret(ibarra, '2026-06-01').reason).toMatch(/^R. Ibarra:/);
  });

  it('lists everybody who may read on a day', () => {
    expect(whoCanInterpret([ibarra, whyte, osei], '2025-03-14').map((p) => p.name)).toEqual([
      'R. Ibarra',
      'K. Osei',
    ]);
  });
});

describe('checkCrew', () => {
  it('lets a proper crew work', () => {
    const check = checkCrew([ibarra, whyte], '2025-03-14');
    expect(check.mayWork).toBe(true);
    expect(check.reasons).toEqual([]);
    expect(check.readers).toEqual(['R. Ibarra']);
  });

  it('refuses a crew of one however well certified', () => {
    const check = checkCrew([ibarra], '2025-03-14');
    expect(check.mayWork).toBe(false);
    expect(check.reasons.join(' ')).toMatch(/at least 2 people/);
  });

  it('refuses a crew with nobody who can read film', () => {
    const other = makePerson({ name: 'M. Lund', role: 'assistant', monitored: true });
    const check = checkCrew([whyte, other], '2025-03-14');
    expect(check.mayWork).toBe(false);
    expect(check.reasons.join(' ')).toMatch(/nobody on the crew may interpret/);
  });

  it('refuses a crew with no radiographer on it', () => {
    const other = makePerson({ name: 'M. Lund', role: 'assistant', monitored: true });
    expect(checkCrew([whyte, other], '2025-03-14').reasons.join(' ')).toMatch(
      /no radiographer/
    );
  });

  it('names anybody who is not a monitored radiation worker', () => {
    const unbadged = makePerson({ name: 'T. Kerr', role: 'assistant' });
    const check = checkCrew([ibarra, unbadged], '2025-03-14');
    expect(check.mayWork).toBe(false);
    expect(check.reasons.join(' ')).toMatch(/T. Kerr/);
  });

  it('gives every reason at once', () => {
    const unbadged = makePerson({ name: 'T. Kerr', role: 'assistant' });
    expect(checkCrew([unbadged], '2025-03-14').reasons.length).toBeGreaterThanOrEqual(3);
  });

  it('turns a working crew away once the certification lapses', () => {
    expect(checkCrew([ibarra, whyte], '2025-03-14').mayWork).toBe(true);
    expect(checkCrew([ibarra, whyte], '2026-06-01').mayWork).toBe(false);
  });
});

describe('signatory', () => {
  it('picks the most senior current reader', () => {
    expect(signatory([ibarra, osei, whyte], '2025-03-14').name).toBe('K. Osei');
  });

  it('falls to the next one when the senior reader has lapsed', () => {
    const lapsed = makePerson({
      name: 'K. Osei',
      role: 'radiographer',
      certification: cert('III', '2016-08-01', '2016-08-01'),
      monitored: true,
    });
    expect(signatory([ibarra, lapsed], '2025-03-14').name).toBe('R. Ibarra');
  });

  it('is nobody when no one may sign', () => {
    expect(signatory([whyte], '2025-03-14')).toBe(null);
  });
});

describe('qualifiedLevels', () => {
  it('lists everybody whose level permits interpretation', () => {
    expect(qualifiedLevels([ibarra, whyte, osei])).toEqual([
      { name: 'R. Ibarra', level: 'II' },
      { name: 'K. Osei', level: 'III' },
    ]);
  });

  it('leaves out a level one, who does not interpret', () => {
    const trainee = makePerson({
      name: 'S. Pike',
      role: 'assistant',
      certification: cert('I', '2024-05-10', '2025-01-06'),
      monitored: true,
    });
    expect(qualifiedLevels([trainee])).toEqual([]);
  });

  it('leaves out anybody with no certification', () => {
    expect(qualifiedLevels([whyte])).toEqual([]);
  });
});
