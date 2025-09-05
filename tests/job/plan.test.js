import { describe, it, expect } from 'vitest';
import {
  setupMinutes,
  moveMinutes,
  jointMinutes,
  planDay,
  costOverTime,
  lastDayThatFits,
} from '../../src/job/plan.js';
import { makeJoint } from '../../src/joint/spec.js';
import { makeSource } from '../../src/safety/sourcelog.js';

const source = makeSource({
  serial: 'IR-7741',
  isotopeId: 'IR192',
  assayCi: 80,
  assayDay: '2025-03-01',
});

const joints = [
  makeJoint({ id: 'J-101', nominalSize: 6, schedule: 40 }),
  makeJoint({ id: 'J-102', nominalSize: 6, schedule: 40 }),
  makeJoint({ id: 'J-103', nominalSize: 6, schedule: 40 }),
];

const options = { sourceToObjectIn: 48 };

describe('the standing times', () => {
  it('carries a setup and a move time', () => {
    expect(setupMinutes()).toBeGreaterThan(0);
    expect(moveMinutes()).toBeGreaterThan(0);
  });
});

describe('jointMinutes', () => {
  const cost = jointMinutes({ joint: joints[0], source, day: '2025-03-14', ...options });

  it('counts a setup per shot rather than per joint', () => {
    expect(cost.shots).toBeGreaterThan(1);
    expect(cost.setupMinutes).toBeCloseTo(setupMinutes() * cost.shots, 2);
  });

  it('adds the setups to the exposures', () => {
    expect(cost.totalMinutes).toBeCloseTo(cost.exposureMinutes + cost.setupMinutes, 2);
  });

  it('is usually more setup than exposure on a short shot', () => {
    expect(cost.setupMinutes).toBeGreaterThan(cost.exposureMinutes);
  });

  it('takes longer as the source decays', () => {
    const march = jointMinutes({ joint: joints[0], source, day: '2025-03-14', ...options });
    const july = jointMinutes({ joint: joints[0], source, day: '2025-07-14', ...options });
    expect(july.exposureMinutes).toBeGreaterThan(march.exposureMinutes);
    expect(july.activityCi).toBeLessThan(march.activityCi);
  });

  it('leaves the setup time alone as the source decays', () => {
    const march = jointMinutes({ joint: joints[0], source, day: '2025-03-14', ...options });
    const july = jointMinutes({ joint: joints[0], source, day: '2025-07-14', ...options });
    expect(july.setupMinutes).toBeCloseTo(march.setupMinutes, 2);
  });

  it('refuses a distance at zero', () => {
    expect(() =>
      jointMinutes({ joint: joints[0], source, day: '2025-03-14', sourceToObjectIn: 0 })
    ).toThrow(/source to object distance/);
  });
});

describe('planDay', () => {
  it('fits the whole list into a long day', () => {
    const plan = planDay({ joints, source, day: '2025-03-14', hoursAvailable: 10, ...options });
    expect(plan.fitsAll).toBe(true);
    expect(plan.scheduled).toHaveLength(3);
    expect(plan.spilled).toEqual([]);
  });

  it('spills what does not fit into a short one', () => {
    const plan = planDay({
      joints,
      source,
      day: '2025-03-14',
      hoursAvailable: 1.5,
      ...options,
    });
    expect(plan.fitsAll).toBe(false);
    expect(plan.scheduled.length + plan.spilled.length).toBe(3);
  });

  it('keeps the order the crew walks the line in', () => {
    const plan = planDay({ joints, source, day: '2025-03-14', hoursAvailable: 10, ...options });
    expect(plan.scheduled.map((entry) => entry.jointId)).toEqual(['J-101', 'J-102', 'J-103']);
  });

  it('takes the move time out of the budget before anything is scheduled', () => {
    const plan = planDay({ joints, source, day: '2025-03-14', hoursAvailable: 10, ...options });
    expect(plan.budgetMinutes).toBeCloseTo(10 * 60 - moveMinutes(), 2);
  });

  it('accounts for every minute it spent', () => {
    const plan = planDay({ joints, source, day: '2025-03-14', hoursAvailable: 10, ...options });
    const spent = plan.scheduled.reduce((total, entry) => total + entry.totalMinutes, 0);
    expect(plan.usedMinutes).toBeCloseTo(spent, 2);
    expect(plan.freeMinutes).toBeCloseTo(plan.budgetMinutes - plan.usedMinutes, 2);
  });

  it('records the activity the plan was made on', () => {
    const plan = planDay({ joints, source, day: '2025-03-14', hoursAvailable: 10, ...options });
    expect(plan.activityCi).toBeLessThan(80);
    expect(plan.source).toBe('IR-7741');
  });

  it('refuses a day with no hours in it', () => {
    expect(() =>
      planDay({ joints, source, day: '2025-03-14', hoursAvailable: 0, ...options })
    ).toThrow(/hours available/);
  });

  it('plans an empty list without complaining', () => {
    const plan = planDay({ joints: [], source, day: '2025-03-14', ...options });
    expect(plan.fitsAll).toBe(true);
    expect(plan.usedMinutes).toBe(0);
  });
});

describe('costOverTime', () => {
  const days = ['2025-03-14', '2025-06-14', '2025-09-14'];

  it('shows the same list costing more as the months pass', () => {
    const costs = costOverTime({ joints, source, days, hoursAvailable: 10, ...options });
    expect(costs[1].totalMinutes).toBeGreaterThan(costs[0].totalMinutes);
    expect(costs[2].totalMinutes).toBeGreaterThan(costs[1].totalMinutes);
  });

  it('shows the activity falling alongside it', () => {
    const costs = costOverTime({ joints, source, days, hoursAvailable: 10, ...options });
    expect(costs[2].activityCi).toBeLessThan(costs[0].activityCi);
  });

  it('says on which days the list still fits', () => {
    const costs = costOverTime({ joints, source, days, hoursAvailable: 2, ...options });
    expect(costs.map((entry) => entry.fitsAll)).toContain(false);
  });
});

describe('lastDayThatFits', () => {
  const days = ['2025-03-14', '2025-06-14', '2025-09-14', '2025-12-14'];

  it('finds the last day the whole list still fits', () => {
    const day = lastDayThatFits({ joints, source, days, hoursAvailable: 10, ...options });
    expect(days).toContain(day);
  });

  it('never fits for longer on a shorter day', () => {
    const tight = lastDayThatFits({ joints, source, days, hoursAvailable: 3.5, ...options });
    const roomy = lastDayThatFits({ joints, source, days, hoursAvailable: 10, ...options });
    expect(days.indexOf(roomy)).toBeGreaterThanOrEqual(days.indexOf(tight));
  });

  it('refuses when the list fits on none of them', () => {
    expect(() =>
      lastDayThatFits({ joints, source, days, hoursAvailable: 0.4, ...options })
    ).toThrow(/does not fit on any of those days/);
  });
});
