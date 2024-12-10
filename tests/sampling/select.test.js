import { describe, it, expect } from 'vitest';
import {
  generator,
  seedFrom,
  shuffle,
  selectJoints,
  progressiveFor,
  escalateFor,
} from '../../src/sampling/select.js';

const joints = (welder, n, from = 1) =>
  Array.from({ length: n }, (_unused, i) => ({ id: `${welder}-${i + from}`, welder }));

describe('generator', () => {
  it('produces the same stream for the same seed', () => {
    const a = generator(12345);
    const b = generator(12345);
    expect([a(), a(), a()]).toEqual([b(), b(), b()]);
  });

  it('produces a different stream for a different seed', () => {
    const a = generator(1);
    const b = generator(2);
    expect(a()).not.toBe(b());
  });

  it('stays inside zero and one', () => {
    const next = generator(99);
    for (let i = 0; i < 500; i += 1) {
      const value = next();
      expect(value).toBeGreaterThanOrEqual(0);
      expect(value).toBeLessThan(1);
    }
  });

  it('refuses a seed that is not a whole number', () => {
    expect(() => generator(1.5)).toThrow(/whole number/);
  });
});

describe('seedFrom', () => {
  it('turns a job number into a stable seed', () => {
    expect(seedFrom('C-4471')).toBe(seedFrom('C-4471'));
  });

  it('gives different jobs different seeds', () => {
    expect(seedFrom('C-4471')).not.toBe(seedFrom('C-4472'));
  });

  it('ignores surrounding whitespace', () => {
    expect(seedFrom(' C-4471 ')).toBe(seedFrom('C-4471'));
  });

  it('refuses an empty job number', () => {
    expect(() => seedFrom('')).toThrow(/non empty string/);
    expect(() => seedFrom(null)).toThrow(/non empty string/);
  });

  it('always yields a whole number a generator will take', () => {
    const seed = seedFrom('C-4471');
    expect(Number.isInteger(seed)).toBe(true);
    expect(() => generator(seed)).not.toThrow();
  });
});

describe('shuffle', () => {
  it('is reproducible for a seed', () => {
    const items = [1, 2, 3, 4, 5, 6, 7, 8];
    expect(shuffle(items, 7)).toEqual(shuffle(items, 7));
  });

  it('keeps every item', () => {
    const items = [1, 2, 3, 4, 5, 6, 7, 8];
    expect(shuffle(items, 7).sort((a, b) => a - b)).toEqual(items);
  });

  it('actually moves things', () => {
    const items = Array.from({ length: 40 }, (_unused, i) => i);
    expect(shuffle(items, 7)).not.toEqual(items);
  });

  it('leaves the caller array alone', () => {
    const items = [1, 2, 3];
    shuffle(items, 7);
    expect(items).toEqual([1, 2, 3]);
  });
});

describe('selectJoints', () => {
  const job = [...joints('A17', 30), ...joints('B04', 5), ...joints('C09', 2)];

  it('picks the number asked for', () => {
    expect(selectJoints(job, 5, 42)).toHaveLength(5);
  });

  it('is reproducible for a seed', () => {
    expect(selectJoints(job, 5, 42)).toEqual(selectJoints(job, 5, 42));
  });

  it('picks differently on a different seed', () => {
    const a = selectJoints(job, 5, 42).map((j) => j.id);
    const b = selectJoints(job, 5, 99).map((j) => j.id);
    expect(a).not.toEqual(b);
  });

  it('covers every welder before doubling up on one', () => {
    const picked = selectJoints(job, 3, 42);
    const welders = new Set(picked.map((j) => j.welder));
    expect(welders.size).toBe(3);
  });

  it('does not miss the welder with the fewest joints', () => {
    // A purely random draw of 3 from this job misses C09 most of the time.
    for (const seed of [1, 2, 3, 4, 5]) {
      const welders = new Set(selectJoints(job, 3, seed).map((j) => j.welder));
      expect(welders.has('C09')).toBe(true);
    }
  });

  it('never picks the same joint twice', () => {
    const picked = selectJoints(job, 20, 42);
    expect(new Set(picked.map((j) => j.id)).size).toBe(20);
  });

  it('picks nothing when nothing is asked for', () => {
    expect(selectJoints(job, 0, 42)).toEqual([]);
  });

  it('refuses to pick more than the job has', () => {
    expect(() => selectJoints(job, 100, 42)).toThrow(/more joints than the job has/);
  });
});

describe('progressiveFor', () => {
  const job = [...joints('A17', 6), ...joints('B04', 6)];

  it('pulls two more joints by the same welder', () => {
    const failed = job[0];
    const result = progressiveFor({ failed, allJoints: job });
    expect(result.additional).toHaveLength(2);
    expect(result.additional.every((j) => j.welder === 'A17')).toBe(true);
    expect(result.welder).toBe('A17');
  });

  it('never pulls the failed joint back in', () => {
    const failed = job[0];
    const result = progressiveFor({ failed, allJoints: job });
    expect(result.additional.map((j) => j.id)).not.toContain(failed.id);
  });

  it('skips joints already shot', () => {
    const failed = job[0];
    const result = progressiveFor({
      failed,
      allJoints: job,
      alreadyShot: [job[1], job[2]],
    });
    expect(result.additional.map((j) => j.id)).toEqual(['A17-4', 'A17-5']);
  });

  it('says so when the welder has not made two more joints', () => {
    const small = joints('C09', 2);
    const result = progressiveFor({ failed: small[0], allJoints: small });
    expect(result.additional).toHaveLength(1);
    expect(result.exhausted).toBe(true);
  });

  it('refuses without a failed joint', () => {
    expect(() => progressiveFor({ failed: null, allJoints: job })).toThrow(
      /failed joint record is needed/
    );
  });
});

describe('escalateFor', () => {
  it('takes everything back to the last accepted joint', () => {
    const job = [
      { id: 'A-1', welder: 'A17', disposition: 'accept' },
      { id: 'A-2', welder: 'A17', disposition: null },
      { id: 'A-3', welder: 'A17', disposition: null },
      { id: 'B-1', welder: 'B04', disposition: null },
    ];
    const pulled = escalateFor({ welder: 'A17', allJoints: job });
    expect(pulled.map((j) => j.id)).toEqual(['A-2', 'A-3']);
  });

  it('takes everything when nothing by that welder was ever accepted', () => {
    const job = joints('A17', 3);
    expect(escalateFor({ welder: 'A17', allJoints: job })).toHaveLength(3);
  });

  it('takes nothing when the last joint was accepted', () => {
    const job = [{ id: 'A-1', welder: 'A17', disposition: 'accept' }];
    expect(escalateFor({ welder: 'A17', allJoints: job })).toEqual([]);
  });

  it('leaves other welders out of it', () => {
    const job = [...joints('A17', 3), ...joints('B04', 3)];
    const pulled = escalateFor({ welder: 'A17', allJoints: job });
    expect(pulled.every((j) => j.welder === 'A17')).toBe(true);
  });
});
