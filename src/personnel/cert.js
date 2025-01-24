import { BadInput, NotFound } from '../errors.js';
import { round, mean } from '../util/num.js';
import { requireArray, requireAtLeast } from '../util/guard.js';
import { requireDay, addYears, addMonths, daysBetween } from '../util/dates.js';

/**
 * Certifying a technician to read film.
 *
 * The examination rule is the interesting one and it is easy to get wrong in
 * a way that certifies somebody who should not be. There are three papers,
 * general, specific and practical. The composite is the plain average of the
 * three and it has to reach eighty. Separately, no single paper may be under
 * seventy. Both conditions, not either: a candidate scoring 95, 95 and 68
 * averages 86 and is not certified, and a candidate scoring 75, 75 and 75
 * passes every paper and averages 75 and is not certified either.
 *
 * The vision test is annual and the certification is five yearly, so the two
 * clocks run independently and a technician can be current on one and lapsed
 * on the other. Both have to be good on the day the film is read.
 */

const LEVELS = {
  I: { trainingHours: 40, experienceHours: 210, mayInterpret: false, maySign: false },
  II: { trainingHours: 40, experienceHours: 630, mayInterpret: true, maySign: true },
  III: { trainingHours: 40, experienceHours: 1680, mayInterpret: true, maySign: true },
};

const PAPERS = ['general', 'specific', 'practical'];
const COMPOSITE_PASS = 80;
const PAPER_PASS = 70;
const CERT_YEARS = 5;
const VISION_MONTHS = 12;

export function levels() {
  return Object.keys(LEVELS);
}

export function requirementsFor(level) {
  const found = LEVELS[level];
  if (!found) {
    throw new NotFound(`no certification level ${level}`, { asked: level, known: levels() });
  }
  return { ...found };
}

export function papers() {
  return [...PAPERS];
}

export function passMarks() {
  return { compositePass: COMPOSITE_PASS, paperPass: PAPER_PASS };
}

/**
 * Grade the three papers. Reports both conditions separately so a failing
 * candidate can be told which one they missed.
 */
export function gradeExamination(scores) {
  if (typeof scores !== 'object' || scores === null) {
    throw new BadInput('examination scores must be given as an object', { got: scores });
  }
  const values = PAPERS.map((paper) => {
    const value = scores[paper];
    requireAtLeast(value, 0, `${paper} paper`);
    if (value > 100) {
      throw new BadInput(`${paper} paper cannot score above 100`, { got: value });
    }
    return value;
  });
  const composite = round(mean(values), 2);
  const lowest = Math.min(...values);
  return {
    scores: Object.fromEntries(PAPERS.map((paper, i) => [paper, values[i]])),
    composite,
    lowestPaper: lowest,
    compositeMet: composite >= COMPOSITE_PASS,
    everyPaperMet: lowest >= PAPER_PASS,
    pass: composite >= COMPOSITE_PASS && lowest >= PAPER_PASS,
  };
}

/** Whether the hours put a candidate in reach of a level. */
export function hoursMet({ level, trainingHours, experienceHours }) {
  const wanted = requirementsFor(level);
  requireAtLeast(trainingHours, 0, 'training hours');
  requireAtLeast(experienceHours, 0, 'experience hours');
  return {
    level,
    trainingMet: trainingHours >= wanted.trainingHours,
    experienceMet: experienceHours >= wanted.experienceHours,
    trainingShort: Math.max(0, wanted.trainingHours - trainingHours),
    experienceShort: Math.max(0, wanted.experienceHours - experienceHours),
    met: trainingHours >= wanted.trainingHours && experienceHours >= wanted.experienceHours,
  };
}

export function makeCertification({
  person,
  level,
  certifiedOn,
  examination,
  trainingHours,
  experienceHours,
  visionOn,
}) {
  if (typeof person !== 'string' || person.trim() === '') {
    throw new BadInput('a certification names the person it belongs to', { got: person });
  }
  requirementsFor(level);
  requireDay(certifiedOn, 'certification date');
  requireDay(visionOn, 'vision test date');
  const exam = gradeExamination(examination);
  const hours = hoursMet({ level, trainingHours, experienceHours });
  return {
    person: person.trim(),
    level,
    certifiedOn,
    expiresOn: addYears(certifiedOn, CERT_YEARS),
    visionOn,
    visionExpiresOn: addMonths(visionOn, VISION_MONTHS),
    examination: exam,
    hours,
    valid: exam.pass && hours.met,
  };
}

/**
 * Whether somebody may read film on a given day. Three clocks have to be
 * good at once and the answer names the one that is not.
 */
export function mayInterpret(certification, day) {
  requireDay(day, 'date');
  if (!certification.valid) {
    return { allowed: false, reason: 'the certification was never valid' };
  }
  if (!requirementsFor(certification.level).mayInterpret) {
    return { allowed: false, reason: `a level ${certification.level} does not interpret film` };
  }
  if (daysBetween(certification.certifiedOn, day) < 0) {
    return { allowed: false, reason: 'the certification had not been issued yet' };
  }
  if (day > certification.expiresOn) {
    return { allowed: false, reason: 'the certification has expired' };
  }
  if (day > certification.visionExpiresOn) {
    return { allowed: false, reason: 'the annual vision test has lapsed' };
  }
  return { allowed: true, reason: null };
}

/** What lapses next, so a scheduler can book the recertification. */
export function nextDue(certification, day) {
  requireDay(day, 'date');
  const items = [
    { what: 'vision test', on: certification.visionExpiresOn },
    { what: 'certification', on: certification.expiresOn },
  ].sort((a, b) => (a.on < b.on ? -1 : 1));
  return items.map((item) => ({ ...item, daysLeft: daysBetween(day, item.on) }));
}

/** Everyone whose certification or vision lapses inside a window. */
export function lapsingWithin(certifications, { day, days }) {
  requireArray(certifications, 'certifications');
  requireAtLeast(days, 0, 'window');
  return certifications
    .map((certification) => ({
      person: certification.person,
      due: nextDue(certification, day).filter((item) => item.daysLeft <= days),
    }))
    .filter((entry) => entry.due.length > 0);
}
