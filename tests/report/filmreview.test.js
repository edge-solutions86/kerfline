import { describe, it, expect } from 'vitest';
import {
  defaultFraction,
  selectForReview,
  makeReview,
  direction,
  agreement,
  byReader,
  disagreements,
  renderReview,
} from '../../src/report/filmreview.js';

const review = (jointId, firstCall, secondCall, reviewer = 'K. Osei') =>
  makeReview({ jointId, day: '2025-03-20', reviewer, firstCall, secondCall });

const interpretations = Array.from({ length: 20 }, (_u, i) => ({
  jointId: `J-${i + 1}`,
  reader: i < 10 ? 'R. Ibarra' : 'D. Whyte',
}));

describe('selectForReview', () => {
  it('takes a tenth by default', () => {
    expect(defaultFraction()).toBe(0.1);
    expect(selectForReview(interpretations, { job: 'C-4471' })).toHaveLength(2);
  });

  it('is reproducible for a job', () => {
    const a = selectForReview(interpretations, { job: 'C-4471' });
    const b = selectForReview(interpretations, { job: 'C-4471' });
    expect(a.map((i) => i.jointId)).toEqual(b.map((i) => i.jointId));
  });

  it('picks differently for a different job', () => {
    const a = selectForReview(interpretations, { job: 'C-4471', fraction: 0.25 });
    const b = selectForReview(interpretations, { job: 'C-9999', fraction: 0.25 });
    expect(a.map((i) => i.jointId)).not.toEqual(b.map((i) => i.jointId));
  });

  it('rounds the share up', () => {
    expect(selectForReview(interpretations, { job: 'C-1', fraction: 0.11 })).toHaveLength(3);
  });

  it('takes everything at a fraction of one', () => {
    expect(selectForReview(interpretations, { job: 'C-1', fraction: 1 })).toHaveLength(20);
  });

  it('reads in register order once picked', () => {
    const picked = selectForReview(interpretations, { job: 'C-1', fraction: 0.5 });
    const ids = picked.map((i) => i.jointId);
    expect(ids).toEqual([...ids].sort((a, b) => Number(a.slice(2)) - Number(b.slice(2))));
  });

  it('refuses a fraction outside nought to one', () => {
    expect(() => selectForReview(interpretations, { job: 'C-1', fraction: 0 })).toThrow(
      /review fraction/
    );
    expect(() => selectForReview(interpretations, { job: 'C-1', fraction: 1.5 })).toThrow(
      /more than all of them/
    );
  });

  it('refuses a selection with no job', () => {
    expect(() => selectForReview(interpretations, { job: ' ' })).toThrow(/belongs to a job/);
  });
});

describe('makeReview', () => {
  it('records both calls and whether they agree', () => {
    const entry = review('J-1', 'accept', 'accept');
    expect(entry.agrees).toBe(true);
    expect(review('J-1', 'accept', 'repair').agrees).toBe(false);
  });

  it('refuses a review missing either call', () => {
    expect(() =>
      makeReview({
        jointId: 'J-1',
        day: '2025-03-20',
        reviewer: 'x',
        firstCall: '',
        secondCall: 'accept',
      })
    ).toThrow(/what the first reader called it/);
    expect(() =>
      makeReview({
        jointId: 'J-1',
        day: '2025-03-20',
        reviewer: 'x',
        firstCall: 'accept',
        secondCall: ' ',
      })
    ).toThrow(/what the second reader called it/);
  });

  it('refuses a review with no reviewer', () => {
    expect(() =>
      makeReview({
        jointId: 'J-1',
        day: '2025-03-20',
        reviewer: '',
        firstCall: 'a',
        secondCall: 'a',
      })
    ).toThrow(/who read it again/);
  });
});

describe('direction', () => {
  it('says nothing when the two agree', () => {
    expect(direction(review('J-1', 'accept', 'accept'))).toBe('agreed');
  });

  it('calls the first reader lenient when the second was harsher', () => {
    expect(direction(review('J-1', 'accept', 'repair'))).toBe('first reader was lenient');
    expect(direction(review('J-1', 'repair', 'reject'))).toBe('first reader was lenient');
  });

  it('calls them strict when the second was softer', () => {
    expect(direction(review('J-1', 'reject', 'accept'))).toBe('first reader was strict');
  });

  it('says so when the calls cannot be compared', () => {
    expect(direction(review('J-1', 'accept', 'reshoot'))).toBe('not comparable');
  });
});

describe('agreement', () => {
  it('reports full agreement', () => {
    const reviews = [review('J-1', 'accept', 'accept'), review('J-2', 'repair', 'repair')];
    const summary = agreement(reviews);
    expect(summary.rate).toBe(1);
    expect(summary.disagreements).toBe(0);
    expect(summary.oneSided).toBe(false);
  });

  it('counts an even split as not one sided', () => {
    const reviews = [review('J-1', 'accept', 'repair'), review('J-2', 'repair', 'accept')];
    const summary = agreement(reviews);
    expect(summary.lenient).toBe(1);
    expect(summary.strict).toBe(1);
    expect(summary.oneSided).toBe(false);
    expect(summary.drifting).toBe(false);
  });

  it('calls three the same way a drift', () => {
    const reviews = [
      review('J-1', 'accept', 'repair'),
      review('J-2', 'accept', 'repair'),
      review('J-3', 'accept', 'repair'),
    ];
    const summary = agreement(reviews);
    expect(summary.oneSided).toBe(true);
    expect(summary.drifting).toBe(true);
  });

  it('does not call two the same way a drift yet', () => {
    const reviews = [review('J-1', 'accept', 'repair'), review('J-2', 'accept', 'repair')];
    expect(agreement(reviews).oneSided).toBe(true);
    expect(agreement(reviews).drifting).toBe(false);
  });

  it('agrees with itself on an empty set', () => {
    expect(agreement([]).rate).toBe(1);
    expect(agreement([]).drifting).toBe(false);
  });
});

describe('byReader', () => {
  it('splits the agreement by reader, worst first', () => {
    const reviews = [
      { ...review('J-1', 'accept', 'repair'), reader: 'R. Ibarra' },
      { ...review('J-2', 'accept', 'accept'), reader: 'D. Whyte' },
    ];
    const rows = byReader(reviews, (entry) => entry.reader);
    expect(rows[0].reader).toBe('R. Ibarra');
    expect(rows[0].rate).toBe(0);
  });

  it('refuses without a way to find the reader', () => {
    expect(() => byReader([], 'reader')).toThrow(/needs a way to find the reader/);
  });
});

describe('disagreements and renderReview', () => {
  const reviews = [
    review('J-1', 'accept', 'repair'),
    review('J-2', 'accept', 'accept'),
    review('J-3', 'accept', 'repair'),
    review('J-4', 'accept', 'repair'),
  ];

  it('lists only the ones that disagreed, with the direction', () => {
    const found = disagreements(reviews);
    expect(found).toHaveLength(3);
    expect(found[0].direction).toBe('first reader was lenient');
  });

  it('prints the summary and the disagreements', () => {
    const text = renderReview(reviews, { job: 'C-4471' });
    expect(text).toContain('SECOND READING');
    expect(text).toContain('Agreed');
    expect(text).toContain('J-1');
  });

  it('raises the finding when every disagreement went one way', () => {
    const text = renderReview(reviews, { job: 'C-4471' });
    expect(text).toContain('FINDING');
    expect(text).toContain('a reader who has moved');
  });

  it('raises no finding on an even split', () => {
    const even = [review('J-1', 'accept', 'repair'), review('J-2', 'repair', 'accept')];
    expect(renderReview(even, { job: 'C-1' })).not.toContain('FINDING');
  });

  it('says none plainly when everybody agreed', () => {
    const agreed = [review('J-1', 'accept', 'accept')];
    expect(renderReview(agreed, { job: 'C-1' })).toContain('None.');
  });

  it('keeps every line inside the paper width', () => {
    for (const line of renderReview(reviews, { job: 'C-4471' }).split('\n')) {
      expect(line.length).toBeLessThanOrEqual(78);
    }
  });
});
