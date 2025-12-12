import { BadInput } from '../errors.js';
import { round } from '../util/num.js';
import { requireArray, requireLabel } from '../util/guard.js';
import { requireDay } from '../util/dates.js';
import { append } from '../store/journal.js';
import { byKind } from '../store/query.js';
import { reshootFactor } from '../exposure/aim.js';
import { rateOf } from '../util/rate.js';

/**
 * What to change before shooting it again.
 *
 * A reshoot is only useful if something is different about it, and which
 * something depends on what went wrong. A light film wants more time. A film
 * with no indicator on it wants the indicator put on, and exactly the same
 * exposure. A film that failed on backscatter wants lead behind the cassette
 * and nothing else touched.
 *
 * The failure worth being careful about is density variation. A film out at
 * one end is fixed by an extra exposure at that end, and a film out at both
 * ends is a technique that cannot cover the section, which means a different
 * film or a different source and not another go at the same setup. Shooting
 * that one again unchanged is the commonest wasted film there is.
 */

const CORRECTIONS = {
  identification: {
    changes: ['lay the missing identification in lead before the shot'],
    exposureFactor: 1,
    techniqueChange: false,
  },
  density: {
    changes: [],
    exposureFactor: null,
    techniqueChange: false,
  },
  variation: {
    changes: ['add an exposure at the end that ran outside the band'],
    exposureFactor: 1,
    techniqueChange: false,
  },
  sensitivity: {
    changes: ['fit the indicator the table calls for and check it is in the beam'],
    exposureFactor: 1,
    techniqueChange: false,
  },
  backscatter: {
    changes: ['put lead behind the cassette and reshoot with the B in place'],
    exposureFactor: 1,
    techniqueChange: false,
  },
};

export function correctionFor(check) {
  const found = CORRECTIONS[check];
  if (!found) {
    throw new BadInput(`no correction is known for a ${check} failure`, {
      check,
      known: Object.keys(CORRECTIONS),
    });
  }
  return { check, ...found, changes: [...found.changes] };
}

/**
 * Plan the reshoot from a graded film. Returns the changes and the exposure
 * to use, or says plainly that the technique itself has to change.
 */
export function planReshoot({ grade, minutes, filmId = 'D7', wantedDensity = 2.5 }) {
  if (typeof grade !== 'object' || grade === null || !Array.isArray(grade.failures)) {
    throw new BadInput('a reshoot is planned from a graded film', { got: grade });
  }
  if (grade.acceptable) {
    return { needed: false, changes: [], minutes, techniqueChange: false };
  }

  const changes = [];
  let factor = 1;
  let techniqueChange = false;

  for (const failure of grade.failures) {
    const correction = correctionFor(failure.check);
    changes.push(...correction.changes);

    if (failure.check === 'density' && grade.density) {
      factor *= reshootFactor({
        filmId,
        gotDensity: grade.density.density,
        wantedDensity,
      });
      changes.push(
        grade.density.verdict === 'light'
          ? 'lengthen the exposure, the film came out light'
          : 'shorten the exposure, the film came out dark'
      );
    }

    if (failure.check === 'variation' && grade.area) {
      const ends = new Set(grade.area.failures.map((reading) => reading.at));
      if (ends.size > 2 || [...ends].some((at) => at === null)) {
        techniqueChange = true;
        changes.push('the section is wider than this film covers, change film or source');
      }
    }
  }

  return {
    needed: true,
    changes: [...new Set(changes)],
    exposureFactor: round(factor, 4),
    minutes: minutes === undefined ? undefined : round(minutes * factor, 3),
    techniqueChange,
  };
}

/** Write the reshoot into the journal against the joint it belongs to. */
export function recordReshoot(journal, { jointId, day, plan, film = null }) {
  requireDay(day, 'reshoot date');
  requireLabel(jointId, 'a reshoot names the joint it is on');
  return append(journal, {
    kind: 'shot',
    day,
    subject: jointId.trim(),
    payload: {
      reshoot: true,
      film,
      changes: plan.changes,
      exposureFactor: plan.exposureFactor ?? 1,
      techniqueChange: plan.techniqueChange ?? false,
    },
  });
}

/**
 * How much of the shooting is being done twice. A rate that climbs is a
 * technique or a darkroom problem rather than bad luck, and it is one of the
 * few numbers that says so early.
 */
export function reshootRate(journal) {
  const shots = byKind(journal, 'shot');
  const reshoots = shots.filter((entry) => entry.payload.reshoot === true);
  return {
    shots: shots.length,
    reshoots: reshoots.length,
    rate: rateOf(reshoots.length, shots.length),
  };
}

/** The reasons reshoots are being called, commonest first. */
export function reshootReasons(journal) {
  const counts = new Map();
  for (const entry of byKind(journal, 'shot')) {
    if (entry.payload.reshoot !== true) continue;
    for (const change of entry.payload.changes ?? []) {
      counts.set(change, (counts.get(change) ?? 0) + 1);
    }
  }
  return [...counts.entries()]
    .map(([change, count]) => ({ change, count }))
    .sort((a, b) => b.count - a.count || a.change.localeCompare(b.change));
}

/** Joints shot more times than a job should need. */
export function repeatedlyShot(journal, limit = 2) {
  requireArray(journal.entries, 'entries');
  const counts = new Map();
  for (const entry of byKind(journal, 'shot')) {
    counts.set(entry.subject, (counts.get(entry.subject) ?? 0) + 1);
  }
  return [...counts.entries()]
    .filter(([, count]) => count > limit)
    .map(([jointId, count]) => ({ jointId, shots: count }))
    .sort((a, b) => b.shots - a.shots);
}
