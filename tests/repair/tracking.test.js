import { describe, it, expect } from 'vitest';
import {
  sameSpotDegrees,
  maximumRepairs,
  makeAttempt,
  nextLabel,
  repairsAt,
  canRepairAt,
  addAttempt,
  chainState,
  overdue,
} from '../../src/repair/tracking.js';

const shot = (label, day, disposition, at = null) =>
  makeAttempt({ label, day, disposition, at });

describe('the rules', () => {
  it('calls thirty degrees the same spot and allows two repairs there', () => {
    expect(sameSpotDegrees()).toBe(30);
    expect(maximumRepairs()).toBe(2);
  });
});

describe('makeAttempt', () => {
  it('builds an attempt', () => {
    const attempt = shot('original', '2025-03-14', 'repair', '3:00');
    expect(attempt.label).toBe('original');
    expect(attempt.at).toBe('3:00');
  });

  it('refuses an attempt with no label', () => {
    expect(() => makeAttempt({ label: '', day: '2025-03-14', disposition: 'accept' })).toThrow(
      /carries a label/
    );
  });

  it('refuses a disposition it does not know', () => {
    expect(() => makeAttempt({ label: 'R1', day: '2025-03-14', disposition: 'maybe' })).toThrow(
      /accept, repair or reject/
    );
  });

  it('refuses a date that is not a real day', () => {
    expect(() =>
      makeAttempt({ label: 'R1', day: '2025-02-30', disposition: 'accept' })
    ).toThrow(/not a real calendar day/);
  });
});

describe('nextLabel', () => {
  it('starts with the original shot', () => {
    expect(nextLabel([])).toBe('original');
  });

  it('counts the repairs after that', () => {
    const attempts = [shot('original', '2025-03-14', 'repair', '3:00')];
    expect(nextLabel(attempts)).toBe('R1');
    attempts.push(shot('R1', '2025-03-15', 'repair', '3:00'));
    expect(nextLabel(attempts)).toBe('R2');
  });
});

describe('repairsAt', () => {
  const attempts = [
    shot('original', '2025-03-14', 'repair', '3:00'),
    shot('R1', '2025-03-15', 'repair', '3:05'),
    shot('R2', '2025-03-16', 'repair', '9:00'),
  ];

  it('counts repairs within thirty degrees', () => {
    expect(repairsAt(attempts, '3:00')).toHaveLength(1);
    expect(repairsAt(attempts, '3:10')).toHaveLength(1);
  });

  it('does not count a repair on the other side of the pipe', () => {
    expect(repairsAt(attempts, '12:00')).toHaveLength(0);
  });

  it('does not count the original shot as a repair', () => {
    const first = [shot('original', '2025-03-14', 'repair', '3:00')];
    expect(repairsAt(first, '3:00')).toHaveLength(0);
  });

  it('counts across the top of the pipe rather than subtracting', () => {
    const nearTop = [shot('R1', '2025-03-15', 'repair', '11:55')];
    expect(repairsAt(nearTop, '12:05')).toHaveLength(1);
  });
});

describe('canRepairAt', () => {
  it('allows a first repair', () => {
    const attempts = [shot('original', '2025-03-14', 'repair', '3:00')];
    expect(canRepairAt(attempts, '3:00').allowed).toBe(true);
  });

  it('allows a second repair at the same spot', () => {
    const attempts = [
      shot('original', '2025-03-14', 'repair', '3:00'),
      shot('R1', '2025-03-15', 'repair', '3:00'),
    ];
    expect(canRepairAt(attempts, '3:00').allowed).toBe(true);
  });

  it('refuses a third repair at the same spot', () => {
    const attempts = [
      shot('original', '2025-03-14', 'repair', '3:00'),
      shot('R1', '2025-03-15', 'repair', '3:00'),
      shot('R2', '2025-03-16', 'repair', '3:02'),
    ];
    const check = canRepairAt(attempts, '3:00');
    expect(check.allowed).toBe(false);
    expect(check.reason).toMatch(/already been made/);
  });

  it('allows a repair elsewhere on the same joint', () => {
    const attempts = [
      shot('original', '2025-03-14', 'repair', '3:00'),
      shot('R1', '2025-03-15', 'repair', '3:00'),
      shot('R2', '2025-03-16', 'repair', '3:00'),
    ];
    expect(canRepairAt(attempts, '9:00').allowed).toBe(true);
  });

  it('refuses any repair once the joint has been rejected', () => {
    const attempts = [shot('original', '2025-03-14', 'reject', '3:00')];
    expect(canRepairAt(attempts, '9:00').allowed).toBe(false);
  });
});

describe('addAttempt', () => {
  const first = shot('original', '2025-03-14', 'repair', '3:00');

  it('appends an attempt', () => {
    const chain = addAttempt([first], shot('R1', '2025-03-15', 'accept', '3:00'));
    expect(chain).toHaveLength(2);
  });

  it('leaves the caller array alone', () => {
    const attempts = [first];
    addAttempt(attempts, shot('R1', '2025-03-15', 'accept', '3:00'));
    expect(attempts).toHaveLength(1);
  });

  it('refuses an attempt dated before the one it follows', () => {
    expect(() => addAttempt([first], shot('R1', '2025-03-13', 'accept', '3:00'))).toThrow(
      /dated before/
    );
  });

  it('refuses another attempt once the joint was accepted', () => {
    const closed = [shot('original', '2025-03-14', 'accept')];
    expect(() => addAttempt(closed, shot('R1', '2025-03-15', 'repair', '3:00'))).toThrow(
      /already accepted/
    );
  });

  it('refuses a third repair at the same spot', () => {
    const chain = [
      first,
      shot('R1', '2025-03-15', 'repair', '3:00'),
      shot('R2', '2025-03-16', 'repair', '3:00'),
    ];
    expect(() => addAttempt(chain, shot('R3', '2025-03-17', 'repair', '3:00'))).toThrow(
      /already been made/
    );
  });
});

describe('chainState', () => {
  it('reports a joint that has not been shot', () => {
    expect(chainState([])).toEqual({ status: 'not shot', repairs: 0, open: true, days: 0 });
  });

  it('reports a joint waiting on a repair', () => {
    const state = chainState([shot('original', '2025-03-14', 'repair', '3:00')]);
    expect(state.status).toBe('awaiting repair');
    expect(state.open).toBe(true);
  });

  it('reports an accepted joint and closes it', () => {
    const state = chainState([
      shot('original', '2025-03-14', 'repair', '3:00'),
      shot('R1', '2025-03-17', 'accept', '3:00'),
    ]);
    expect(state.status).toBe('accepted');
    expect(state.open).toBe(false);
    expect(state.repairs).toBe(1);
    expect(state.days).toBe(3);
    expect(state.closedOn).toBe('2025-03-17');
  });

  it('reports a joint that was cut out', () => {
    const state = chainState([shot('original', '2025-03-14', 'reject', '3:00')]);
    expect(state.status).toBe('cut out');
    expect(state.open).toBe(false);
  });
});

describe('overdue', () => {
  const chains = [
    { id: 'J-1', attempts: [shot('original', '2025-03-01', 'repair', '3:00')] },
    { id: 'J-2', attempts: [shot('original', '2025-03-14', 'repair', '3:00')] },
    { id: 'J-3', attempts: [shot('original', '2025-03-01', 'accept')] },
  ];

  it('finds the chains open past the limit', () => {
    const late = overdue(chains, 7, '2025-03-16');
    expect(late.map((chain) => chain.id)).toEqual(['J-1']);
  });

  it('leaves closed chains out however old they are', () => {
    expect(overdue(chains, 1, '2026-01-01').map((c) => c.id)).toEqual(['J-1', 'J-2']);
  });

  it('finds nothing when nothing is late', () => {
    expect(overdue(chains, 90, '2025-03-16')).toEqual([]);
  });

  it('refuses a limit that is not a whole number of days', () => {
    expect(() => overdue(chains, 1.5, '2025-03-16')).toThrow(/whole number/);
  });
});
