import { describe, it, expect } from 'vitest';
import {
  incidentKinds,
  reportingWindows,
  classify,
  makeIncident,
  markReported,
  unreported,
  overdueReports,
  dueWithin,
} from '../../src/safety/incident.js';

const base = {
  reference: 'INC-1',
  kind: 'survey_failure',
  day: '2025-03-14',
  detail: 'boundary reading over two at the rope',
};

describe('the vocabulary', () => {
  it('names the kinds and the windows', () => {
    expect(incidentKinds()).toContain('disconnect');
    expect(incidentKinds()).toContain('overexposure');
    expect(reportingWindows()).toEqual({ immediate: 0, oneDay: 1, thirtyDay: 30 });
  });

  it('refuses a kind nobody records', () => {
    expect(() => classify({ kind: 'nuisance' })).toThrow(/incident kind/);
  });
});

describe('classify', () => {
  it('reports a disconnect inside a day whatever the dose was', () => {
    expect(classify({ kind: 'disconnect', doseMrem: 0 }).window).toBe('oneDay');
    expect(classify({ kind: 'lost_source', doseMrem: 0 }).window).toBe('oneDay');
  });

  it('says the equipment failing is the reason, not the dose', () => {
    expect(classify({ kind: 'disconnect' }).because).toMatch(/equipment failed/);
  });

  it('reports a very large dose immediately', () => {
    expect(classify({ kind: 'overexposure', doseMrem: 30000 }).window).toBe('immediate');
  });

  it('reports a large dose inside a day', () => {
    expect(classify({ kind: 'overexposure', doseMrem: 6000 }).window).toBe('oneDay');
  });

  it('treats reaching the annual limit as the same one day report', () => {
    // The one day threshold and the annual whole body limit are both five
    // thousand millirem, so there is no gap between them to fall into.
    const atLimit = classify({ kind: 'overexposure', doseMrem: 5100 });
    expect(atLimit.window).toBe('oneDay');
    expect(atLimit.because).toMatch(/annual whole body limit/);
  });

  it('reports a small dose inside thirty days', () => {
    expect(classify({ kind: 'overexposure', doseMrem: 400 }).window).toBe('thirtyDay');
  });

  it('lets a very large dose overtake the equipment clock', () => {
    expect(classify({ kind: 'disconnect', doseMrem: 30000 }).window).toBe('immediate');
  });

  it('reports a survey failure with no dose inside thirty days', () => {
    expect(classify({ kind: 'survey_failure', doseMrem: 0 }).window).toBe('thirtyDay');
  });

  it('never sets the clock by how bad the day felt', () => {
    // A disconnect where nobody was exposed reports faster than a dose that
    // somebody actually picked up on a job where the equipment behaved.
    const quiet = classify({ kind: 'disconnect', doseMrem: 0 });
    const noisy = classify({ kind: 'overexposure', doseMrem: 400 });
    expect(quiet.days).toBeLessThan(noisy.days);
  });

  it('refuses a negative dose', () => {
    expect(() => classify({ kind: 'damage', doseMrem: -1 })).toThrow(/not be below 0/);
  });
});

describe('makeIncident', () => {
  it('sets the reporting date from the classification', () => {
    const incident = makeIncident(base);
    expect(incident.window).toBe('thirtyDay');
    expect(incident.reportBy).toBe('2025-04-13');
  });

  it('sets it the same day for an immediate report', () => {
    const incident = makeIncident({ ...base, kind: 'overexposure', doseMrem: 30000 });
    expect(incident.reportBy).toBe('2025-03-14');
  });

  it('starts unreported', () => {
    expect(makeIncident(base).reportedOn).toBe(null);
  });

  it('refuses an incident with no reference or no detail', () => {
    expect(() => makeIncident({ ...base, reference: ' ' })).toThrow(/carries a reference/);
    expect(() => makeIncident({ ...base, detail: '' })).toThrow(/says what happened/);
  });

  it('refuses a date that is not a real day', () => {
    expect(() => makeIncident({ ...base, day: '2025-02-30' })).toThrow(
      /not a real calendar day/
    );
  });
});

describe('markReported', () => {
  const incident = makeIncident(base);

  it('records the date and that it was on time', () => {
    const reported = markReported(incident, '2025-03-20');
    expect(reported.reportedOn).toBe('2025-03-20');
    expect(reported.onTime).toBe(true);
    expect(reported.daysLate).toBe(0);
  });

  it('counts the days when it was late', () => {
    const reported = markReported(incident, '2025-04-20');
    expect(reported.onTime).toBe(false);
    expect(reported.daysLate).toBe(7);
  });

  it('is on time on the last day of the window', () => {
    expect(markReported(incident, '2025-04-13').onTime).toBe(true);
  });

  it('refuses a report dated before the incident', () => {
    expect(() => markReported(incident, '2025-03-01')).toThrow(/before it happened/);
  });

  it('leaves the incident it was given alone', () => {
    markReported(incident, '2025-03-20');
    expect(incident.reportedOn).toBe(null);
  });
});

describe('unreported and overdueReports', () => {
  const slow = makeIncident(base);
  const fast = makeIncident({ ...base, reference: 'INC-2', kind: 'disconnect' });
  const done = markReported(makeIncident({ ...base, reference: 'INC-3' }), '2025-03-16');
  const all = [slow, fast, done];

  it('lists only the ones not yet reported', () => {
    expect(unreported(all).map((i) => i.reference)).toEqual(['INC-1', 'INC-2']);
  });

  it('finds nothing overdue on the day', () => {
    expect(overdueReports(all, '2025-03-14')).toEqual([]);
  });

  it('finds the fast one overdue first', () => {
    const late = overdueReports(all, '2025-03-20');
    expect(late).toHaveLength(1);
    expect(late[0].reference).toBe('INC-2');
  });

  it('puts the most urgent window first once both are late', () => {
    const late = overdueReports(all, '2025-05-01');
    expect(late[0].window).toBe('oneDay');
    expect(late).toHaveLength(2);
  });

  it('never lists a reported incident as overdue', () => {
    expect(overdueReports(all, '2026-01-01').map((i) => i.reference)).not.toContain('INC-3');
  });
});

describe('dueWithin', () => {
  const slow = makeIncident(base);

  it('finds nothing due far ahead of the window', () => {
    expect(dueWithin([slow], { day: '2025-03-14', days: 5 })).toEqual([]);
  });

  it('finds it as the window closes', () => {
    expect(dueWithin([slow], { day: '2025-04-10', days: 5 })).toHaveLength(1);
  });

  it('does not find one that is already overdue', () => {
    expect(dueWithin([slow], { day: '2025-05-01', days: 5 })).toEqual([]);
  });
});
