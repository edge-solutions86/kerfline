import { describe, it, expect } from 'vitest';
import { makeJournal, appendAll } from '../../src/store/journal.js';
import {
  where,
  byKind,
  bySubject,
  between,
  subjects,
  timeline,
  latestPerSubject,
  latestFor,
  groupBy,
  countByKind,
  currentDispositions,
  awaitingVerdict,
  coveredSpan,
  mergeEntries,
} from '../../src/store/query.js';

const entry = (kind, day, subject, payload = {}) => ({ kind, day, subject, payload });

const journal = appendAll(makeJournal('C-4471'), [
  entry('joint', '2025-03-10', 'J-2', { wallIn: 0.28 }),
  entry('joint', '2025-03-10', 'J-10', { wallIn: 0.28 }),
  entry('shot', '2025-03-11', 'J-2'),
  entry('shot', '2025-03-11', 'J-10'),
  entry('verdict', '2025-03-12', 'J-2', { disposition: 'repair' }),
  entry('repair', '2025-03-14', 'J-2', { label: 'R1' }),
  entry('verdict', '2025-03-16', 'J-2', { disposition: 'accept' }),
]);

describe('the basic filters', () => {
  it('filters on any predicate', () => {
    expect(where(journal, (e) => e.day === '2025-03-11')).toHaveLength(2);
  });

  it('filters by kind and by subject', () => {
    expect(byKind(journal, 'verdict')).toHaveLength(2);
    expect(bySubject(journal, 'J-10')).toHaveLength(2);
  });

  it('filters by day span, both ends included', () => {
    expect(between(journal, '2025-03-11', '2025-03-12')).toHaveLength(3);
    expect(between(journal, '2025-03-10', '2025-03-10')).toHaveLength(2);
  });

  it('finds nothing outside the span', () => {
    expect(between(journal, '2025-04-01', '2025-04-30')).toEqual([]);
  });

  it('refuses a predicate that is not a function', () => {
    expect(() => where(journal, 'verdict')).toThrow(/needs a predicate/);
  });

  it('refuses something that is not a journal', () => {
    expect(() => byKind({}, 'verdict')).toThrow(/not a journal/);
  });
});

describe('subjects and timeline', () => {
  it('lists the subjects in register order', () => {
    expect(subjects(journal)).toEqual(['J-2', 'J-10']);
  });

  it('walks one joint history oldest first', () => {
    const history = timeline(journal, 'J-2');
    expect(history.map((e) => e.kind)).toEqual([
      'joint',
      'shot',
      'verdict',
      'repair',
      'verdict',
    ]);
  });

  it('is empty for a subject nobody recorded', () => {
    expect(timeline(journal, 'J-99')).toEqual([]);
  });
});

describe('latestPerSubject', () => {
  it('takes the last verdict and not every one', () => {
    const latest = latestPerSubject(journal, 'verdict');
    expect(latest).toHaveLength(1);
    expect(latest[0].payload.disposition).toBe('accept');
  });

  it('counts a reopened joint once rather than three times', () => {
    expect(byKind(journal, 'verdict')).toHaveLength(2);
    expect(latestPerSubject(journal, 'verdict')).toHaveLength(1);
  });

  it('sorts by subject in register order', () => {
    const latest = latestPerSubject(journal, 'joint');
    expect(latest.map((e) => e.subject)).toEqual(['J-2', 'J-10']);
  });

  it('finds the last of a kind for one subject', () => {
    expect(latestFor(journal, { subject: 'J-2', kind: 'verdict' }).payload.disposition).toBe(
      'accept'
    );
  });

  it('answers null when a subject has no entry of that kind', () => {
    expect(latestFor(journal, { subject: 'J-10', kind: 'verdict' })).toBe(null);
  });
});

describe('groupBy and countByKind', () => {
  it('groups on any key', () => {
    const groups = groupBy(journal, (e) => e.day);
    expect(groups.get('2025-03-10')).toHaveLength(2);
    expect(groups.size).toBe(5);
  });

  it('keeps journal order inside a group', () => {
    const groups = groupBy(journal, (e) => e.subject);
    expect(groups.get('J-2').map((e) => e.seq)).toEqual([1, 3, 5, 6, 7]);
  });

  it('counts the kinds', () => {
    expect(countByKind(journal)).toEqual({ joint: 2, shot: 2, verdict: 2, repair: 1 });
  });

  it('counts nothing in an empty journal', () => {
    expect(countByKind(makeJournal('C-0'))).toEqual({});
  });

  it('refuses a key that is not a function', () => {
    expect(() => groupBy(journal, 'day')).toThrow(/needs a key function/);
  });
});

describe('currentDispositions', () => {
  it('reports one row per joint with a verdict', () => {
    const rows = currentDispositions(journal);
    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject({ subject: 'J-2', disposition: 'accept' });
  });

  it('reports the latest and not the first', () => {
    expect(currentDispositions(journal)[0].day).toBe('2025-03-16');
  });

  it('leaves the disposition null when the entry carries none', () => {
    const bare = appendAll(makeJournal('C-1'), [entry('verdict', '2025-03-01', 'J-1')]);
    expect(currentDispositions(bare)[0].disposition).toBe(null);
  });
});

describe('awaitingVerdict', () => {
  it('names joints shot but never judged', () => {
    expect(awaitingVerdict(journal)).toEqual(['J-10']);
  });

  it('is empty once everything has a verdict', () => {
    const done = appendAll(journal, [
      entry('verdict', '2025-03-17', 'J-10', { disposition: 'accept' }),
    ]);
    expect(awaitingVerdict(done)).toEqual([]);
  });

  it('does not name a joint that was never shot', () => {
    const planned = appendAll(makeJournal('C-2'), [entry('joint', '2025-03-01', 'J-1')]);
    expect(awaitingVerdict(planned)).toEqual([]);
  });
});

describe('coveredSpan', () => {
  it('runs from the first entry to the last', () => {
    expect(coveredSpan(journal)).toEqual({ from: '2025-03-10', to: '2025-03-16' });
  });

  it('is null for an empty journal', () => {
    expect(coveredSpan(makeJournal('C-0'))).toBe(null);
  });
});

describe('mergeEntries', () => {
  const other = appendAll(makeJournal('C-4472'), [
    entry('joint', '2025-03-09', 'K-1'),
    entry('shot', '2025-03-13', 'K-1'),
  ]);

  it('interleaves two jobs in day order', () => {
    const merged = mergeEntries([journal, other]);
    expect(merged[0].day).toBe('2025-03-09');
    expect(merged.map((e) => e.day)).toEqual([...merged.map((e) => e.day)].sort());
  });

  it('tags each entry with the job it came from', () => {
    const merged = mergeEntries([journal, other]);
    expect(new Set(merged.map((e) => e.job))).toEqual(new Set(['C-4471', 'C-4472']));
  });

  it('keeps entry order inside a day', () => {
    const merged = mergeEntries([journal]);
    const sameDay = merged.filter((e) => e.day === '2025-03-10');
    expect(sameDay.map((e) => e.seq)).toEqual([1, 2]);
  });

  it('refuses anything that is not a list of journals', () => {
    expect(() => mergeEntries(journal)).toThrow(/must be an array/);
  });
});
