import { describe, it, expect } from 'vitest';
import {
  levels,
  requirementsFor,
  papers,
  passMarks,
  gradeExamination,
  hoursMet,
  makeCertification,
  mayInterpret,
  nextDue,
  lapsingWithin,
} from '../../src/personnel/cert.js';

const goodExam = { general: 85, specific: 88, practical: 90 };

const certification = makeCertification({
  person: 'R. Ibarra',
  level: 'II',
  certifiedOn: '2023-05-10',
  examination: goodExam,
  trainingHours: 80,
  experienceHours: 900,
  visionOn: '2025-01-06',
});

describe('the levels', () => {
  it('runs one to three', () => {
    expect(levels()).toEqual(['I', 'II', 'III']);
  });

  it('asks more experience at each level', () => {
    expect(requirementsFor('II').experienceHours).toBeGreaterThan(
      requirementsFor('I').experienceHours
    );
    expect(requirementsFor('III').experienceHours).toBeGreaterThan(
      requirementsFor('II').experienceHours
    );
  });

  it('does not let a level one interpret film', () => {
    expect(requirementsFor('I').mayInterpret).toBe(false);
    expect(requirementsFor('II').mayInterpret).toBe(true);
  });

  it('refuses a level that does not exist', () => {
    expect(() => requirementsFor('IV')).toThrow(/no certification level IV/);
  });
});

describe('gradeExamination', () => {
  it('names the three papers and the two marks', () => {
    expect(papers()).toEqual(['general', 'specific', 'practical']);
    expect(passMarks()).toEqual({ compositePass: 80, paperPass: 70 });
  });

  it('passes a candidate who clears both conditions', () => {
    const result = gradeExamination(goodExam);
    expect(result.composite).toBeCloseTo(87.67, 2);
    expect(result.pass).toBe(true);
  });

  it('fails a high average with one paper under seventy', () => {
    const result = gradeExamination({ general: 95, specific: 95, practical: 68 });
    expect(result.composite).toBe(86);
    expect(result.compositeMet).toBe(true);
    expect(result.everyPaperMet).toBe(false);
    expect(result.pass).toBe(false);
  });

  it('fails every paper passing but the average short', () => {
    const result = gradeExamination({ general: 75, specific: 75, practical: 75 });
    expect(result.everyPaperMet).toBe(true);
    expect(result.compositeMet).toBe(false);
    expect(result.pass).toBe(false);
  });

  it('passes exactly on both marks', () => {
    const result = gradeExamination({ general: 70, specific: 85, practical: 85 });
    expect(result.composite).toBe(80);
    expect(result.lowestPaper).toBe(70);
    expect(result.pass).toBe(true);
  });

  it('refuses a score outside nought to a hundred', () => {
    expect(() => gradeExamination({ ...goodExam, general: 101 })).toThrow(/above 100/);
    expect(() => gradeExamination({ ...goodExam, general: -1 })).toThrow(/not be below 0/);
  });

  it('refuses a missing paper', () => {
    expect(() => gradeExamination({ general: 85, specific: 88 })).toThrow(/practical paper/);
  });
});

describe('hoursMet', () => {
  it('meets a level with enough of both', () => {
    const met = hoursMet({ level: 'II', trainingHours: 80, experienceHours: 900 });
    expect(met.met).toBe(true);
    expect(met.trainingShort).toBe(0);
  });

  it('says how short the experience is', () => {
    const met = hoursMet({ level: 'II', trainingHours: 80, experienceHours: 400 });
    expect(met.met).toBe(false);
    expect(met.experienceShort).toBe(230);
  });

  it('needs both and not either', () => {
    const noTraining = hoursMet({ level: 'II', trainingHours: 10, experienceHours: 900 });
    expect(noTraining.experienceMet).toBe(true);
    expect(noTraining.met).toBe(false);
  });
});

describe('makeCertification', () => {
  it('sets the two clocks independently', () => {
    expect(certification.expiresOn).toBe('2028-05-10');
    expect(certification.visionExpiresOn).toBe('2026-01-06');
  });

  it('is valid when the exam and the hours are both good', () => {
    expect(certification.valid).toBe(true);
  });

  it('is not valid when the exam failed however good the hours are', () => {
    const failed = makeCertification({
      person: 'D. Whyte',
      level: 'II',
      certifiedOn: '2023-05-10',
      examination: { general: 95, specific: 95, practical: 68 },
      trainingHours: 400,
      experienceHours: 5000,
      visionOn: '2025-01-06',
    });
    expect(failed.valid).toBe(false);
  });

  it('refuses a certification with nobody on it', () => {
    expect(() =>
      makeCertification({
        person: '',
        level: 'II',
        certifiedOn: '2023-05-10',
        examination: goodExam,
        trainingHours: 80,
        experienceHours: 900,
        visionOn: '2025-01-06',
      })
    ).toThrow(/names the person/);
  });
});

describe('mayInterpret', () => {
  it('lets a current level two read film', () => {
    expect(mayInterpret(certification, '2025-03-14').allowed).toBe(true);
  });

  it('will not let a level one read film however current', () => {
    const levelOne = makeCertification({
      person: 'K. Osei',
      level: 'I',
      certifiedOn: '2024-05-10',
      examination: goodExam,
      trainingHours: 80,
      experienceHours: 300,
      visionOn: '2025-01-06',
    });
    const check = mayInterpret(levelOne, '2025-03-14');
    expect(check.allowed).toBe(false);
    expect(check.reason).toMatch(/does not interpret film/);
  });

  it('stops once the certification expires', () => {
    const check = mayInterpret(certification, '2028-05-11');
    expect(check.allowed).toBe(false);
    expect(check.reason).toMatch(/certification has expired/);
  });

  it('stops once the vision test lapses even with years of certification left', () => {
    const check = mayInterpret(certification, '2026-01-07');
    expect(check.allowed).toBe(false);
    expect(check.reason).toMatch(/vision test has lapsed/);
  });

  it('does not allow reading before the certificate was issued', () => {
    expect(mayInterpret(certification, '2023-05-09').allowed).toBe(false);
  });

  it('never allows reading on an invalid certification', () => {
    const failed = makeCertification({
      person: 'D. Whyte',
      level: 'II',
      certifiedOn: '2023-05-10',
      examination: { general: 60, specific: 60, practical: 60 },
      trainingHours: 80,
      experienceHours: 900,
      visionOn: '2025-01-06',
    });
    expect(mayInterpret(failed, '2025-03-14').allowed).toBe(false);
  });
});

describe('nextDue', () => {
  it('puts the sooner of the two clocks first', () => {
    const due = nextDue(certification, '2025-03-14');
    expect(due[0].what).toBe('vision test');
    expect(due[1].what).toBe('certification');
  });

  it('counts the days left', () => {
    const due = nextDue(certification, '2026-01-01');
    expect(due[0].daysLeft).toBe(5);
  });

  it('goes negative once something has lapsed', () => {
    expect(nextDue(certification, '2026-02-01')[0].daysLeft).toBeLessThan(0);
  });
});

describe('lapsingWithin', () => {
  it('finds nobody when nothing is due', () => {
    expect(lapsingWithin([certification], { day: '2025-03-14', days: 30 })).toEqual([]);
  });

  it('finds somebody whose vision test is coming up', () => {
    const lapsing = lapsingWithin([certification], { day: '2025-12-20', days: 30 });
    expect(lapsing).toHaveLength(1);
    expect(lapsing[0].person).toBe('R. Ibarra');
    expect(lapsing[0].due[0].what).toBe('vision test');
  });

  it('refuses a negative window', () => {
    expect(() => lapsingWithin([certification], { day: '2025-03-14', days: -1 })).toThrow(
      /not be below 0/
    );
  });
});
