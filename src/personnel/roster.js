import { BadInput } from '../errors.js';
import { requireArray, requireLabel } from '../util/guard.js';
import { requireDay } from '../util/dates.js';
import { mayInterpret, requirementsFor } from './cert.js';

/**
 * Who is on the crew, and whether that crew may work.
 *
 * A radiography crew is never one person. The second body is not there to
 * carry the camera: it is there so that somebody who is not holding the crank
 * is watching the boundary, and so that if something goes wrong there is
 * somebody to go for help who is not the person it went wrong to. So a crew
 * of one is refused here regardless of how well certified that one is.
 *
 * Beyond that the rules are ordinary: at least one certified radiographer,
 * everybody a monitored radiation worker, and the person who signs the film
 * current on the day they sign it.
 */

const ROLES = ['radiographer', 'assistant', 'rso'];
const MINIMUM_CREW = 2;

export function roles() {
  return [...ROLES];
}

export function minimumCrew() {
  return MINIMUM_CREW;
}

export function makePerson({ name, role, certification = null, monitored = false }) {
  requireLabel(name, 'a crew member has a name');
  if (!ROLES.includes(role)) {
    throw new BadInput(`a role is one of ${ROLES.join(', ')}`, { got: role, known: ROLES });
  }
  return { name: name.trim(), role, certification, monitored: Boolean(monitored) };
}

/** Whether one person may read film on a day. */
export function canInterpret(person, day) {
  requireDay(day, 'date');
  if (!person.certification) {
    return { allowed: false, reason: `${person.name} holds no certification` };
  }
  const check = mayInterpret(person.certification, day);
  return {
    allowed: check.allowed,
    reason: check.reason ? `${person.name}: ${check.reason}` : null,
  };
}

export function whoCanInterpret(roster, day) {
  requireArray(roster, 'roster');
  return roster.filter((person) => canInterpret(person, day).allowed);
}

/**
 * Whether a crew may work on a day, and every reason it may not. All the
 * reasons, because a crew short two things gets both fixed in one trip rather
 * than being turned round twice.
 */
export function checkCrew(crew, day) {
  requireArray(crew, 'crew');
  requireDay(day, 'date');
  const reasons = [];

  if (crew.length < MINIMUM_CREW) {
    reasons.push(`a crew is at least ${MINIMUM_CREW} people and this one is ${crew.length}`);
  }

  const readers = crew.filter((person) => canInterpret(person, day).allowed);
  const radiographers = crew.filter((person) => person.role === 'radiographer');
  if (radiographers.length === 0) {
    reasons.push('no radiographer on the crew');
  }
  if (readers.length === 0) {
    reasons.push('nobody on the crew may interpret film today');
  }

  const unmonitored = crew.filter((person) => !person.monitored);
  if (unmonitored.length > 0) {
    reasons.push(
      `not a monitored radiation worker: ${unmonitored.map((p) => p.name).join(', ')}`
    );
  }

  return {
    day,
    size: crew.length,
    readers: readers.map((person) => person.name),
    mayWork: reasons.length === 0,
    reasons,
  };
}

/** The person who should sign the film: the most senior current reader. */
export function signatory(crew, day) {
  const readers = whoCanInterpret(crew, day);
  if (readers.length === 0) return null;
  const rank = (person) => ['I', 'II', 'III'].indexOf(person.certification.level);
  return readers.reduce((best, person) => (rank(person) > rank(best) ? person : best));
}

/** Everybody on the roster whose level permits interpretation at all. */
export function qualifiedLevels(roster) {
  requireArray(roster, 'roster');
  return roster
    .filter((person) => person.certification)
    .filter((person) => requirementsFor(person.certification.level).mayInterpret)
    .map((person) => ({ name: person.name, level: person.certification.level }));
}
