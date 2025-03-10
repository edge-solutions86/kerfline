import { BadInput } from '../errors.js';
import { requireArray } from '../util/guard.js';
import { requireDay } from '../util/dates.js';
import { append } from '../store/journal.js';
import { checkIdentification, backscatterVerdict } from '../film/identification.js';
import { judgeDensity } from '../density/limits.js';
import { judgeArea } from '../density/variation.js';
import { holeSensitivityFor } from '../iqi/sensitivity.js';
import { selectIqi } from '../iqi/selection.js';
import { holeMultiple } from '../iqi/holetype.js';
import { judge } from '../accept/engine.js';
import { mayInterpret } from '../personnel/cert.js';

/**
 * Reading a film and judging the weld.
 *
 * The order here is the whole point. A film has to pass on its own before
 * anything is said about the weld: identification, density, density variation
 * and the indicator. A weld judged off a film that is too light has been
 * judged on the indications that were visible, and the ones that were not
 * visible are exactly the ones that would have failed it.
 *
 * So a film that fails quality produces a reshoot and no weld verdict at all.
 * Not an accept, not a repair, and not a verdict with a caveat on it: the
 * question was not answered and the record says so.
 */

const QUALITY_CHECKS = ['identification', 'density', 'variation', 'sensitivity', 'backscatter'];

export function qualityChecks() {
  return [...QUALITY_CHECKS];
}

/**
 * Grade one film. Every check runs, so a reshoot is called with the whole
 * list of what was wrong rather than the first thing found.
 */
export function gradeFilm(input) {
  if (typeof input !== 'object' || input === null) {
    throw new BadInput('a film is graded from an object', { got: input });
  }
  const {
    identification = {},
    sourceKind = 'gamma',
    indicatorDensity,
    areaDensities = [],
    indicator = null,
    penetratedThicknessIn,
    backscatter = null,
    indicatorSide = 'source',
  } = input;

  const failures = [];

  const ident = checkIdentification(identification);
  if (!ident.complete) {
    const what = ident.missing.length > 0 ? ident.missing.join(', ') : 'an unreadable date';
    failures.push({ check: 'identification', reason: `the film is missing ${what}` });
  }

  let densityVerdict = null;
  if (indicatorDensity === undefined || indicatorDensity === null) {
    failures.push({ check: 'density', reason: 'no density was read at the indicator' });
  } else {
    densityVerdict = judgeDensity(indicatorDensity, sourceKind);
    if (!densityVerdict.pass) {
      failures.push({
        check: 'density',
        reason: `the film reads ${densityVerdict.verdict} at ${indicatorDensity}`,
      });
    }
  }

  let areaVerdict = null;
  if (densityVerdict && areaDensities.length > 0) {
    areaVerdict = judgeArea(areaDensities, indicatorDensity);
    if (!areaVerdict.pass) {
      failures.push({
        check: 'variation',
        reason: `${areaVerdict.failures.length} reading(s) outside the variation band`,
      });
    }
  }

  let sensitivityVerdict = null;
  if (indicator === null) {
    failures.push({ check: 'sensitivity', reason: 'no indicator was seen on the film' });
  } else {
    // The rule is not a computed percentage. It is that the indicator the
    // table calls for at this thickness was on the film and its essential
    // hole was visible. A thinner plaque, or a smaller hole than the
    // essential one, is more demanding and passes; a thicker plaque or a
    // bigger hole does not. The equivalent percentage is carried alongside
    // because it is what puts a hole reading and a wire reading on one scale,
    // but nothing is judged on it.
    const required = selectIqi(penetratedThicknessIn, indicatorSide);
    const plaqueOk = indicator.identity <= required.holeIdentity;
    const holeOk = holeMultiple(indicator.hole) <= holeMultiple(required.essentialHole);
    sensitivityVerdict = {
      seen: { identity: indicator.identity, hole: indicator.hole },
      required: { identity: required.holeIdentity, hole: required.essentialHole },
      equivalentPercent: holeSensitivityFor({
        identity: indicator.identity,
        hole: indicator.hole,
        thicknessIn: penetratedThicknessIn,
      }),
      pass: plaqueOk && holeOk,
    };
    if (!sensitivityVerdict.pass) {
      failures.push({
        check: 'sensitivity',
        reason: `the film shows ${indicator.identity} ${indicator.hole} where ${required.holeIdentity} ${required.essentialHole} is required`,
      });
    }
  }

  let backscatterResult = null;
  if (backscatter !== null) {
    backscatterResult = backscatterVerdict(backscatter);
    if (!backscatterResult.pass) {
      failures.push({ check: 'backscatter', reason: backscatterResult.reason });
    }
  }

  return {
    acceptable: failures.length === 0,
    failures,
    density: densityVerdict,
    area: areaVerdict,
    sensitivity: sensitivityVerdict,
    backscatter: backscatterResult,
  };
}

/**
 * Interpret a joint. Runs the film quality gate first and only judges the
 * weld once every film has passed it.
 */
export function interpretJoint(input) {
  if (typeof input !== 'object' || input === null) {
    throw new BadInput('an interpretation is built from an object', { got: input });
  }
  const {
    joint,
    day,
    films = [],
    indications = [],
    code = 'API1104',
    reader = null,
    certification = null,
    ...options
  } = input;

  requireDay(day, 'interpretation date');
  requireArray(films, 'films');
  requireArray(indications, 'indications');
  if (typeof joint !== 'object' || joint === null) {
    throw new BadInput('an interpretation needs a joint', { got: joint });
  }

  if (certification) {
    const allowed = mayInterpret(certification, day);
    if (!allowed.allowed) {
      throw new BadInput(
        `${reader ?? 'the reader'} may not interpret film: ${allowed.reason}`,
        {
          reader,
          reason: allowed.reason,
        }
      );
    }
  }

  const graded = films.map((film) => ({ film: film.film ?? null, ...gradeFilm(film) }));
  const rejected = graded.filter((film) => !film.acceptable);

  if (films.length === 0) {
    return {
      jointId: joint.id ?? null,
      day,
      reader,
      films: graded,
      outcome: 'reshoot',
      verdict: null,
      reasons: ['no films were presented'],
    };
  }

  if (rejected.length > 0) {
    return {
      jointId: joint.id ?? null,
      day,
      reader,
      films: graded,
      outcome: 'reshoot',
      verdict: null,
      reasons: rejected.flatMap((film) =>
        film.failures.map((failure) => `${film.film ?? 'film'}: ${failure.reason}`)
      ),
    };
  }

  const verdict = judge({ code, indications, joint, ...options });
  return {
    jointId: joint.id ?? null,
    day,
    reader,
    films: graded,
    outcome: verdict.disposition,
    verdict,
    reasons: verdict.reasons ?? [],
  };
}

/** Write the interpretation and, when there is one, the verdict. */
export function recordInterpretation(journal, result) {
  if (typeof result !== 'object' || result === null || !result.jointId) {
    throw new BadInput('an interpretation record needs a joint', { got: result });
  }
  const withInterpretation = append(journal, {
    kind: 'interpretation',
    day: result.day,
    subject: result.jointId,
    payload: {
      reader: result.reader,
      films: result.films.length,
      filmsRejected: result.films.filter((film) => !film.acceptable).length,
      outcome: result.outcome,
      reasons: result.reasons,
    },
  });

  if (!result.verdict) return withInterpretation;

  return append(withInterpretation, {
    kind: 'verdict',
    day: result.day,
    subject: result.jointId,
    payload: {
      disposition: result.verdict.disposition,
      code: result.verdict.code,
      clause: result.verdict.clause,
      reasons: result.verdict.reasons ?? [],
    },
  });
}

/** Interpret and record in one step. */
export function interpret(journal, input) {
  const result = interpretJoint(input);
  return { journal: recordInterpretation(journal, result), result };
}
