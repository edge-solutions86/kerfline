import { describe, it, expect } from 'vitest';
import {
  entryKinds,
  makeJournal,
  append,
  appendAll,
  verify,
  entryCount,
  entryAt,
  replay,
  serialise,
} from '../../src/store/journal.js';

const entry = (kind, day, subject, payload = {}) => ({ kind, day, subject, payload });

function filled() {
  return appendAll(makeJournal('C-4471'), [
    entry('joint', '2025-03-10', 'J-118', { wallIn: 0.28 }),
    entry('shot', '2025-03-11', 'J-118', { film: 'D7' }),
    entry('verdict', '2025-03-12', 'J-118', { disposition: 'accept' }),
  ]);
}

describe('makeJournal', () => {
  it('starts empty', () => {
    const journal = makeJournal('C-4471');
    expect(journal.name).toBe('C-4471');
    expect(entryCount(journal)).toBe(0);
  });

  it('refuses a journal with no job on it', () => {
    expect(() => makeJournal('  ')).toThrow(/named after the job/);
    expect(() => makeJournal(null)).toThrow(/named after the job/);
  });

  it('names the kinds an entry can be', () => {
    expect(entryKinds()).toContain('joint');
    expect(entryKinds()).toContain('verdict');
    expect(entryKinds()).toContain('survey');
  });
});

describe('append', () => {
  it('numbers entries from one', () => {
    const journal = filled();
    expect(journal.entries.map((e) => e.seq)).toEqual([1, 2, 3]);
  });

  it('leaves the journal it was given alone', () => {
    const before = makeJournal('C-4471');
    append(before, entry('joint', '2025-03-10', 'J-118'));
    expect(entryCount(before)).toBe(0);
  });

  it('freezes an entry so it cannot be edited in place', () => {
    const journal = filled();
    expect(() => {
      journal.entries[0].subject = 'J-999';
    }).toThrow();
    expect(() => {
      journal.entries[0].payload.wallIn = 9;
    }).toThrow();
  });

  it('copies the payload rather than holding the caller object', () => {
    const payload = { wallIn: 0.28 };
    const journal = append(
      makeJournal('C-4471'),
      entry('joint', '2025-03-10', 'J-118', payload)
    );
    payload.wallIn = 9;
    expect(journal.entries[0].payload.wallIn).toBe(0.28);
  });

  it('refuses a kind it does not carry', () => {
    expect(() =>
      append(makeJournal('C-4471'), entry('invoice', '2025-03-10', 'J-118'))
    ).toThrow(/a journal entry is one of/);
  });

  it('refuses an entry with no subject', () => {
    expect(() => append(makeJournal('C-4471'), entry('joint', '2025-03-10', ''))).toThrow(
      /names what it is about/
    );
  });

  it('refuses an entry dated before the one it follows', () => {
    const journal = filled();
    expect(() => append(journal, entry('note', '2025-03-01', 'J-118'))).toThrow(
      /dated before the one it follows/
    );
  });

  it('allows an entry on the same day as the one before', () => {
    const journal = filled();
    expect(entryCount(append(journal, entry('note', '2025-03-12', 'J-118')))).toBe(4);
  });

  it('refuses something that is not a journal', () => {
    expect(() => append({}, entry('joint', '2025-03-10', 'J-118'))).toThrow(/not a journal/);
  });
});

describe('the checksum chain', () => {
  it('reports an untouched journal as intact', () => {
    const check = verify(filled());
    expect(check.intact).toBe(true);
    expect(check.entries).toBe(3);
  });

  it('reports an empty journal as intact', () => {
    expect(verify(makeJournal('C-4471')).intact).toBe(true);
  });

  it('gives every entry a different checksum', () => {
    const sums = filled().entries.map((e) => e.checksum);
    expect(new Set(sums).size).toBe(sums.length);
  });

  it('catches an entry altered in the middle', () => {
    const journal = filled();
    const tampered = {
      name: journal.name,
      entries: journal.entries.map((e) => (e.seq === 2 ? { ...e, subject: 'J-999' } : e)),
    };
    const check = verify(tampered);
    expect(check.intact).toBe(false);
    expect(check.brokenAt).toBe(2);
  });

  it('catches an entry altered only in its payload', () => {
    const journal = filled();
    const tampered = {
      name: journal.name,
      entries: journal.entries.map((e) =>
        e.seq === 3 ? { ...e, payload: { disposition: 'reject' } } : e
      ),
    };
    expect(verify(tampered).brokenAt).toBe(3);
  });

  it('catches an entry removed from the middle', () => {
    const journal = filled();
    const tampered = {
      name: journal.name,
      entries: journal.entries.filter((e) => e.seq !== 2),
    };
    expect(verify(tampered).intact).toBe(false);
  });

  it('breaks at the first bad entry and not somewhere later', () => {
    const journal = filled();
    const tampered = {
      name: journal.name,
      entries: journal.entries.map((e) => (e.seq === 1 ? { ...e, day: '2025-03-09' } : e)),
    };
    expect(verify(tampered).brokenAt).toBe(1);
  });
});

describe('entryAt', () => {
  it('finds an entry by its number', () => {
    expect(entryAt(filled(), 2).kind).toBe('shot');
  });

  it('refuses a number that is not there', () => {
    expect(() => entryAt(filled(), 9)).toThrow(/no entry 9 in C-4471/);
  });
});

describe('replay', () => {
  it('folds the journal into a state', () => {
    const counts = replay(
      filled(),
      (state, entry_) => ({ ...state, [entry_.kind]: (state[entry_.kind] ?? 0) + 1 }),
      {}
    );
    expect(counts).toEqual({ joint: 1, shot: 1, verdict: 1 });
  });

  it('walks the entries in order', () => {
    const order = replay(filled(), (state, entry_) => [...state, entry_.seq], []);
    expect(order).toEqual([1, 2, 3]);
  });

  it('returns the initial state for an empty journal', () => {
    expect(replay(makeJournal('C-4471'), (state) => state, 'untouched')).toBe('untouched');
  });

  it('refuses a reducer that is not a function', () => {
    expect(() => replay(filled(), null, {})).toThrow(/needs a reducer/);
  });
});

describe('serialise', () => {
  it('writes one line per entry', () => {
    expect(serialise(filled()).split('\n')).toHaveLength(3);
  });

  it('is empty for an empty journal', () => {
    expect(serialise(makeJournal('C-4471'))).toBe('');
  });

  it('is stable for the same journal', () => {
    expect(serialise(filled())).toBe(serialise(filled()));
  });

  it('carries the checksum so a file can be checked without rebuilding', () => {
    expect(serialise(filled())).toContain('checksum');
  });
});
