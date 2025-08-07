import { BadInput } from '../errors.js';
import { round } from '../util/num.js';
import { requireArray } from '../util/guard.js';
import { requireDay } from '../util/dates.js';
import { keyValueBlock, renderTable, rule } from '../util/text.js';
import { between } from '../store/query.js';
import { compareIds } from '../util/ids.js';
import { missingSurveys } from '../safety/survey.js';
import { block, heading, page, pageWidth } from './layout.js';

/**
 * The daily field report.
 *
 * One page per crew per day, written at the end of it. What went out, what
 * came back, what was shot and what was read.
 *
 * The part that earns its place is the survey check. Three surveys belong to
 * every day a source left the store, and the day they are missing is never
 * the day somebody notices. Comparing what was recorded against what was
 * required, on the day, while the crew is still in the yard, is the only time
 * that gap is cheap to close.
 */

const WIDTH = pageWidth();

export function buildDaily({ job, day, journal, crew = [], surveys = [], source = null }) {
  requireDay(day, 'report date');
  requireArray(crew, 'crew');
  requireArray(surveys, 'surveys');
  if (typeof journal !== 'object' || journal === null || !Array.isArray(journal.entries)) {
    throw new BadInput('a daily report needs a journal', { got: journal });
  }

  const entries = between(journal, day, day);
  const kinds = {};
  for (const entry of entries) {
    kinds[entry.kind] = (kinds[entry.kind] ?? 0) + 1;
  }

  const shot = entries.filter((entry) => entry.kind === 'shot');
  const verdicts = entries.filter((entry) => entry.kind === 'verdict');
  const films = shot.reduce((total, entry) => total + (entry.payload.films ?? 0), 0);

  const outstanding =
    source === null ? [] : missingSurveys(surveys.filter((s) => s.day === day));

  return {
    job: job ?? null,
    day,
    crew: crew.map((person) => person.name ?? String(person)),
    source,
    entries: entries.length,
    kinds,
    jointsShot: [...new Set(shot.map((entry) => entry.subject))].sort(compareIds),
    films,
    verdicts: verdicts.map((entry) => ({
      joint: entry.subject,
      disposition: entry.payload.disposition ?? null,
    })),
    surveysDone: surveys.filter((survey) => survey.day === day).map((survey) => survey.kind),
    surveysMissing: outstanding,
    complete: outstanding.length === 0,
  };
}

/** What the day amounted to, as counts. */
export function dailyTotals(report) {
  const repaired = report.verdicts.filter((row) => row.disposition !== 'accept').length;
  return {
    joints: report.jointsShot.length,
    films: report.films,
    read: report.verdicts.length,
    accepted: report.verdicts.length - repaired,
    repaired,
    rate: report.verdicts.length === 0 ? 0 : round(repaired / report.verdicts.length, 4),
  };
}

export function renderDaily(report) {
  const totals = dailyTotals(report);

  const head = [
    heading('DAILY FIELD REPORT', WIDTH),
    keyValueBlock([
      ['Job', report.job ?? ''],
      ['Date', report.day],
      ['Crew', report.crew.join(', ')],
      ['Source', report.source ?? 'none out'],
    ]),
  ].join('\n');

  const work = [
    'WORK',
    rule(WIDTH),
    keyValueBlock([
      ['Joints shot', String(totals.joints)],
      ['Films exposed', String(totals.films)],
      ['Joints read', String(totals.read)],
      ['Accepted', String(totals.accepted)],
      ['Repaired or cut out', String(totals.repaired)],
    ]),
  ].join('\n');

  const readings =
    report.verdicts.length === 0
      ? block('READINGS', 'Nothing read today.', WIDTH)
      : [
          'READINGS',
          rule(WIDTH),
          renderTable(report.verdicts, [
            { key: 'joint', label: 'joint' },
            { key: 'disposition', label: 'result' },
          ]),
        ].join('\n');

  const safety = [
    'SAFETY',
    rule(WIDTH),
    report.source === null
      ? 'No source left the store.'
      : report.complete
        ? `Surveys complete: ${report.surveysDone.join(', ')}`
        : `MISSING SURVEYS: ${report.surveysMissing.join(', ')}`,
  ].join('\n');

  return page([head, work, readings, safety]);
}

/** Days in a span where a source went out and the surveys are short. */
export function daysMissingSurveys(reports) {
  requireArray(reports, 'reports');
  return reports
    .filter((report) => report.source !== null && !report.complete)
    .map((report) => ({ day: report.day, missing: report.surveysMissing }));
}
