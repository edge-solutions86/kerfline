import { BadInput, NotFound } from '../errors.js';
import { isFiniteNumber } from '../util/num.js';

/**
 * The film the shop is qualified to shoot on.
 *
 * Speed is quoted relative to the middle of the range rather than in any
 * absolute unit, because that is the only form the number is any use in: a
 * technique worked out on one film moves to another by the ratio of the two
 * speeds and nothing else. A slower film has finer grain and resolves more,
 * so the trade is always exposure time against what the reader can see.
 *
 * The class letters are the graded system the code writes acceptance in.
 * Class I is the finest, class III the coarsest, and a code paragraph that
 * calls for class I has ruled out three of the six films below on its own.
 */

const FILMS = [
  { id: 'D2', label: 'very fine grain', speed: 0.06, grade: 'SPECIAL', grain: 'ultrafine' },
  {
    id: 'D3',
    label: 'fine grain high contrast',
    speed: 0.12,
    grade: 'SPECIAL',
    grain: 'ultrafine',
  },
  { id: 'D4', label: 'fine grain', speed: 0.25, grade: 'I', grain: 'fine' },
  { id: 'D5', label: 'medium grain high contrast', speed: 0.5, grade: 'I', grain: 'fine' },
  { id: 'D7', label: 'medium grain', speed: 1, grade: 'II', grain: 'medium' },
  { id: 'D8', label: 'coarse grain fast', speed: 2, grade: 'III', grain: 'coarse' },
];

const BY_ID = new Map(FILMS.map((f) => [f.id, Object.freeze(f)]));
const GRADES = ['SPECIAL', 'I', 'II', 'III'];

export function films() {
  return [...BY_ID.values()];
}

export function filmIds() {
  return [...BY_ID.keys()];
}

export function film(id) {
  if (typeof id !== 'string') {
    throw new BadInput('film must be named by a string', { got: id });
  }
  const found = BY_ID.get(id.trim().toUpperCase());
  if (!found) {
    throw new NotFound(`no film ${id} on the qualified list`, { asked: id, known: filmIds() });
  }
  return found;
}

export function filmSpeed(id) {
  return film(id).speed;
}

export function filmGrade(id) {
  return film(id).grade;
}

export function grades() {
  return [...GRADES];
}

/**
 * True when a film meets a called for class. A finer film always satisfies a
 * call for a coarser one, which is the direction that catches people out: a
 * job asking for class II accepts D4 and D5 and refuses D8.
 */
export function meetsGrade(id, calledFor) {
  const wanted = GRADES.indexOf(calledFor);
  if (wanted === -1) {
    throw new BadInput(`no such film class ${calledFor}`, { calledFor, known: GRADES });
  }
  return GRADES.indexOf(filmGrade(id)) <= wanted;
}

export function filmsMeeting(calledFor) {
  return films().filter((f) => meetsGrade(f.id, calledFor));
}

/**
 * Move an exposure from one film to another. A slower film needs a longer
 * exposure by exactly the ratio of the two speeds.
 */
export function convertExposure(exposure, fromId, toId) {
  if (!isFiniteNumber(exposure) || exposure < 0) {
    throw new BadInput('exposure must not be negative', { got: exposure });
  }
  return (exposure * filmSpeed(fromId)) / filmSpeed(toId);
}

/** The fastest qualified film that still meets a called for class. */
export function fastestMeeting(calledFor) {
  const candidates = filmsMeeting(calledFor);
  if (candidates.length === 0) {
    throw new NotFound(`no qualified film meets class ${calledFor}`, { calledFor });
  }
  return candidates.reduce((best, f) => (f.speed > best.speed ? f : best));
}
