import { BadInput } from '../errors.js';
import { round, maxOf } from '../util/num.js';
import { requireAbove, requireArray, requireAtLeast, requireLabel } from '../util/guard.js';
import { requireDay } from '../util/dates.js';
import { restrictedAreaRate, highRadiationRate } from './boundary.js';

/**
 * Survey records.
 *
 * Three surveys are made around every exposure and they answer different
 * questions. The boundary survey says the rope is in the right place. The
 * perimeter survey during the shot says nobody has walked inside it. The
 * retraction survey says the source went home.
 *
 * That last one is the one that kills people. A source that hangs up in the
 * guide tube reads normal at the boundary, because the crank is out and the
 * shot has ended and everything looks finished, and the only thing that says
 * otherwise is the meter held against the camera as the crew walks back in.
 * So a retraction survey with no reading is not a missing record here, it is
 * a refusal.
 */

const KINDS = ['boundary', 'perimeter', 'retraction'];

export function surveyKinds() {
  return [...KINDS];
}

export function makeReading({ atFt, rateMrPerHour, note = null }) {
  requireAbove(atFt, 0, 'distance');
  requireAtLeast(rateMrPerHour, 0, 'dose rate');
  return { atFt: round(atFt, 1), rateMrPerHour: round(rateMrPerHour, 3), note };
}

export function makeSurvey({ kind, day, instrument, readings, surveyor = null }) {
  if (!KINDS.includes(kind)) {
    throw new BadInput(`a survey is one of ${KINDS.join(', ')}`, { got: kind, known: KINDS });
  }
  requireDay(day, 'survey date');
  requireLabel(instrument, 'a survey names the meter it was taken with');
  requireArray(readings, 'readings');
  if (readings.length === 0) {
    throw new BadInput('a survey with no readings is not a survey', { kind });
  }
  return {
    kind,
    day,
    instrument: instrument.trim(),
    surveyor,
    readings: readings.map((reading) => makeReading(reading)),
  };
}

/**
 * A retraction survey passes when the reading at the camera is down at
 * background. Anything above the threshold means the source is not fully
 * shielded and nobody approaches.
 */
export function judgeRetraction(
  survey,
  { backgroundMrPerHour = 0.02, thresholdMrPerHour = 2 } = {}
) {
  if (survey.kind !== 'retraction') {
    throw new BadInput('that is not a retraction survey', { kind: survey.kind });
  }
  const highest = maxOf(survey.readings.map((reading) => reading.rateMrPerHour));
  const home = highest <= thresholdMrPerHour;
  return {
    highestMrPerHour: highest,
    thresholdMrPerHour,
    backgroundMrPerHour,
    sourceIsHome: home,
    atBackground: highest <= backgroundMrPerHour * 5,
    action: home ? 'approach permitted' : 'do not approach, the source is not shielded',
  };
}

/** A boundary survey passes when nothing outside the rope is over the limit. */
export function judgeBoundary(survey, { boundaryFt }) {
  if (survey.kind !== 'boundary') {
    throw new BadInput('that is not a boundary survey', { kind: survey.kind });
  }
  requireAbove(boundaryFt, 0, 'boundary distance');
  const limit = restrictedAreaRate();
  const outside = survey.readings.filter((reading) => reading.atFt >= boundaryFt);
  const breaches = outside.filter((reading) => reading.rateMrPerHour > limit);
  return {
    boundaryFt,
    limitMrPerHour: limit,
    readingsOutside: outside.length,
    breaches,
    pass: outside.length > 0 && breaches.length === 0,
    reason:
      outside.length === 0
        ? 'no reading was taken at or beyond the rope'
        : breaches.length === 0
          ? null
          : 'the rope is inside the two milliroentgen line',
  };
}

/**
 * Where the rope should move to, from the readings taken. Uses the inverse
 * square through the worst breach rather than assuming the calculated
 * distance, because the whole point of surveying is that the calculation
 * missed some scatter.
 */
export function boundaryFromReadings(survey) {
  requireArray(survey.readings, 'readings');
  const limit = restrictedAreaRate();
  const worst = survey.readings.reduce((a, b) =>
    b.rateMrPerHour * b.atFt ** 2 > a.rateMrPerHour * a.atFt ** 2 ? b : a
  );
  if (worst.rateMrPerHour <= 0) return 0;
  return round(worst.atFt * Math.sqrt(worst.rateMrPerHour / limit), 1);
}

/** Any reading that puts somebody in a high radiation area. */
export function highRadiationReadings(survey) {
  const limit = highRadiationRate();
  return survey.readings.filter((reading) => reading.rateMrPerHour > limit);
}

/** The three surveys a shot needs, and which of them are missing. */
export function missingSurveys(surveys) {
  requireArray(surveys, 'surveys');
  const present = new Set(surveys.map((survey) => survey.kind));
  return KINDS.filter((kind) => !present.has(kind));
}
