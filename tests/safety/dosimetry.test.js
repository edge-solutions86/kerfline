import { describe, it, expect } from 'vitest';
import {
  annualLimitMrem,
  investigationLevels,
  makeBadgeRecord,
  doseInPeriod,
  annualDose,
  judgeAnnual,
  comparePocket,
} from '../../src/safety/dosimetry.js';

const badge = (from, to, mrem, returned = true) =>
  makeBadgeRecord({ person: 'R. Ibarra', from, to, mrem, returned });

describe('the limits', () => {
  it('sets the annual limit at five thousand millirem', () => {
    expect(annualLimitMrem()).toBe(5000);
  });

  it('puts the investigation levels well below it', () => {
    const levels = investigationLevels();
    expect(levels.levelOneMrem).toBe(500);
    expect(levels.levelTwoMrem).toBe(1500);
    expect(levels.levelTwoMrem).toBeLessThan(annualLimitMrem());
  });
});

describe('makeBadgeRecord', () => {
  it('builds a returned badge', () => {
    const record = badge('2025-01-01', '2025-03-31', 42);
    expect(record.mrem).toBe(42);
    expect(record.returned).toBe(true);
  });

  it('leaves an unreturned badge with no reading at all', () => {
    const record = badge('2025-01-01', '2025-03-31', null, false);
    expect(record.mrem).toBe(null);
  });

  it('refuses a reading on a badge that has not come back', () => {
    expect(() => badge('2025-01-01', '2025-03-31', 42, false)).toThrow(
      /cannot carry a reading/
    );
  });

  it('refuses a period that ends before it starts', () => {
    expect(() => badge('2025-03-31', '2025-01-01', 42)).toThrow(/cannot end before it starts/);
  });

  it('refuses a negative dose', () => {
    expect(() => badge('2025-01-01', '2025-03-31', -1)).toThrow(/not be below 0/);
  });

  it('refuses a record with nobody on it', () => {
    expect(() =>
      makeBadgeRecord({ person: '  ', from: '2025-01-01', to: '2025-03-31', mrem: 1 })
    ).toThrow(/names the person/);
  });
});

describe('doseInPeriod', () => {
  const records = [
    badge('2025-01-01', '2025-03-31', 120),
    badge('2025-04-01', '2025-06-30', 210),
    badge('2025-07-01', '2025-09-30', 90),
  ];

  it('adds the badges inside the window', () => {
    const period = doseInPeriod(records, { from: '2025-01-01', to: '2025-06-30' });
    expect(period.mrem).toBeCloseTo(330, 1);
    expect(period.records).toBe(2);
  });

  it('leaves out badges outside the window', () => {
    const period = doseInPeriod(records, { from: '2025-07-01', to: '2025-09-30' });
    expect(period.mrem).toBeCloseTo(90, 1);
  });

  it('is complete when every badge came back', () => {
    expect(doseInPeriod(records, { from: '2025-01-01', to: '2025-12-31' }).complete).toBe(true);
  });

  it('does not treat a missing badge as a zero', () => {
    const withMissing = [...records, badge('2025-10-01', '2025-12-31', null, false)];
    const period = doseInPeriod(withMissing, { from: '2025-01-01', to: '2025-12-31' });
    expect(period.complete).toBe(false);
    expect(period.outstandingBadges).toBe(1);
  });

  it('is zero over a window with nothing in it', () => {
    const period = doseInPeriod(records, { from: '2024-01-01', to: '2024-06-30' });
    expect(period.mrem).toBe(0);
    expect(period.records).toBe(0);
  });
});

describe('annualDose', () => {
  it('looks back twelve months', () => {
    const records = [
      badge('2024-01-01', '2024-03-31', 500),
      badge('2025-01-01', '2025-03-31', 120),
    ];
    const period = annualDose(records, '2025-06-30');
    expect(period.mrem).toBeCloseTo(120, 1);
  });
});

describe('judgeAnnual', () => {
  it('calls a quiet year within limits', () => {
    const records = [badge('2025-01-01', '2025-03-31', 120)];
    const verdict = judgeAnnual(records, '2025-06-30');
    expect(verdict.standing).toBe('within limits');
    expect(verdict.remainingMrem).toBeCloseTo(4880, 1);
  });

  it('raises a level one investigation past a tenth of the limit', () => {
    const records = [badge('2025-01-01', '2025-03-31', 600)];
    expect(judgeAnnual(records, '2025-06-30').standing).toBe('level one investigation');
  });

  it('raises a level two investigation past three tenths', () => {
    const records = [badge('2025-01-01', '2025-03-31', 1600)];
    expect(judgeAnnual(records, '2025-06-30').standing).toBe('level two investigation');
  });

  it('reports going over the annual limit', () => {
    const records = [badge('2025-01-01', '2025-03-31', 5200)];
    const verdict = judgeAnnual(records, '2025-06-30');
    expect(verdict.standing).toBe('over the annual limit');
    expect(verdict.remainingMrem).toBe(0);
  });

  it('never reports within limits on an incomplete record', () => {
    const records = [
      badge('2025-01-01', '2025-03-31', 120),
      badge('2025-04-01', '2025-06-30', null, false),
    ];
    const verdict = judgeAnnual(records, '2025-06-30');
    expect(verdict.standing).toBe('incomplete record');
  });

  it('reports the fraction of the limit used', () => {
    const records = [badge('2025-01-01', '2025-03-31', 500)];
    expect(judgeAnnual(records, '2025-06-30').fractionUsed).toBeCloseTo(0.1, 4);
  });
});

describe('comparePocket', () => {
  it('agrees when the two readings are close', () => {
    expect(comparePocket({ badgeMrem: 100, pocketMrem: 110 }).agrees).toBe(true);
  });

  it('disagrees when they are far apart', () => {
    const check = comparePocket({ badgeMrem: 100, pocketMrem: 400 });
    expect(check.agrees).toBe(false);
    expect(check.differenceMrem).toBe(300);
  });

  it('keeps the badge as the record either way', () => {
    const check = comparePocket({ badgeMrem: 100, pocketMrem: 400 });
    expect(check.ofRecord).toBe(100);
    expect(check.note).toMatch(/badge is the record/);
  });

  it('agrees when both read nothing rather than dividing by zero', () => {
    const check = comparePocket({ badgeMrem: 0, pocketMrem: 0 });
    expect(check.agrees).toBe(true);
    expect(check.differenceMrem).toBe(0);
  });

  it('takes a tighter tolerance', () => {
    expect(comparePocket({ badgeMrem: 100, pocketMrem: 120, tolerance: 0.1 }).agrees).toBe(
      false
    );
  });

  it('refuses a negative reading', () => {
    expect(() => comparePocket({ badgeMrem: -1, pocketMrem: 0 })).toThrow(/not be below 0/);
  });
});
