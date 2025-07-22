import { describe, it, expect } from 'vitest';
import {
  buildDaily,
  dailyTotals,
  renderDaily,
  daysMissingSurveys,
} from '../../src/report/daily.js';
import { makeJournal, appendAll } from '../../src/store/journal.js';
import { makeSurvey } from '../../src/safety/survey.js';

const entry = (kind, day, subject, payload = {}) => ({ kind, day, subject, payload });

const journal = appendAll(makeJournal('C-4471'), [
  entry('shot', '2025-03-14', 'J-2', { films: 3 }),
  entry('shot', '2025-03-14', 'J-3', { films: 3 }),
  entry('verdict', '2025-03-14', 'J-2', { disposition: 'accept' }),
  entry('verdict', '2025-03-14', 'J-3', { disposition: 'repair' }),
  entry('shot', '2025-03-15', 'J-4', { films: 2 }),
]);

const survey = (kind, day) =>
  makeSurvey({ kind, day, instrument: 'Ludlum 3', readings: [{ atFt: 10, rateMrPerHour: 1 }] });

const allSurveys = ['boundary', 'perimeter', 'retraction'].map((kind) =>
  survey(kind, '2025-03-14')
);

describe('buildDaily', () => {
  const report = buildDaily({
    job: 'C-4471',
    day: '2025-03-14',
    journal,
    crew: [{ name: 'R. Ibarra' }, { name: 'D. Whyte' }],
    surveys: allSurveys,
    source: 'IR-7741',
  });

  it('takes only that day entries', () => {
    expect(report.entries).toBe(4);
  });

  it('lists the joints shot, in register order', () => {
    expect(report.jointsShot).toEqual(['J-2', 'J-3']);
  });

  it('adds the films up', () => {
    expect(report.films).toBe(6);
  });

  it('lists the readings with their dispositions', () => {
    expect(report.verdicts).toEqual([
      { joint: 'J-2', disposition: 'accept' },
      { joint: 'J-3', disposition: 'repair' },
    ]);
  });

  it('records the crew by name', () => {
    expect(report.crew).toEqual(['R. Ibarra', 'D. Whyte']);
  });

  it('is complete when all three surveys were done', () => {
    expect(report.complete).toBe(true);
    expect(report.surveysMissing).toEqual([]);
  });

  it('leaves the next day out', () => {
    expect(report.jointsShot).not.toContain('J-4');
  });

  it('refuses a report with no journal', () => {
    expect(() => buildDaily({ day: '2025-03-14', journal: {} })).toThrow(/needs a journal/);
  });

  it('refuses a date that is not a real day', () => {
    expect(() => buildDaily({ day: '2025-02-30', journal })).toThrow(/not a real calendar day/);
  });
});

describe('the survey check', () => {
  it('names what is missing when a source went out', () => {
    const report = buildDaily({
      day: '2025-03-14',
      journal,
      surveys: [survey('boundary', '2025-03-14')],
      source: 'IR-7741',
    });
    expect(report.complete).toBe(false);
    expect(report.surveysMissing).toEqual(['perimeter', 'retraction']);
  });

  it('asks for nothing when no source left the store', () => {
    const report = buildDaily({ day: '2025-03-14', journal, surveys: [], source: null });
    expect(report.complete).toBe(true);
    expect(report.surveysMissing).toEqual([]);
  });

  it('does not count a survey from another day', () => {
    const report = buildDaily({
      day: '2025-03-14',
      journal,
      surveys: ['boundary', 'perimeter', 'retraction'].map((kind) =>
        survey(kind, '2025-03-13')
      ),
      source: 'IR-7741',
    });
    expect(report.surveysMissing).toHaveLength(3);
  });
});

describe('dailyTotals', () => {
  const report = buildDaily({
    day: '2025-03-14',
    journal,
    surveys: allSurveys,
    source: 'IR-7741',
  });

  it('counts the day', () => {
    const totals = dailyTotals(report);
    expect(totals.joints).toBe(2);
    expect(totals.films).toBe(6);
    expect(totals.read).toBe(2);
    expect(totals.accepted).toBe(1);
    expect(totals.repaired).toBe(1);
  });

  it('reports a rate with the count it came from', () => {
    expect(dailyTotals(report).rate).toBeCloseTo(0.5, 4);
  });

  it('is zero on a day with nothing read', () => {
    const quiet = buildDaily({ day: '2025-03-15', journal, source: null });
    expect(dailyTotals(quiet).rate).toBe(0);
    expect(dailyTotals(quiet).read).toBe(0);
  });
});

describe('renderDaily', () => {
  const report = buildDaily({
    job: 'C-4471',
    day: '2025-03-14',
    journal,
    crew: [{ name: 'R. Ibarra' }],
    surveys: allSurveys,
    source: 'IR-7741',
  });

  it('prints the heading, the job and the date', () => {
    const text = renderDaily(report);
    expect(text).toContain('DAILY FIELD REPORT');
    expect(text).toContain('C-4471');
    expect(text).toContain('2025-03-14');
  });

  it('prints the work and the readings', () => {
    const text = renderDaily(report);
    expect(text).toContain('Films exposed');
    expect(text).toContain('J-2');
    expect(text).toContain('repair');
  });

  it('says the surveys are complete when they are', () => {
    expect(renderDaily(report)).toContain('Surveys complete');
  });

  it('shouts about missing surveys', () => {
    const short = buildDaily({
      day: '2025-03-14',
      journal,
      surveys: [],
      source: 'IR-7741',
    });
    expect(renderDaily(short)).toContain('MISSING SURVEYS');
  });

  it('says so when no source went out', () => {
    const quiet = buildDaily({ day: '2025-03-16', journal, source: null });
    const text = renderDaily(quiet);
    expect(text).toContain('No source left the store.');
    expect(text).toContain('Nothing read today.');
  });

  it('keeps every line inside the paper width', () => {
    for (const line of renderDaily(report).split('\n')) {
      expect(line.length).toBeLessThanOrEqual(78);
    }
  });
});

describe('daysMissingSurveys', () => {
  it('finds the days a source went out and the surveys are short', () => {
    const short = buildDaily({ day: '2025-03-14', journal, surveys: [], source: 'IR-7741' });
    const good = buildDaily({
      day: '2025-03-15',
      journal,
      surveys: ['boundary', 'perimeter', 'retraction'].map((kind) =>
        survey(kind, '2025-03-15')
      ),
      source: 'IR-7741',
    });
    const found = daysMissingSurveys([short, good]);
    expect(found).toHaveLength(1);
    expect(found[0].day).toBe('2025-03-14');
  });

  it('leaves out days when no source went out', () => {
    const quiet = buildDaily({ day: '2025-03-16', journal, source: null });
    expect(daysMissingSurveys([quiet])).toEqual([]);
  });
});
