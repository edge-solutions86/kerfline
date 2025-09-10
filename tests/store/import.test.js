import { describe, it, expect } from 'vitest';
import {
  parseLine,
  parseJournal,
  verifyImported,
  importJournal,
  surveyFile,
} from '../../src/store/import.js';
import { makeJournal, appendAll, serialise } from '../../src/store/journal.js';

const entry = (kind, day, subject, payload = {}) => ({ kind, day, subject, payload });

const journal = appendAll(makeJournal('C-4471'), [
  entry('joint', '2025-03-10', 'J-118', { wallIn: 0.28 }),
  entry('shot', '2025-03-11', 'J-118', { films: 3 }),
  entry('verdict', '2025-03-12', 'J-118', { disposition: 'accept' }),
]);

const file = serialise(journal);

describe('parseLine', () => {
  const good = file.split('\n')[0];

  it('reads a line the serialiser wrote', () => {
    const parsed = parseLine(good, 1);
    expect(parsed.kind).toBe('joint');
    expect(parsed.subject).toBe('J-118');
    expect(parsed.payload.wallIn).toBe(0.28);
  });

  it('names the line number on an empty line', () => {
    expect(() => parseLine('', 7)).toThrow(/line 7 is empty/);
  });

  it('names the line number on something that is not JSON', () => {
    expect(() => parseLine('not a record', 7)).toThrow(/line 7 is not readable/);
  });

  it('refuses an array or a bare value', () => {
    expect(() => parseLine('[1,2]', 3)).toThrow(/line 3 is not a record/);
    expect(() => parseLine('42', 3)).toThrow(/line 3 is not a record/);
  });

  it('names the field that is missing', () => {
    expect(() => parseLine('{"seq":1,"kind":"joint","day":"2025-03-10"}', 4)).toThrow(
      /line 4 has no subject/
    );
  });

  it('refuses a bad sequence number', () => {
    const bad = JSON.stringify({
      seq: 0,
      kind: 'joint',
      day: '2025-03-10',
      subject: 'J-1',
      checksum: 'x',
    });
    expect(() => parseLine(bad, 2)).toThrow(/bad sequence number/);
  });

  it('refuses a kind the journal does not carry', () => {
    const bad = JSON.stringify({
      seq: 1,
      kind: 'invoice',
      day: '2025-03-10',
      subject: 'J-1',
      checksum: 'x',
    });
    expect(() => parseLine(bad, 2)).toThrow(/unknown kind invoice/);
  });

  it('refuses a date that is not a real day', () => {
    const bad = JSON.stringify({
      seq: 1,
      kind: 'joint',
      day: '2025-02-30',
      subject: 'J-1',
      checksum: 'x',
    });
    expect(() => parseLine(bad, 5)).toThrow(/line 5 has a date that is not a real day/);
  });

  it('defaults a missing payload to nothing rather than refusing', () => {
    const bare = JSON.stringify({
      seq: 1,
      kind: 'note',
      day: '2025-03-10',
      subject: 'J-1',
      checksum: 'x',
    });
    expect(parseLine(bare, 1).payload).toEqual({});
  });
});

describe('parseJournal', () => {
  it('reads back what the serialiser wrote', () => {
    const back = parseJournal(file, 'C-4471');
    expect(back.entries).toHaveLength(3);
    expect(back.name).toBe('C-4471');
  });

  it('ignores blank lines', () => {
    expect(parseJournal(`${file}\n\n`, 'C-4471').entries).toHaveLength(3);
  });

  it('reads an empty file as an empty journal', () => {
    expect(parseJournal('', 'C-4471').entries).toEqual([]);
  });

  it('refuses a file whose lines are out of sequence', () => {
    const reordered = file.split('\n').reverse().join('\n');
    expect(() => parseJournal(reordered, 'C-4471')).toThrow(/line 1 is numbered 3/);
  });

  it('refuses an import with no job name', () => {
    expect(() => parseJournal(file, ' ')).toThrow(/needs a job name/);
  });

  it('refuses something that is not text', () => {
    expect(() => parseJournal(42, 'C-4471')).toThrow(/is text/);
  });
});

describe('verifyImported', () => {
  it('finds an untouched file intact', () => {
    expect(verifyImported(parseJournal(file, 'C-4471')).intact).toBe(true);
  });

  it('finds an empty file intact', () => {
    expect(verifyImported(parseJournal('', 'C-4471')).intact).toBe(true);
  });

  it('catches a payload edited in the file', () => {
    const edited = file.replace('"disposition":"accept"', '"disposition":"reject"');
    const check = verifyImported(parseJournal(edited, 'C-4471'));
    expect(check.intact).toBe(false);
    expect(check.brokenAt).toBe(3);
  });

  it('catches an edit in the middle and points at it', () => {
    const edited = file.replace('"films":3', '"films":9');
    expect(verifyImported(parseJournal(edited, 'C-4471')).brokenAt).toBe(2);
  });

  it('catches a subject that was changed', () => {
    const edited = file.replace('"subject":"J-118"', '"subject":"J-119"');
    expect(verifyImported(parseJournal(edited, 'C-4471')).intact).toBe(false);
  });
});

describe('importJournal', () => {
  it('imports a good file', () => {
    const back = importJournal(file, 'C-4471');
    expect(back.entries).toHaveLength(3);
  });

  it('refuses an edited file rather than importing it', () => {
    const edited = file.replace('"films":3', '"films":9');
    expect(() => importJournal(edited, 'C-4471')).toThrow(/the file has been edited/);
  });

  it('round trips through the serialiser', () => {
    const back = importJournal(file, 'C-4471');
    expect(back.entries.map((e) => e.checksum)).toEqual(journal.entries.map((e) => e.checksum));
  });
});

describe('surveyFile', () => {
  it('says what a good file holds', () => {
    const survey = surveyFile(file);
    expect(survey.lines).toBe(3);
    expect(survey.readable).toBe(true);
    expect(survey.kinds).toEqual({ joint: 1, shot: 1, verdict: 1 });
  });

  it('reads every line before reporting, rather than stopping at the first', () => {
    const broken = ['nonsense', 'also nonsense'].join('\n');
    const survey = surveyFile(broken);
    expect(survey.problems).toHaveLength(2);
    expect(survey.readable).toBe(false);
  });

  it('names the line numbers of the problems', () => {
    const mixed = [file.split('\n')[0], 'nonsense'].join('\n');
    const survey = surveyFile(mixed);
    expect(survey.problems[0].line).toBe(2);
  });

  it('surveys an empty file', () => {
    expect(surveyFile('')).toEqual({ lines: 0, kinds: {}, problems: [], readable: true });
  });
});
