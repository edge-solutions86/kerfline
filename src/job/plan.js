import { BadInput } from '../errors.js';
import { round, sum } from '../util/num.js';
import { requireArray, requireAbove, requireAtLeast } from '../util/guard.js';
import { requireDay, daysBetween } from '../util/dates.js';
import { planExposure } from '../exposure/technique.js';
import { viewingPlan } from '../geom/viewing.js';
import { exposuresForCircumference } from '../geom/coverage.js';
import { activityOn } from '../safety/sourcelog.js';

/**
 * Planning a day's shooting.
 *
 * The thing that makes this worth computing rather than guessing is that the
 * source decays. The same list of joints that took six hours in March takes
 * longer in May and longer again in July, and a crew that plans off the March
 * number spends the summer finishing in the dark.
 *
 * Setup time is per shot rather than per joint, because moving the camera
 * round the pipe is the work. A joint needing six shots is not six times the
 * exposure of a joint needing one, it is six setups plus six exposures, and
 * the setups are usually the larger half.
 */

const SETUP_MINUTES = 8;
const MOVE_MINUTES = 20;

export function setupMinutes() {
  return SETUP_MINUTES;
}

export function moveMinutes() {
  return MOVE_MINUTES;
}

/** What one joint will cost in minutes on a given day. */
export function jointMinutes({
  joint,
  source,
  day,
  sourceToObjectIn,
  filmId = 'D7',
  screens = 'LEAD_STANDARD',
  targetDensity = 2,
  maxThicknessRatio = 1.2,
  setupPerShot = SETUP_MINUTES,
}) {
  requireDay(day, 'date');
  requireAbove(sourceToObjectIn, 0, 'source to object distance');
  requireAtLeast(setupPerShot, 0, 'setup time');

  const viewing = viewingPlan({ odIn: joint.odIn, wallIn: joint.wallIn });
  const shots = exposuresForCircumference({
    odIn: joint.odIn,
    sourceToObjectIn,
    maxThicknessRatio,
  });
  const exposure = planExposure({
    isotopeId: source.isotope,
    thicknessIn: viewing.penetratedThicknessIn,
    distanceIn: sourceToObjectIn + joint.odIn,
    filmId,
    screens,
    targetDensity,
    assayCi: source.assayCi,
    daysSinceAssay: daysBetween(source.assayDay, day),
  });

  const exposureMinutes = round(exposure.minutes * shots, 2);
  const setupTotal = round(setupPerShot * shots, 2);
  return {
    jointId: joint.id,
    shots,
    activityCi: activityOn(source, day),
    exposureMinutes,
    setupMinutes: setupTotal,
    totalMinutes: round(exposureMinutes + setupTotal, 2),
  };
}

/**
 * Fit as many joints into a day as the hours allow, in the order given. The
 * order is the caller's because the crew walks the line in a sequence that
 * has nothing to do with how long each joint takes.
 */
export function planDay({ joints, source, day, hoursAvailable = 10, ...options }) {
  requireArray(joints, 'joints');
  requireAbove(hoursAvailable, 0, 'hours available');
  const budget = hoursAvailable * 60 - MOVE_MINUTES;

  const scheduled = [];
  const spilled = [];
  let used = 0;

  for (const joint of joints) {
    const cost = jointMinutes({ joint, source, day, ...options });
    if (used + cost.totalMinutes <= budget) {
      scheduled.push(cost);
      used += cost.totalMinutes;
    } else {
      spilled.push(cost);
    }
  }

  return {
    day,
    source: source.serial,
    activityCi: activityOn(source, day),
    hoursAvailable,
    budgetMinutes: round(budget, 2),
    usedMinutes: round(used, 2),
    freeMinutes: round(budget - used, 2),
    scheduled,
    spilled,
    fitsAll: spilled.length === 0,
  };
}

/**
 * The same plan on a later day, so a scheduler can see what the decay costs
 * before the crew finds out in the field.
 */
export function costOverTime({ joints, source, days, ...options }) {
  requireArray(days, 'days');
  return days.map((day) => {
    const plan = planDay({ joints, source, day, ...options });
    return {
      day,
      activityCi: plan.activityCi,
      totalMinutes: round(
        sum([...plan.scheduled, ...plan.spilled].map((entry) => entry.totalMinutes)),
        2
      ),
      fitsAll: plan.fitsAll,
    };
  });
}

/** The last day the whole list still fits inside the hours available. */
export function lastDayThatFits({ joints, source, days, ...options }) {
  const costs = costOverTime({ joints, source, days, ...options });
  const fitting = costs.filter((entry) => entry.fitsAll);
  if (fitting.length === 0) {
    throw new BadInput('the list does not fit on any of those days', { days });
  }
  return fitting[fitting.length - 1].day;
}
