import { BadInput } from '../errors.js';
import { requireArray, requireAtLeast, requireOneOf } from '../util/guard.js';
import { requireDay, addDays, daysBetween } from '../util/dates.js';
import { annualLimitMrem } from './dosimetry.js';

/**
 * Radiation incidents and how fast they have to be reported.
 *
 * The reporting clock is the part that matters and it is not proportional to
 * how bad the day felt. A source that failed to retract and was recovered
 * with nobody exposed is reportable inside twenty four hours, because the
 * equipment failed in the way that kills people. A technician who picked up
 * more dose than they should have on a job where everything worked is a
 * thirty day report.
 *
 * So the clock is set by what happened, not by what it cost, and the two
 * come apart often enough that deciding by feel gets it wrong.
 */

const KINDS = ['overexposure', 'disconnect', 'lost_source', 'survey_failure', 'damage'];
const WINDOWS = { immediate: 0, oneDay: 1, thirtyDay: 30 };

// Dose thresholds in millirem, whole body.
const IMMEDIATE_MREM = 25000;
const ONE_DAY_MREM = 5000;

export function incidentKinds() {
  return [...KINDS];
}

export function reportingWindows() {
  return { ...WINDOWS };
}

/**
 * How fast this has to be reported. Equipment failures set the clock on their
 * own, whatever dose came of them, and a dose big enough sets it faster still.
 */
export function classify({ kind, doseMrem = 0 }) {
  requireOneOf(kind, KINDS, 'incident kind');
  requireAtLeast(doseMrem, 0, 'dose');

  if (doseMrem >= IMMEDIATE_MREM) {
    return { window: 'immediate', days: WINDOWS.immediate, because: 'the dose is very large' };
  }

  if (kind === 'disconnect' || kind === 'lost_source') {
    return {
      window: 'oneDay',
      days: WINDOWS.oneDay,
      because: 'the equipment failed in the way that hurts people',
    };
  }

  // The one day dose threshold and the annual whole body limit are the same
  // number, so a dose that exceeds the annual limit is always already a one
  // day report. There is deliberately no separate branch for it: writing one
  // would be unreachable code that reads as though it did something.
  if (doseMrem >= ONE_DAY_MREM) {
    return {
      window: 'oneDay',
      days: WINDOWS.oneDay,
      because:
        doseMrem >= annualLimitMrem()
          ? 'the dose reaches the annual whole body limit'
          : 'the dose is large',
    };
  }

  if (kind === 'survey_failure' || kind === 'damage') {
    return {
      window: 'thirtyDay',
      days: WINDOWS.thirtyDay,
      because: 'a procedure or a piece of equipment failed',
    };
  }

  return { window: 'thirtyDay', days: WINDOWS.thirtyDay, because: 'a dose was picked up' };
}

export function makeIncident({
  reference,
  kind,
  day,
  detail,
  person = null,
  doseMrem = 0,
  source = null,
}) {
  requireDay(day, 'incident date');
  if (typeof reference !== 'string' || reference.trim() === '') {
    throw new BadInput('an incident carries a reference', { got: reference });
  }
  if (typeof detail !== 'string' || detail.trim() === '') {
    throw new BadInput('an incident says what happened', { got: detail });
  }
  const clock = classify({ kind, doseMrem });
  return {
    reference: reference.trim(),
    kind,
    day,
    detail: detail.trim(),
    person,
    doseMrem,
    source,
    ...clock,
    reportBy: addDays(day, clock.days),
    reportedOn: null,
  };
}

export function markReported(incident, day) {
  requireDay(day, 'reporting date');
  if (daysBetween(incident.day, day) < 0) {
    throw new BadInput('an incident cannot be reported before it happened', {
      incident: incident.day,
      day,
    });
  }
  return {
    ...incident,
    reportedOn: day,
    onTime: daysBetween(day, incident.reportBy) >= 0,
    daysLate: Math.max(0, daysBetween(incident.reportBy, day)),
  };
}

export function unreported(incidents) {
  requireArray(incidents, 'incidents');
  return incidents.filter((incident) => incident.reportedOn === null);
}

/** Incidents whose reporting window has run out, most urgent first. */
export function overdueReports(incidents, day) {
  requireDay(day, 'date');
  const order = ['immediate', 'oneDay', 'thirtyDay'];
  return unreported(incidents)
    .filter((incident) => daysBetween(incident.reportBy, day) > 0)
    .map((incident) => ({ ...incident, daysLate: daysBetween(incident.reportBy, day) }))
    .sort(
      (a, b) => order.indexOf(a.window) - order.indexOf(b.window) || b.daysLate - a.daysLate
    );
}

/** What is due inside a window, so nothing is missed by a day. */
export function dueWithin(incidents, { day, days }) {
  requireDay(day, 'date');
  requireAtLeast(days, 0, 'window');
  return unreported(incidents).filter((incident) => {
    const left = daysBetween(day, incident.reportBy);
    return left >= 0 && left <= days;
  });
}
