import { describe, it, expect } from 'vitest';
import {
  snapshotAt,
  verifySnapshot,
  stateAt,
  diffStates,
  changedBetween,
  activeDays,
  history,
  reopenings,
} from '../../src/store/snapshot.js';
import { makeJournal, appendAll } from '../../src/store/journal.js';

const entry = (kind, day, subject, payload = {}) => ({ kind, day, subject, payload });

const journal = appendAll(makeJournal('C-4471'), [
  entry('shot', '2025-03-10', 'J-2'),
  entry('verdict', '2025-03-11', 'J-2', { disposition: 'repair' }),
  entry('shot', '2025-03-12', 'J-10'),
  entry('verdict', '2025-03-14', 'J-2', { disposition: 'accept' }),
  entry('verdict', '2025-03-14', 'J-10', { disposition: 'accept' }),
  entry('verdict', '2025-03-20', 'J-2', { disposition: 'repair' }),
]);

describe('snapshotAt', () => {
  it('drops the entries written later', () => {
    const snapshot = snapshotAt(journal, '2025-03-12');
    expect(snapshot.entries).toHaveLength(3);
    expect(snapshot.dropped).toBe(3);
  });

  it('keeps entries written on the day itself', () => {
    expect(snapshotAt(journal, '2025-03-14').entries).toHaveLength(5);
  });

  it('is the whole journal at the end', () => {
    const snapshot = snapshotAt(journal, '2025-12-31');
    expect(snapshot.entries).toHaveLength(journal.entries.length);
    expect(snapshot.dropped).toBe(0);
  });

  it('is empty before anything happened', () => {
    expect(snapshotAt(journal, '2025-01-01').entries).toEqual([]);
  });

  it('keeps the job name and records what it is as of', () => {
    const snapshot = snapshotAt(journal, '2025-03-12');
    expect(snapshot.name).toBe('C-4471');
    expect(snapshot.asOf).toBe('2025-03-12');
  });

  it('verifies against the same chain the journal did', () => {
    expect(verifySnapshot(snapshotAt(journal, '2025-03-14')).intact).toBe(true);
    expect(verifySnapshot(snapshotAt(journal, '2025-01-01')).intact).toBe(true);
  });

  it('refuses something that is not a journal', () => {
    expect(() => snapshotAt({}, '2025-03-14')).toThrow(/not a journal/);
  });
});

describe('stateAt', () => {
  it('reads the record as it stood', () => {
    expect(stateAt(journal, '2025-03-11')).toEqual({ 'J-2': 'repair' });
  });

  it('moves as the record moves', () => {
    expect(stateAt(journal, '2025-03-14')).toEqual({ 'J-2': 'accept', 'J-10': 'accept' });
  });

  it('shows a joint reopened later', () => {
    expect(stateAt(journal, '2025-03-20')['J-2']).toBe('repair');
  });

  it('is empty before anything was judged', () => {
    expect(stateAt(journal, '2025-03-10')).toEqual({});
  });

  it('reads in register order', () => {
    expect(Object.keys(stateAt(journal, '2025-03-14'))).toEqual(['J-2', 'J-10']);
  });
});

describe('diffStates', () => {
  it('finds nothing between two identical states', () => {
    const diff = diffStates({ 'J-1': 'accept' }, { 'J-1': 'accept' });
    expect(diff.quiet).toBe(true);
    expect(diff.changed).toEqual([]);
  });

  it('finds a subject that appeared', () => {
    const diff = diffStates({}, { 'J-1': 'accept' });
    expect(diff.added).toEqual([{ subject: 'J-1', disposition: 'accept' }]);
    expect(diff.quiet).toBe(false);
  });

  it('finds a disposition that moved', () => {
    const diff = diffStates({ 'J-1': 'repair' }, { 'J-1': 'accept' });
    expect(diff.changed).toEqual([{ subject: 'J-1', from: 'repair', to: 'accept' }]);
  });

  it('calls ordinary progress progress and not a reopening', () => {
    expect(diffStates({ 'J-1': 'repair' }, { 'J-1': 'accept' }).reopened).toEqual([]);
  });

  it('calls a joint going back from accepted a reopening', () => {
    const diff = diffStates({ 'J-1': 'accept' }, { 'J-1': 'repair' });
    expect(diff.reopened).toHaveLength(1);
  });

  it('finds a subject that vanished', () => {
    const diff = diffStates({ 'J-1': 'accept' }, {});
    expect(diff.removed).toEqual([{ subject: 'J-1', disposition: 'accept' }]);
  });

  it('refuses to compare something that is not a state', () => {
    expect(() => diffStates(null, {})).toThrow(/two states are needed/);
  });
});

describe('changedBetween', () => {
  it('reports what moved over a period', () => {
    const diff = changedBetween(journal, '2025-03-11', '2025-03-14');
    expect(diff.changed).toEqual([{ subject: 'J-2', from: 'repair', to: 'accept' }]);
    expect(diff.added).toEqual([{ subject: 'J-10', disposition: 'accept' }]);
  });

  it('is quiet over a period when nothing moved', () => {
    expect(changedBetween(journal, '2025-03-14', '2025-03-15').quiet).toBe(true);
  });

  it('refuses a period that runs backwards', () => {
    expect(() => changedBetween(journal, '2025-03-14', '2025-03-11')).toThrow(/runs backwards/);
  });
});

describe('activeDays and history', () => {
  it('lists only the days something happened', () => {
    expect(activeDays(journal)).toEqual([
      '2025-03-10',
      '2025-03-11',
      '2025-03-12',
      '2025-03-14',
      '2025-03-20',
    ]);
  });

  it('walks the record day by day', () => {
    const walked = history(journal);
    expect(walked).toHaveLength(activeDays(journal).length);
    expect(walked[0].state).toEqual({});
    expect(walked[walked.length - 1].state['J-2']).toBe('repair');
  });

  it('has nothing to walk on an empty journal', () => {
    expect(history(makeJournal('C-0'))).toEqual([]);
  });
});

describe('reopenings', () => {
  it('finds the joint that went back after being accepted', () => {
    const found = reopenings(journal);
    expect(found).toHaveLength(1);
    expect(found[0]).toMatchObject({ subject: 'J-2', from: 'accept', to: 'repair' });
    expect(found[0].day).toBe('2025-03-20');
  });

  it('finds nothing on a job that only went forwards', () => {
    const clean = appendAll(makeJournal('C-1'), [
      entry('verdict', '2025-03-11', 'J-1', { disposition: 'repair' }),
      entry('verdict', '2025-03-14', 'J-1', { disposition: 'accept' }),
    ]);
    expect(reopenings(clean)).toEqual([]);
  });
});
