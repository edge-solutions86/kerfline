import { describe, it, expect } from 'vitest';
import {
  requiredFields,
  makeSheet,
  completeness,
  indicationRows,
  renderSheet,
  summarise,
} from '../../src/report/interpretation.js';
import { makeIndication } from '../../src/indication/measure.js';

const full = () =>
  makeSheet({
    job: 'C-4471',
    jointId: 'J-118',
    day: '2025-03-14',
    reader: 'R. Ibarra',
    readerLevel: 'II',
    code: 'API1104',
    technique: 'DWSI',
    wallIn: 0.28,
    films: ['1', '2', '3'],
    indications: [
      makeIndication({ type: 'SLAG', measuredIn: 0.15, at: 8, film: '2' }),
      makeIndication({ type: 'POROSITY', measuredIn: 0.04, at: 3, film: '1' }),
    ],
    verdict: { disposition: 'accept', clause: '9.3', reasons: [] },
  });

describe('makeSheet', () => {
  it('keeps what it was given', () => {
    const sheet = full();
    expect(sheet.jointId).toBe('J-118');
    expect(sheet.films).toEqual(['1', '2', '3']);
    expect(sheet.indications).toHaveLength(2);
  });

  it('leaves the optional fields null rather than undefined', () => {
    const sheet = makeSheet({});
    expect(sheet.job).toBe(null);
    expect(sheet.verdict).toBe(null);
    expect(sheet.indications).toEqual([]);
  });

  it('refuses a date that is not a real day', () => {
    expect(() => makeSheet({ day: '2025-02-30' })).toThrow(/not a real calendar day/);
  });

  it('refuses indications that are not a list', () => {
    expect(() => makeSheet({ indications: 'slag' })).toThrow(/must be an array/);
  });

  it('refuses something that is not an object', () => {
    expect(() => makeSheet('J-118')).toThrow(/built from an object/);
  });
});

describe('completeness', () => {
  it('calls a full sheet issuable', () => {
    const state = completeness(full());
    expect(state.issuable).toBe(true);
    expect(state.missing).toEqual([]);
  });

  it('names the required fields', () => {
    expect(requiredFields()).toEqual(['job', 'jointId', 'day', 'reader', 'code']);
  });

  it('lists everything missing rather than the first', () => {
    const state = completeness(makeSheet({}));
    expect(state.missing).toContain('job');
    expect(state.missing).toContain('reader');
    expect(state.missing).toContain('verdict');
    expect(state.missing).toContain('films');
    expect(state.issuable).toBe(false);
  });

  it('counts a sheet with no reader as not issuable', () => {
    const sheet = makeSheet({ ...full(), reader: null });
    expect(completeness(sheet).missing).toEqual(['reader']);
  });

  it('counts a blank field as missing', () => {
    const sheet = makeSheet({ ...full(), job: '   ' });
    expect(completeness(sheet).missing).toEqual(['job']);
  });

  it('does not require indications, since a clean joint has none', () => {
    const sheet = makeSheet({ ...full(), indications: [] });
    expect(completeness(sheet).issuable).toBe(true);
  });
});

describe('indicationRows', () => {
  it('sorts them the way they sit round the weld', () => {
    const rows = indicationRows(full());
    expect(rows.map((row) => row.at)).toEqual([3, 8]);
  });

  it('numbers them from one after sorting', () => {
    expect(indicationRows(full()).map((row) => row.item)).toEqual([1, 2]);
  });

  it('prints the size to three places with the trailing zeros', () => {
    const rows = indicationRows(full());
    expect(rows[0].sizeIn).toBe('0.040');
  });

  it('is empty for a clean joint', () => {
    expect(indicationRows(makeSheet({}))).toEqual([]);
  });
});

describe('renderSheet', () => {
  it('prints the heading and the joint', () => {
    const text = renderSheet(full());
    expect(text).toContain('RADIOGRAPHIC INTERPRETATION');
    expect(text).toContain('J-118');
    expect(text).toContain('API1104');
  });

  it('prints the findings table', () => {
    const text = renderSheet(full());
    expect(text).toContain('FINDINGS');
    expect(text).toContain('indication');
    expect(text).toContain('slag inclusion');
  });

  it('says so plainly when there is nothing to report', () => {
    const clean = makeSheet({ ...full(), indications: [] });
    expect(renderSheet(clean)).toContain('No reportable indications.');
  });

  it('prints the disposition in capitals with its clause', () => {
    const text = renderSheet(full());
    expect(text).toContain('Disposition: ACCEPT');
    expect(text).toContain('Clause: 9.3');
  });

  it('lists every reason on a rejected joint', () => {
    const sheet = makeSheet({
      ...full(),
      verdict: { disposition: 'reject', clause: '9.3.9', reasons: ['crack 0.2 in long'] },
    });
    const text = renderSheet(sheet);
    expect(text).toContain('Disposition: REJECT');
    expect(text).toContain('crack 0.2 in long');
  });

  it('marks a draft and says what it is waiting on', () => {
    const sheet = makeSheet({ ...full(), reader: null });
    const text = renderSheet(sheet);
    expect(text).toContain('DRAFT');
    expect(text).toContain('reader');
  });

  it('does not mark a complete sheet as a draft', () => {
    expect(renderSheet(full())).not.toContain('DRAFT');
  });

  it('prints the reader and their level', () => {
    expect(renderSheet(full())).toContain('R. Ibarra (Level II)');
  });

  it('appends remarks only when there are some', () => {
    expect(renderSheet(full())).not.toContain('REMARKS');
    const withRemarks = makeSheet({ ...full(), remarks: 'Reshot at 3:00 for density.' });
    expect(renderSheet(withRemarks)).toContain('REMARKS');
    expect(renderSheet(withRemarks)).toContain('Reshot at 3:00');
  });

  it('renders a sheet with almost nothing on it rather than throwing', () => {
    expect(() => renderSheet(makeSheet({}))).not.toThrow();
    expect(renderSheet(makeSheet({}))).toContain('Not yet judged.');
  });

  it('keeps every line inside the paper width', () => {
    for (const line of renderSheet(full()).split('\n')) {
      expect(line.length).toBeLessThanOrEqual(78);
    }
  });
});

describe('summarise', () => {
  it('gives a register row', () => {
    expect(summarise(full())).toEqual({
      jointId: 'J-118',
      day: '2025-03-14',
      disposition: 'accept',
      indications: 2,
      largestIn: 0.15,
      issuable: true,
    });
  });

  it('reports nothing largest on a clean joint', () => {
    const clean = makeSheet({ ...full(), indications: [] });
    expect(summarise(clean).largestIn).toBe(0);
  });

  it('leaves the disposition null while the joint is unjudged', () => {
    const sheet = makeSheet({ ...full(), verdict: null });
    expect(summarise(sheet).disposition).toBe(null);
    expect(summarise(sheet).issuable).toBe(false);
  });
});
