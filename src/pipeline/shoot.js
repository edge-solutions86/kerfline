import { BadInput } from '../errors.js';
import { requireDay } from '../util/dates.js';
import { buildTechnique } from '../report/technique.js';
import { append } from '../store/journal.js';
import { checkOut } from '../safety/sourcelog.js';
import { marksForShot } from '../film/identification.js';
import { needsBackscatterCheck } from '../exposure/screens.js';

/**
 * Planning and recording a shot.
 *
 * This is the first of the three places where the library stops calculating
 * and starts writing things down. It puts the technique, the source check and
 * the lead marks in one place so that a shot cannot be recorded without the
 * things that had to be true before it was fired.
 *
 * The source check is the part worth insisting on. A camera whose leak test
 * has run out does not become acceptable because the job is behind, so
 * planning a shot with one fails here rather than producing a plan somebody
 * then works from.
 */

export function planShot(input) {
  if (typeof input !== 'object' || input === null) {
    throw new BadInput('a shot is planned from an object', { got: input });
  }
  const {
    job,
    joint,
    day,
    source,
    leakTests = [],
    screens = 'LEAD_STANDARD',
    side = 'source',
  } = input;
  requireDay(day, 'shot date');

  if (typeof source !== 'object' || source === null) {
    throw new BadInput('a shot needs a source', { got: source });
  }

  // Refuses when the leak test has run out or the source has never had one.
  const booking = checkOut({ source, tests: leakTests, day });

  const technique = buildTechnique({
    ...input,
    isotopeId: source.isotope,
    assayCi: booking.activityCi,
    daysSinceAssay: 0,
    screens,
  });

  const shots = (technique.coverage ?? []).map((shot) => ({
    ...shot,
    marks: marksForShot({
      position: String(shot.shot),
      side,
      backscatterCheck: needsBackscatterCheck(screens),
    }),
  }));

  return {
    job: job ?? null,
    jointId: joint.id ?? null,
    day,
    source: { serial: source.serial, isotope: source.isotope, activityCi: booking.activityCi },
    technique,
    shots,
    ready: technique.usable && shots.length > 0,
    problems: technique.problems,
  };
}

/**
 * Write the plan into the journal. A plan that is not ready is still recorded,
 * because the record of having tried and been stopped is worth keeping and is
 * the thing a supervisor is looking for when the joint is still open a week
 * later.
 */
export function recordShot(journal, plan) {
  if (typeof plan !== 'object' || plan === null || !plan.jointId) {
    throw new BadInput('a shot record needs a plan with a joint on it', { got: plan });
  }
  return append(journal, {
    kind: 'shot',
    day: plan.day,
    subject: plan.jointId,
    payload: {
      source: plan.source.serial,
      isotope: plan.source.isotope,
      activityCi: plan.source.activityCi,
      technique: plan.technique.viewing.technique,
      exposureMinutes: plan.technique.exposure ? plan.technique.exposure.minutes : null,
      films: plan.shots.length,
      ready: plan.ready,
      problems: plan.problems,
    },
  });
}

/** Plan and record in one step, which is what the command line does. */
export function shoot(journal, input) {
  const plan = planShot(input);
  return { journal: recordShot(journal, plan), plan };
}

/**
 * The films a plan will produce, as identifiers, so the darkroom can label
 * jackets before the crew comes back.
 */
export function filmLabels(plan) {
  return plan.shots.map((shot) => `${plan.jointId}-${shot.shot}`);
}
