import { describe, it, expect } from 'vitest';
import { makeJournal, appendAll, verify } from '../../src/store/journal.js';
import {
  attemptsFor,
  jointState,
  recordRepair,
  closeJoint,
  openJoints,
} from '../../src/pipeline/close.js';

const entry = (kind, day, subject, payload = {}) => ({ kind, day, subject, payload });

const shotOnly = appendAll(makeJournal('C-4471'), [entry('shot', '2025-03-10', 'J-118')]);

const repairing = appendAll(shotOnly, [
  entry('verdict', '2025-03-11', 'J-118', { disposition: 'repair', at: '3:00' }),
]);

const accepted = appendAll(repairing, [
  entry('repair', '2025-03-13', 'J-118', { at: '3:00' }),
  entry('shot', '2025-03-14', 'J-118'),
  entry('verdict', '2025-03-14', 'J-118', { disposition: 'accept' }),
]);

describe('attemptsFor', () => {
  it('rebuilds the chain from the verdicts', () => {
    expect(attemptsFor(accepted, 'J-118').map((a) => a.label)).toEqual(['original', 'R1']);
  });

  it('is empty for a joint with no verdict yet', () => {
    expect(attemptsFor(shotOnly, 'J-118')).toEqual([]);
  });

  it('carries the location a repair was called at', () => {
    expect(attemptsFor(repairing, 'J-118')[0].at).toBe('3:00');
  });
});

describe('jointState', () => {
  it('reports a joint shot but not judged', () => {
    const state = jointState(shotOnly, 'J-118');
    expect(state.everShot).toBe(true);
    expect(state.status).toBe('not shot');
    expect(state.open).toBe(true);
  });

  it('reports a joint waiting on a repair', () => {
    const state = jointState(repairing, 'J-118');
    expect(state.status).toBe('awaiting repair');
    expect(state.open).toBe(true);
  });

  it('reports an accepted joint as closed', () => {
    const state = jointState(accepted, 'J-118');
    expect(state.status).toBe('accepted');
    expect(state.open).toBe(false);
    expect(state.repairs).toBe(1);
  });

  it('reports a joint that was never shot', () => {
    const state = jointState(makeJournal('C-0'), 'J-999');
    expect(state.everShot).toBe(false);
  });
});

describe('recordRepair', () => {
  it('writes a repair entry', () => {
    const journal = recordRepair(repairing, {
      jointId: 'J-118',
      day: '2025-03-13',
      at: '3:00',
    });
    expect(journal.entries[journal.entries.length - 1].kind).toBe('repair');
    expect(verify(journal).intact).toBe(true);
  });

  it('carries the welder and the note', () => {
    const journal = recordRepair(repairing, {
      jointId: 'J-118',
      day: '2025-03-13',
      at: '3:00',
      welder: 'A17',
      note: 'ground to sound metal',
    });
    const payload = journal.entries[journal.entries.length - 1].payload;
    expect(payload.welder).toBe('A17');
    expect(payload.note).toBe('ground to sound metal');
  });

  it('refuses a third repair at the same spot', () => {
    const twice = appendAll(repairing, [
      entry('verdict', '2025-03-14', 'J-118', { disposition: 'repair', at: '3:00' }),
      entry('verdict', '2025-03-15', 'J-118', { disposition: 'repair', at: '3:02' }),
    ]);
    expect(() =>
      recordRepair(twice, { jointId: 'J-118', day: '2025-03-16', at: '3:00' })
    ).toThrow(/already been made/);
  });

  it('allows a repair elsewhere on the same joint', () => {
    const twice = appendAll(repairing, [
      entry('verdict', '2025-03-14', 'J-118', { disposition: 'repair', at: '3:00' }),
      entry('verdict', '2025-03-15', 'J-118', { disposition: 'repair', at: '3:02' }),
    ]);
    expect(() =>
      recordRepair(twice, { jointId: 'J-118', day: '2025-03-16', at: '9:00' })
    ).not.toThrow();
  });

  it('refuses a repair with no joint named', () => {
    expect(() => recordRepair(repairing, { jointId: '', day: '2025-03-13' })).toThrow(
      /names the joint/
    );
  });
});

describe('closeJoint', () => {
  it('closes an open joint that is not waiting on a repair', () => {
    const closed = closeJoint(shotOnly, {
      jointId: 'J-118',
      day: '2025-03-15',
      disposition: 'accept',
      by: 'D. Whyte',
    });
    const last = closed.entries[closed.entries.length - 1];
    expect(last.payload.closing).toBe(true);
    expect(last.payload.closedBy).toBe('D. Whyte');
    expect(jointState(closed, 'J-118').status).toBe('accepted');
  });

  it('refuses to close a joint that was never shot', () => {
    expect(() =>
      closeJoint(makeJournal('C-0'), {
        jointId: 'J-9',
        day: '2025-03-15',
        disposition: 'accept',
      })
    ).toThrow(/never shot/);
  });

  it('refuses to accept a joint still waiting on its repair', () => {
    expect(() =>
      closeJoint(repairing, { jointId: 'J-118', day: '2025-03-15', disposition: 'accept' })
    ).toThrow(/has not been shot again/);
  });

  it('lets a joint waiting on a repair be cut out instead', () => {
    const cut = closeJoint(repairing, {
      jointId: 'J-118',
      day: '2025-03-15',
      disposition: 'reject',
    });
    expect(jointState(cut, 'J-118').status).toBe('cut out');
  });

  it('refuses to close a joint its own verdict already accepted', () => {
    // The accepting verdict is what closes a joint. There is no second step,
    // and offering one would let a joint be answered twice.
    expect(jointState(accepted, 'J-118').open).toBe(false);
    expect(() =>
      closeJoint(accepted, { jointId: 'J-118', day: '2025-03-16', disposition: 'accept' })
    ).toThrow(/already accepted/);
  });

  it('refuses to reopen a joint that was cut out', () => {
    const cut = closeJoint(repairing, {
      jointId: 'J-118',
      day: '2025-03-15',
      disposition: 'reject',
    });
    expect(() =>
      closeJoint(cut, { jointId: 'J-118', day: '2025-03-16', disposition: 'accept' })
    ).toThrow(/already cut out/);
  });

  it('refuses a disposition a joint cannot close as', () => {
    expect(() =>
      closeJoint(accepted, { jointId: 'J-118', day: '2025-03-15', disposition: 'repair' })
    ).toThrow(/accepted or cut out/);
  });

  it('keeps the chain intact', () => {
    const closed = closeJoint(repairing, {
      jointId: 'J-118',
      day: '2025-03-15',
      disposition: 'reject',
    });
    expect(verify(closed).intact).toBe(true);
  });
});

describe('openJoints', () => {
  it('lists the joints still owing something', () => {
    const many = appendAll(repairing, [
      entry('shot', '2025-03-12', 'J-200'),
      entry('verdict', '2025-03-12', 'J-200', { disposition: 'accept' }),
    ]);
    expect(openJoints(many).map((state) => state.jointId)).toEqual(['J-118']);
  });

  it('leaves out a joint that was never shot', () => {
    const planned = appendAll(makeJournal('C-1'), [entry('joint', '2025-03-01', 'J-1')]);
    expect(openJoints(planned)).toEqual([]);
  });

  it('is empty once everything has closed', () => {
    expect(openJoints(accepted)).toEqual([]);
  });
});
